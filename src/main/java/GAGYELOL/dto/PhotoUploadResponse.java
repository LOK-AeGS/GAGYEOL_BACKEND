package GAGYELOL.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PhotoUploadResponse {
    private String photoId;
    private String filePath;   // CompleteFormRequest.imageFields 값으로 그대로 사용
    private String fileName;
    private String label;      // 사용자가 지정한 사진 용도 (예: "학생증", "사진")
    private LocalDateTime uploadedAt;
}
