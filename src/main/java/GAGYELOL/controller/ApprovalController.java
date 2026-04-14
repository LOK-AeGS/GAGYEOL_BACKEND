package GAGYELOL.controller;

import GAGYELOL.config.JwtUtil;
import GAGYELOL.dto.approval.ApprovalResponse;
import GAGYELOL.dto.approval.ApproveRequest;
import GAGYELOL.dto.approval.CreateApprovalRequest;
import GAGYELOL.dto.approval.EditFieldsRequest;
import GAGYELOL.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;
    private final JwtUtil jwtUtil;

    // 결재 요청 생성
    @PostMapping
    public ResponseEntity<ApprovalResponse> createRequest(
            @RequestHeader("Authorization") String token,
            @RequestBody CreateApprovalRequest request) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(approvalService.createRequest(userId, request));
    }

    // 결재 단건 조회
    @GetMapping("/{requestId}")
    public ResponseEntity<ApprovalResponse> getRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(approvalService.getRequest(requestId));
    }

    // 그룹 결재 목록 조회
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<ApprovalResponse>> getGroupRequests(@PathVariable Long groupId) {
        return ResponseEntity.ok(approvalService.getGroupRequests(groupId));
    }

    // 승인
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ApprovalResponse> approve(
            @RequestHeader("Authorization") String token,
            @PathVariable Long requestId,
            @RequestBody ApproveRequest request) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(approvalService.approve(userId, requestId, request));
    }

    // 반려
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApprovalResponse> reject(
            @RequestHeader("Authorization") String token,
            @PathVariable Long requestId,
            @RequestBody ApproveRequest request) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(approvalService.reject(userId, requestId, request));
    }

    // 양식지 수정
    @PutMapping("/{requestId}/fields")
    public ResponseEntity<ApprovalResponse> editFields(
            @RequestHeader("Authorization") String token,
            @PathVariable Long requestId,
            @RequestBody EditFieldsRequest request) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(approvalService.editFields(userId, requestId, request));
    }

    private Long extractUserId(String token) {
        return jwtUtil.extractUserId(token.replace("Bearer ", ""));
    }
}
