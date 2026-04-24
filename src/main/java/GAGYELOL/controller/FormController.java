package GAGYELOL.controller;

import GAGYELOL.config.JwtUtil;
import GAGYELOL.dto.FormUploadResponse;
import GAGYELOL.service.FormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {

    private final FormService formService;
    private final JwtUtil jwtUtil;

    @PostMapping("/upload")
    public ResponseEntity<FormUploadResponse> upload(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("formName") String formName,
            @RequestParam(value = "paymentType", defaultValue = "BOTH") String paymentType,
            @RequestParam(value = "policyId", required = false) Long policyId,
            @RequestParam(value = "groupId", required = false) Long groupId
    ) {
        Long userId = jwtUtil.extractUserId(token.replace("Bearer ", ""));
        return ResponseEntity.ok(formService.upload(file, formName, userId, paymentType, policyId, groupId));
    }

    @PostMapping("/{formId}/reanalyze")
    public ResponseEntity<FormUploadResponse> reanalyze(
            @PathVariable Long formId,
            @RequestParam(value = "policyId", required = false) Long policyId
    ) {
        return ResponseEntity.ok(formService.reanalyze(formId, policyId));
    }

    // 1. 양식지 목록 조회 (그룹별)
    @GetMapping
    public ResponseEntity<List<FormUploadResponse>> getFormsByGroup(
            @RequestHeader("Authorization") String token,
            @RequestParam Long groupId
    ) {
        Long userId = jwtUtil.extractUserId(token.replace("Bearer ", ""));
        return ResponseEntity.ok(formService.getFormsByGroup(groupId, userId));
    }

    // 2. 양식지 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<FormUploadResponse> getForm(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id
    ) {
        Long userId = jwtUtil.extractUserId(token.replace("Bearer ", ""));
        return ResponseEntity.ok(formService.getForm(id, userId));
    }

    // ResponseStatusException → 올바른 HTTP 상태코드로 변환 (GlobalExceptionHandler가 500으로 처리하는 것 방지)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getReason() != null ? e.getReason() : e.getMessage()));
    }

    // 3. 양식지 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteForm(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id
    ) {
        Long userId = jwtUtil.extractUserId(token.replace("Bearer ", ""));
        formService.deleteForm(id, userId);
        return ResponseEntity.noContent().build();
    }
}
