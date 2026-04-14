package GAGYELOL.dto.approval;

import lombok.Getter;

import java.util.Map;

@Getter
public class CreateApprovalRequest {
    private Long groupId;
    private Long evidenceId;          // 연결할 증빙서류 (선택)
    private Map<String, String> filledFields; // 초기 양식지 내용
}
