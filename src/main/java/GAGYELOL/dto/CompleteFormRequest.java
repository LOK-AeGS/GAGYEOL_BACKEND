package GAGYELOL.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
public class CompleteFormRequest {
    private List<FormInput> forms;

    @Getter
    @NoArgsConstructor
    public static class FormInput {
        private Long formId;
        private Map<String, String> filledFields;    // 자동 채운 값
        private Map<String, String> userInputFields; // 사용자 입력 값
        // 이미지 필드: 필드명 → 이미지 소스("evidence", "recipientImage", 또는 파일 경로)
        private Map<String, String> imageFields;
    }
}
