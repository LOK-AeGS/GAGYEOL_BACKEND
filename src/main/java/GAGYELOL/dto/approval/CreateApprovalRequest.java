package GAGYELOL.dto.approval;

import lombok.Getter;

import java.util.Map;

@Getter
public class CreateApprovalRequest {
    private Long groupId;
    private Long evidenceId;
    private Long formId;
    private Map<String, String> filledFields;
}
