package GAGYELOL.dto;

import GAGYELOL.entity.Policy;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PolicyResponse {
    private Long policyId;
    private String policyName;
    private String filePath;
    private LocalDateTime createdAt;

    public static PolicyResponse from(Policy policy) {
        return PolicyResponse.builder()
                .policyId(policy.getId())
                .policyName(policy.getPolicyName())
                .filePath(policy.getFilePath())
                .createdAt(policy.getCreatedAt())
                .build();
    }
}
