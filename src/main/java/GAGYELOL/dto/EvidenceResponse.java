package GAGYELOL.dto;

import GAGYELOL.entity.Evidence;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class EvidenceResponse {
    private Long evidenceId;
    private Long groupId;
    private Long policyId;
    private String fileName;
    private String filePath;
    private String extractedText;
    private List<Long> selectedFormIds;
    private LocalDateTime createdAt;

    public static EvidenceResponse from(Evidence evidence, List<Long> selectedFormIds) {
        return EvidenceResponse.builder()
                .evidenceId(evidence.getId())
                .groupId(evidence.getGroup() != null ? evidence.getGroup().getId() : null)
                .policyId(evidence.getPolicyId())
                .fileName(evidence.getFileName())
                .filePath(evidence.getFilePath())
                .extractedText(evidence.getExtractedText())
                .selectedFormIds(selectedFormIds)
                .createdAt(evidence.getCreatedAt())
                .build();
    }
}
