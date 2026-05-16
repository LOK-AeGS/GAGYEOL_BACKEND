package GAGYELOL.controller;

import GAGYELOL.dto.PhotoUploadResponse;
import GAGYELOL.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    /**
     * 사진(학생증, 도장 등) 업로드
     *
     * 반환된 filePath를 POST /api/evidence/{id}/complete 요청의
     * imageFields 맵 값으로 그대로 사용하면 양식지 사진란에 자동 삽입됩니다.
     *
     * 예) imageFields: { "사진": "<filePath>" }
     */
    @PostMapping("/upload")
    public ResponseEntity<PhotoUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "label", defaultValue = "사진") String label
    ) {
        return ResponseEntity.ok(photoService.upload(file, label));
    }

    /**
     * 업로드한 사진 삭제
     */
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> delete(@PathVariable String photoId) {
        photoService.delete(photoId);
        return ResponseEntity.noContent().build();
    }
}
