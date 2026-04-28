package GAGYELOL.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class FormUploadResponse {
    private Long formId;
    private String formName;
    private String description;
    private String paymentType;
    private List<String> fields;
    private List<String> generatedFields; // LLM으로 사업명 기반 생성할 필드 목록
    private LocalDateTime createdAt;
}
