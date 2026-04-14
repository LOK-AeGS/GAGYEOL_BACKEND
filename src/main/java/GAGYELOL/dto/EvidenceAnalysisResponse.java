package GAGYELOL.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EvidenceAnalysisResponse {
    private Long evidenceId;
    private String paymentType;
    private String extractedText;
    private List<FormSummary> availableForms; // 사용자가 선택할 양식지 목록

    @Getter
    @Builder
    public static class FormSummary {
        private Long formId;
        private String formName;
        private String description;
        private String paymentType;
        private List<String> fields;
    }
}
