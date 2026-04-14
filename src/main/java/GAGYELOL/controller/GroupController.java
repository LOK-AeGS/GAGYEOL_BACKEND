package GAGYELOL.controller;

import GAGYELOL.config.JwtUtil;
import GAGYELOL.dto.group.AssignRoleRequest;
import GAGYELOL.dto.group.CreateGroupRequest;
import GAGYELOL.dto.group.GroupResponse;
import GAGYELOL.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final JwtUtil jwtUtil;

    // 그룹 생성
    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @RequestHeader("Authorization") String token,
            @RequestBody CreateGroupRequest request) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(groupService.createGroup(userId, request));
    }

    // 초대코드로 그룹 가입
    @PostMapping("/join/{inviteCode}")
    public ResponseEntity<GroupResponse> joinGroup(
            @RequestHeader("Authorization") String token,
            @PathVariable String inviteCode) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(groupService.joinGroup(userId, inviteCode));
    }

    // 그룹 상세 조회
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroupDetail(groupId));
    }

    // 내 그룹 목록
    @GetMapping("/my")
    public ResponseEntity<List<GroupResponse>> getMyGroups(
            @RequestHeader("Authorization") String token) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(groupService.getMyGroups(userId));
    }

    // 역할 부여 (대표자만 가능)
    @PutMapping("/{groupId}/members/role")
    public ResponseEntity<GroupResponse> assignRole(
            @RequestHeader("Authorization") String token,
            @PathVariable Long groupId,
            @RequestBody AssignRoleRequest request) {
        Long userId = extractUserId(token);
        return ResponseEntity.ok(groupService.assignRole(userId, groupId, request));
    }

    private Long extractUserId(String token) {
        return jwtUtil.extractUserId(token.replace("Bearer ", ""));
    }
}
