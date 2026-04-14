package GAGYELOL.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyUploadResponse {
    private Long policyId;
    private String policyName;
    private int chunkCount;
    private String message;
}
