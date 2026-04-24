package GAGYELOL.service;

import GAGYELOL.dto.CompleteFormRequest;
import GAGYELOL.dto.EvidenceAnalysisResponse;
import GAGYELOL.dto.EvidenceResponse;
import GAGYELOL.dto.FillFieldsRequest;
import GAGYELOL.dto.FillFieldsResponse;
import GAGYELOL.entity.Evidence;
import GAGYELOL.entity.Form;
import GAGYELOL.entity.User;
import GAGYELOL.entity.UserGroup;
import GAGYELOL.entity.EvidenceForm;
import GAGYELOL.repository.ApprovalRequestRepository;
import GAGYELOL.repository.EvidenceFormRepository;
import GAGYELOL.repository.EvidenceRepository;
import GAGYELOL.repository.FormRepository;
import GAGYELOL.repository.PolicyChunkVectorStore;
import GAGYELOL.repository.UserGroupRepository;
import GAGYELOL.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class EvidenceService {

    private final EvidenceRepository evidenceRepository;
    private final EvidenceFormRepository evidenceFormRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final FormRepository formRepository;
    private final UserRepository userRepository;
    private final UserGroupRepository groupRepository;
    private final PolicyChunkVectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final EvidenceAiService evidenceAiService;
    private final FormFillService formFillService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload.evidence-dir:./uploads/evidence}")
    private String uploadDir;

    private static final int TOP_K = 5;

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getByGroup(Long groupId) {
        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("그룹을 찾을 수 없습니다: " + groupId));
        return evidenceRepository.findByGroupOrderByCreatedAtDesc(group).stream()
                .map(e -> EvidenceResponse.from(e, evidenceFormRepository.findFormIdsByEvidenceId(e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EvidenceResponse getById(Long evidenceId) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("증빙서류를 찾을 수 없습니다: " + evidenceId));
        return EvidenceResponse.from(evidence, evidenceFormRepository.findFormIdsByEvidenceId(evidenceId));
    }

    public void delete(Long evidenceId) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("증빙서류를 찾을 수 없습니다: " + evidenceId));

        // 결재 요청에서 참조 중이면 삭제 거부 (FK 무결성 + 감사 보존)
        long approvalCount = approvalRequestRepository.countByEvidence(evidence);
        if (approvalCount > 0) {
            throw new IllegalArgumentException(
                    "해당 증빙서류는 결재 요청에서 사용 중이므로 삭제할 수 없습니다. (결재 요청 " + approvalCount + "건)");
        }

        evidenceFormRepository.deleteByEvidenceId(evidenceId);
        evidenceRepository.delete(evidence);

        // 업로드 파일 제거 (DB 삭제 성공 후에만 시도)
        try {
            Files.deleteIfExists(Paths.get(evidence.getFilePath()));
        } catch (IOException e) {
            log.warn("증빙서류 파일 삭제 실패 (evidenceId={}, path={}): {}",
                    evidenceId, evidence.getFilePath(), e.getMessage());
        }
        log.info("증빙서류 삭제 완료 - evidenceId={}", evidenceId);
    }

    public EvidenceAnalysisResponse analyze(MultipartFile file, Long userId, Long groupId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        UserGroup group = groupId != null
                ? groupRepository.findById(groupId).orElseThrow(() -> new IllegalArgumentException("그룹을 찾을 수 없습니다: " + groupId))
                : null;

        // 그룹의 active_policy_id 자동 사용
        Long policyId = (group != null && group.getActivePolicy() != null)
                ? group.getActivePolicy().getId()
                : null;
        if (policyId == null) {
            log.warn("그룹에 active_policy_id가 설정되지 않았습니다. RAG 검색 없이 진행합니다.");
        }

        // 1. 파일 저장
        String filePath = saveFile(file);
        String fileName = file.getOriginalFilename();
        log.info("증빙서류 저장 완료: {}", filePath);

        // 2. Upstage IE로 결제 유형 분류 (파일 직접 전송, OCR 단계 불필요)
        String mimeType = resolveMimeType(fileName);
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("파일 읽기 실패", e);
        }
        String paymentType = evidenceAiService.classifyPaymentType(fileBytes, mimeType);
        log.info("결제 유형 분류 결과: {}", paymentType);

        // 3. 증빙서류 저장
        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .user(user)
                .group(group)
                .policyId(policyId)
                .filePath(filePath)
                .fileName(fileName)
                .extractedText("")
                .build());

        // 5. 결제 유형에 맞는 양식지 목록 반환 (사용자가 선택)
        List<Form> forms = group != null
                ? formRepository.findByGroupAndPaymentTypeIn(group, List.of(paymentType, "BOTH"))
                : formRepository.findByPaymentTypeIn(List.of(paymentType, "BOTH"));
        log.info("양식지 {}개 로드 완료 (결제유형: {})", forms.size(), paymentType);

        List<EvidenceAnalysisResponse.FormSummary> formSummaries = forms.stream()
                .map(f -> {
                    List<String> fields = List.of();
                    try { fields = objectMapper.readValue(f.getFormFields(), new TypeReference<>() {}); }
                    catch (Exception ignored) {}
                    return EvidenceAnalysisResponse.FormSummary.builder()
                            .formId(f.getId())
                            .formName(f.getFormName())
                            .description(f.getDescription())
                            .paymentType(f.getPaymentType())
                            .fields(fields)
                            .build();
                })
                .toList();

        return EvidenceAnalysisResponse.builder()
                .evidenceId(evidence.getId())
                .paymentType(paymentType)
                .extractedText("")
                .availableForms(formSummaries)
                .build();
    }

    private String resolveMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".hwp"))  return "application/x-hwp";
        if (lower.endsWith(".hwpx")) return "application/hwpx";
        throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
    }

    /**
     * 사용자가 선택한 양식지들의 필드를 증빙서류 내용으로 자동 채웁니다.
     */
    public FillFieldsResponse fillFields(Long evidenceId, FillFieldsRequest request) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("증빙서류를 찾을 수 없습니다: " + evidenceId));

        // 선택된 양식지 저장 (evidence_forms 테이블)
        evidenceFormRepository.deleteByEvidenceId(evidenceId);
        for (Long formId : request.getFormIds()) {
            Form selectedForm = formRepository.findById(formId)
                    .orElseThrow(() -> new IllegalArgumentException("양식지를 찾을 수 없습니다: " + formId));
            evidenceFormRepository.save(EvidenceForm.builder()
                    .id(new EvidenceForm.EvidenceFormId(evidenceId, formId))
                    .evidence(evidence)
                    .form(selectedForm)
                    .build());
        }

        List<FillFieldsResponse.FormFillResult> results = new ArrayList<>();

        for (Long formId : request.getFormIds()) {
            Form form = formRepository.findById(formId)
                    .orElseThrow(() -> new IllegalArgumentException("양식지를 찾을 수 없습니다: " + formId));

            // 양식지가 증빙서류와 같은 그룹 소속인지 검증
            if (evidence.getGroup() != null && form.getGroup() != null
                    && !form.getGroup().getId().equals(evidence.getGroup().getId())) {
                throw new IllegalArgumentException(
                        "양식지(id=" + formId + ")가 해당 그룹 소속이 아닙니다.");
            }

            List<String> formFields;
            try {
                formFields = objectMapper.readValue(form.getFormFields(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("양식지 {} 필드 파싱 실패, 건너뜀", formId);
                continue;
            }

            // Upstage IE로 파일에서 직접 필드 추출
            byte[] evidenceBytes;
            try {
                evidenceBytes = Files.readAllBytes(Paths.get(evidence.getFilePath()));
            } catch (IOException e) {
                throw new RuntimeException("증빙서류 파일 읽기 실패: " + e.getMessage(), e);
            }
            String evidenceMimeType = resolveMimeType(evidence.getFileName());
            String gptResult = evidenceAiService.fillFormFields(evidenceBytes, evidenceMimeType, formFields);
            log.info("GPT 필드 채우기 결과 (formId={}): {}", formId, gptResult);

            Map<String, String> filledFields = new LinkedHashMap<>();
            List<String> missingFields = new ArrayList<>();
            try {
                JsonNode node = objectMapper.readTree(gptResult);
                node.path("filled").fields().forEachRemaining(e -> filledFields.put(e.getKey(), e.getValue().asText()));
                node.path("missing").forEach(n -> missingFields.add(n.asText()));
            } catch (Exception e) {
                log.warn("GPT 결과 파싱 실패 (formId={}): {}", formId, e.getMessage());
            }

            results.add(FillFieldsResponse.FormFillResult.builder()
                    .formId(formId)
                    .formName(form.getFormName())
                    .filledFields(filledFields)
                    .missingFields(missingFields)
                    .build());
        }

        return FillFieldsResponse.builder()
                .evidenceId(evidenceId)
                .results(results)
                .build();
    }

    /**
     * 자동 채운 값 + 사용자 입력을 합쳐 최종 양식지 파일을 생성합니다.
     * 양식지가 1개면 단일 파일, 여러 개면 ZIP으로 반환합니다.
     */
    public byte[] completeForm(Long evidenceId, CompleteFormRequest request) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("증빙서류를 찾을 수 없습니다: " + evidenceId));

        // form이 evidence와 같은 그룹 소속인지 검증
        for (CompleteFormRequest.FormInput input : request.getForms()) {
            Form form = formRepository.findById(input.getFormId())
                    .orElseThrow(() -> new IllegalArgumentException("양식지를 찾을 수 없습니다: " + input.getFormId()));
            if (evidence.getGroup() != null && form.getGroup() != null
                    && !form.getGroup().getId().equals(evidence.getGroup().getId())) {
                throw new IllegalArgumentException("양식지(id=" + input.getFormId() + ")가 해당 그룹 소속이 아닙니다.");
            }
        }

        List<CompleteFormRequest.FormInput> formInputs = request.getForms();

        if (formInputs.size() == 1) {
            return generateSingleFile(formInputs.get(0));
        }
        return generateZip(formInputs);
    }

    private byte[] generateSingleFile(CompleteFormRequest.FormInput input) {
        Form form = formRepository.findById(input.getFormId())
                .orElseThrow(() -> new IllegalArgumentException("양식지를 찾을 수 없습니다: " + input.getFormId()));
        Map<String, String> allFields = mergeFields(input);
        log.info("양식지 최종 완성 - formId={}, 필드 수={}", form.getId(), allFields.size());
        try {
            return formFillService.fill(form.getFilePath(), allFields);
        } catch (IOException e) {
            throw new RuntimeException("양식지 파일 생성 실패: " + e.getMessage(), e);
        }
    }

    private byte[] generateZip(List<CompleteFormRequest.FormInput> formInputs) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(baos)) {

            for (CompleteFormRequest.FormInput input : formInputs) {
                Form form = formRepository.findById(input.getFormId())
                        .orElseThrow(() -> new IllegalArgumentException("양식지를 찾을 수 없습니다: " + input.getFormId()));
                Map<String, String> allFields = mergeFields(input);
                byte[] fileBytes = formFillService.fill(form.getFilePath(), allFields);

                String ext = form.getFilePath().substring(form.getFilePath().lastIndexOf('.'));
                zip.putNextEntry(new ZipEntry(form.getFormName() + ext));
                zip.write(fileBytes);
                zip.closeEntry();
                log.info("ZIP 항목 추가: {}", form.getFormName());
            }
            zip.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("ZIP 생성 실패: " + e.getMessage(), e);
        }
    }

    private Map<String, String> mergeFields(CompleteFormRequest.FormInput input) {
        Map<String, String> allFields = new LinkedHashMap<>();
        if (input.getFilledFields() != null) allFields.putAll(input.getFilledFields());
        if (input.getUserInputFields() != null) allFields.putAll(input.getUserInputFields());
        return allFields;
    }

    private String buildFormListDescription(List<Form> forms) {
        StringBuilder sb = new StringBuilder();
        for (Form form : forms) {
            sb.append(String.format("formId=%d, 양식명: %s, 용도: %s, 필드: %s\n",
                    form.getId(), form.getFormName(),
                    form.getDescription(), form.getFormFields()));
        }
        return sb.toString();
    }

    private String saveFile(MultipartFile file) {
        try {
            Path dirPath = Paths.get(uploadDir);
            Files.createDirectories(dirPath);
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = dirPath.resolve(filename);
            Files.write(filePath, file.getBytes());
            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }
    }
}
