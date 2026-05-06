package GAGYELOL.controller;

import GAGYELOL.config.JwtUtil;
import GAGYELOL.dto.dashboard.DashboardSummaryResponse;
import GAGYELOL.dto.dashboard.MonthlyDirectoryResponse;
import GAGYELOL.dto.dashboard.RecentApprovalsResponse;
import GAGYELOL.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final JwtUtil jwtUtil;

    // 이번 달 제출 건수 / 총 금액 / 처리 중 건수 / 규정 준수율
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @RequestHeader("Authorization") String token,
            @RequestParam Long groupId) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(dashboardService.getSummary(userId, groupId));
    }

    // 연/월별 결의서 디렉토리
    @GetMapping("/monthly-directory")
    public ResponseEntity<List<MonthlyDirectoryResponse>> getMonthlyDirectory(
            @RequestHeader("Authorization") String token,
            @RequestParam Long groupId) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(dashboardService.getMonthlyDirectory(userId, groupId));
    }

    // 결의서 상태 추적 (최근 5건, status 필터 선택)
    // status: IN_PROGRESS(검토중) | APPROVED(승인) | REJECTED(반려)
    @GetMapping("/recent-approvals")
    public ResponseEntity<RecentApprovalsResponse> getRecentApprovals(
            @RequestHeader("Authorization") String token,
            @RequestParam Long groupId,
            @RequestParam(required = false) String status) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(dashboardService.getRecentApprovals(userId, groupId, status));
    }

    private Long extractUserId(String token) {
        return jwtUtil.extractUserId(token.replace("Bearer ", ""));
    }
}
