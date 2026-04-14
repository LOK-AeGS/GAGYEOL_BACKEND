package GAGYELOL.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FillFieldsResponse {
    private Long evidenceId;
    private List<FormFillResult> results;

    @Getter
    @Builder
    public static class FormFillResult {
        private Long formId;
        private String formName;
        private Map<String, String> filledFields;  // 자동으로 채워진 필드
        private List<String> missingFields;         // 사용자 입력 필요 필드
    }
}
