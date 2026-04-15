package GAGYELOL.service;

import GAGYELOL.dto.CompleteFormRequest;
import GAGYELOL.dto.EvidenceAnalysisResponse;
import GAGYELOL.dto.FillFieldsRequest;
import GAGYELOL.dto.FillFieldsResponse;
import GAGYELOL.entity.Evidence;
import GAGYELOL.entity.Form;
import GAGYELOL.entity.User;
import GAGYELOL.entity.UserGroup;
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
import java.io.File;
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
    private final FormRepository formRepository;
    private final UserRepository userRepository;
    private final UserGroupRepository groupRepository;
    private final PolicyChunkVectorStore vectorStore;
    private final PdfParserService pdfParserService;
    private final FormParserService formParserService;
    private final EmbeddingService embeddingService;
    private final EvidenceAiService evidenceAiService;
    private final PolicyAiService policyAiService;
    private final FormFillService formFillService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload.evidence-dir:./uploads/evidence}")
    private String uploadDir;

    private static final int TOP_K = 5;

    public EvidenceAnalysisResponse analyze(MultipartFile file, Long userId, Long policyId, Long groupId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        UserGroup group = groupId != null
                ? groupRepository.findById(groupId).orElse(null)
                : null;

        // 1. 파일 저장
        String filePath = saveFile(file);
        String fileName = file.getOriginalFilename();
        log.info("증빙서류 저장 완료: {}", filePath);

        // 2. 텍스트 추출
        String evidenceText = extractText(filePath, fileName);
        log.info("증빙서류 텍스트 추출 완료 - {}자", evidenceText.length());

        // 3. 결제 유형 분류 (CARD / CASH)
        String paymentType = evidenceAiService.classifyPaymentType(evidenceText);
        log.info("결제 유형 분류 결과: {}", paymentType);

        // 4. 증빙서류 저장
        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .user(user)
                .group(group)
                .policyId(policyId)
                .filePath(filePath)
                .fileName(fileName)
                .extractedText(evidenceText)
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
                .extractedText(evidenceText)
                .availableForms(formSummaries)
                .build();
    }

    private String extractText(String filePath, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        try {
            if (lower.endsWith(".pdf")) {
                byte[] bytes = Files.readAllBytes(Paths.get(filePath));
                String text = pdfParserService.extractText(new File(filePath));
                if (pdfParserService.isImageBasedPdf(text)) {
                    log.info("이미지 기반 PDF - Vision API 사용");
                    List<String> images = pdfParserService.extractPageImagesAsBase64(bytes);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < images.size(); i++) {
                        log.info("페이지 {}/{} Vision 처리 중...", i + 1, images.size());
                        sb.append(policyAiService.extractTextFromImage(images.get(i))).append("\n");
                    }
                    return sb.toString();
                }
                return text;
            } else if (lower.endsWith(".docx") || lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
                return formParserService.extractText(new File(filePath));
            } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return extractTextFromImageFile(filePath, "image/jpeg");
            } else if (lower.endsWith(".png")) {
                return extractTextFromImageFile(filePath, "image/png");
            } else if (lower.endsWith(".webp")) {
                return extractTextFromImageFile(filePath, "image/webp");
            } else {
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
            }
        } catch (IOException e) {
            throw new RuntimeException("텍스트 추출 실패", e);
        }
    }

    private String extractTextFromImageFile(String filePath, String mimeType) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
        return policyAiService.extractTextFromImage(base64, mimeType);
    }

    /**
     * 사용자가 선택한 양식지들의 필드를 증빙서류 내용으로 자동 채웁니다.
     */
    public FillFieldsResponse fillFields(Long evidenceId, FillFieldsRequest request) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("증빙서류를 찾을 수 없습니다: " + evidenceId));

        // 선택된 formIds DB에 저장
        try {
            String formIdsJson = objectMapper.writeValueAsString(request.getFormIds());
            evidenceRepository.updateSelectedFormIds(evidenceId, formIdsJson);
        } catch (Exception e) {
            log.warn("selected_form_ids 저장 실패: {}", e.getMessage());
        }

        List<FillFieldsResponse.FormFillResult> results = new ArrayList<>();

        for (Long formId : request.getFormIds()) {
            Form form = formRepository.findById(formId)
                    .orElseThrow(() -> new IllegalArgumentException("양식지를 찾을 수 없습니다: " + formId));

            List<String> formFields;
            try {
                formFields = objectMapper.readValue(form.getFormFields(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("양식지 {} 필드 파싱 실패, 건너뜀", formId);
                continue;
            }

            String gptResult = evidenceAiService.fillFormFields(evidence.getExtractedText(), formFields);
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
        evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("증빙서류를 찾을 수 없습니다: " + evidenceId));

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
