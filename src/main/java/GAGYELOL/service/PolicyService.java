package GAGYELOL.service;

import GAGYELOL.dto.PolicyUploadResponse;
import GAGYELOL.entity.Policy;
import GAGYELOL.entity.User;
import GAGYELOL.repository.PolicyChunkVectorStore;
import GAGYELOL.repository.PolicyRepository;
import GAGYELOL.repository.UserRepository;
import GAGYELOL.service.PolicyAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final PolicyChunkVectorStore vectorStore;
    private final PdfParserService pdfParserService;
    private final EmbeddingService embeddingService;
    private final PolicyAiService policyAiService;
    private final GAGYELOL.repository.UserGroupRepository groupRepository;

    @Value("${file.upload.policy-dir:./uploads/policies}")
    private String uploadDir;

    public PolicyUploadResponse upload(MultipartFile file, String policyName, Long userId, Long groupId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        GAGYELOL.entity.UserGroup group = groupId != null
                ? groupRepository.findById(groupId).orElse(null) : null;

        // 1. 파일 저장
        String filePath = saveFile(file);
        log.info("파일 저장 완료: {}", filePath);

        // 2. Policy 메타데이터 저장
        Policy policy = policyRepository.save(Policy.builder()
                .user(user)
                .group(group)
                .policyName(policyName)
                .filePath(filePath)
                .build());
        log.info("Policy 저장 완료 - id: {}", policy.getId());

        // 3. PDF 바이트 읽기
        byte[] pdfBytes;
        try {
            pdfBytes = Files.readAllBytes(Paths.get(filePath));
        } catch (IOException e) {
            throw new RuntimeException("파일 읽기 실패", e);
        }

        // 4. 텍스트 추출 (텍스트 기반 or Vision)
        String text;
        try {
            text = pdfParserService.extractText(new File(filePath));
        } catch (IOException e) {
            throw new RuntimeException("PDF 텍스트 추출 실패", e);
        }

        if (pdfParserService.isImageBasedPdf(text)) {
            log.info("이미지 기반 PDF 감지 - Vision API로 처리합니다. policyId={}", policy.getId());
            try {
                List<String> pageImages = pdfParserService.extractPageImagesAsBase64(pdfBytes);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < pageImages.size(); i++) {
                    log.info("페이지 {}/{} Vision 처리 중...", i + 1, pageImages.size());
                    sb.append(policyAiService.extractTextFromImage(pageImages.get(i)));
                    sb.append("\n");
                }
                text = sb.toString();
            } catch (IOException e) {
                throw new RuntimeException("PDF 이미지 변환 실패", e);
            }
        }

        // 5. 청킹 + 임베딩 + 벡터 저장
        List<String> chunks = pdfParserService.chunkText(text);
        for (int i = 0; i < chunks.size(); i++) {
            log.info("청크 {}/{} 임베딩 중...", i + 1, chunks.size());
            float[] embedding = embeddingService.embed(chunks.get(i));
            vectorStore.save(policy.getId(), i, chunks.get(i), embedding);
        }

        log.info("RAG 인덱싱 완료 - policyId={}, 청크 수={}", policy.getId(), chunks.size());

        return PolicyUploadResponse.builder()
                .policyId(policy.getId())
                .policyName(policyName)
                .chunkCount(chunks.size())
                .message("규정책 RAG 인덱싱이 완료되었습니다.")
                .build();
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
