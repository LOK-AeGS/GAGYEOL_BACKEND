package GAGYELOL.controller;

import GAGYELOL.config.JwtUtil;
import GAGYELOL.dto.FormUploadResponse;
import GAGYELOL.service.FormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
            @RequestParam(value = "policyId", required = false) Long policyId
    ) {
        Long userId = jwtUtil.extractUserId(token.replace("Bearer ", ""));
        return ResponseEntity.ok(formService.upload(file, formName, userId, paymentType, policyId));
    }

    @PostMapping("/{formId}/reanalyze")
    public ResponseEntity<FormUploadResponse> reanalyze(
            @PathVariable Long formId,
            @RequestParam(value = "policyId", required = false) Long policyId
    ) {
        return ResponseEntity.ok(formService.reanalyze(formId, policyId));
    }
}
