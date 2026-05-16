package GAGYELOL.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EvidenceListResponse {
    private Long evidenceId;
    private String businessName;
    private String status;       // draft / IN_PROGRESS / APPROVED / REJECTED
    private Integer totalAmount; // 현재 미지원, null 반환
    private LocalDateTime updatedAt;
}
