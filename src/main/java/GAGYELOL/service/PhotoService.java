package GAGYELOL.service;

import GAGYELOL.dto.PhotoUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class PhotoService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif"
    );

    @Value("${file.upload.photo-dir:./uploads/photos}")
    private String uploadDir;

    public PhotoUploadResponse upload(MultipartFile file, String label) {
        String originalName = file.getOriginalFilename();
        String ext = extractExtension(originalName);

        if (ext.isEmpty()) {
            ext = contentTypeToExtension(file.getContentType());
        }

        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지원하지 않는 이미지 형식입니다. 허용 형식: " + ALLOWED_EXTENSIONS
                    + " (전달된 파일명: " + originalName + ", Content-Type: " + file.getContentType() + ")");
        }

        String photoId = UUID.randomUUID().toString();
        String savedName = photoId + ext;

        try {
            Path dirPath = Paths.get(uploadDir);
            Files.createDirectories(dirPath);
            Path filePath = dirPath.resolve(savedName);
            Files.write(filePath, file.getBytes());
            log.info("사진 업로드 완료 - label={}, path={}", label, filePath);

            return PhotoUploadResponse.builder()
                    .photoId(photoId)
                    .filePath(filePath.toString())
                    .fileName(originalName)
                    .label(label)
                    .uploadedAt(LocalDateTime.now())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("사진 파일 저장 실패: " + e.getMessage(), e);
        }
    }

    public void delete(String photoId) {
        String ext = findFileExtension(photoId);
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다: " + photoId);
        }
        try {
            Path filePath = Paths.get(uploadDir).resolve(photoId + ext);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("사진 삭제 완료 - photoId={}", photoId);
            }
        } catch (IOException e) {
            throw new RuntimeException("사진 파일 삭제 실패: " + e.getMessage(), e);
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }

    private String contentTypeToExtension(String contentType) {
        if (contentType == null) return "";
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif"  -> ".gif";
            default -> "";
        };
    }

    private String findFileExtension(String photoId) {
        for (String ext : ALLOWED_EXTENSIONS) {
            Path candidate = Paths.get(uploadDir).resolve(photoId + ext);
            if (Files.exists(candidate)) return ext;
        }
        return null;
    }
}
