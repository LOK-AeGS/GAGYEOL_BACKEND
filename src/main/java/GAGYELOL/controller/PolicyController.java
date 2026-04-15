package GAGYELOL.controller;

import GAGYELOL.config.JwtUtil;
import GAGYELOL.dto.PolicyUploadResponse;
import GAGYELOL.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;
    private final JwtUtil jwtUtil;

    @PostMapping("/upload")
    public ResponseEntity<PolicyUploadResponse> upload(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("policyName") String policyName,
            @RequestParam(value = "groupId", required = false) Long groupId
    ) {
        Long userId = jwtUtil.extractUserId(token.replace("Bearer ", ""));
        return ResponseEntity.ok(policyService.upload(file, policyName, userId, groupId));
    }
}
