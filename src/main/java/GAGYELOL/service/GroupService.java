package GAGYELOL.service;

import GAGYELOL.dto.group.AssignRoleRequest;
import GAGYELOL.dto.group.CreateGroupRequest;
import GAGYELOL.dto.group.GroupResponse;
import GAGYELOL.entity.*;
import GAGYELOL.repository.*;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private final UserRepository userRepository;
    private final UserGroupRepository groupRepository;
    private final GroupRoleRepository roleRepository;
    private final GroupMemberRepository memberRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApprovalRequestRepository approvalRequestRepository;

    public GroupResponse createGroup(Long ownerId, CreateGroupRequest request) {
        User owner = findUser(ownerId);

        UserGroup group = groupRepository.save(UserGroup.builder()
                .name(request.getName())
                .inviteCode(generateInviteCode())
                .owner(owner)
                .build());

        // 역할 생성 (입력 순서 = approval_order)
        List<String> roleNames = request.getRoles();
        for (int i = 0; i < roleNames.size(); i++) {
            roleRepository.save(GroupRole.builder()
                    .group(group)
                    .roleName(roleNames.get(i))
                    .approvalOrder(i)
                    .build());
        }

        // 대표자를 최고 역할(마지막 순서)로 자동 등록
        GroupRole ownerRole = roleRepository.findByGroupAndApprovalOrder(group, roleNames.size() - 1)
                .orElseThrow();
        memberRepository.save(GroupMember.builder()
                .group(group)
                .user(owner)
                .role(ownerRole)
                .build());

        return getGroupDetail(group.getId());
    }

    public GroupResponse joinGroup(Long userId, String inviteCode) {
        User user = findUser(userId);
        UserGroup group = groupRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대코드입니다."));

        if (memberRepository.existsByGroupAndUser(group, user)) {
            throw new IllegalArgumentException("이미 가입된 그룹입니다.");
        }

        // 가입 시 최하위 역할(approval_order=0)로 자동 배정
        GroupRole defaultRole = roleRepository.findByGroupAndApprovalOrder(group, 0)
                .orElseThrow(() -> new IllegalArgumentException("그룹 역할 설정이 올바르지 않습니다."));

        memberRepository.save(GroupMember.builder()
                .group(group)
                .user(user)
                .role(defaultRole)
                .build());

        return getGroupDetail(group.getId());
    }

    public void kickMember(Long ownerId, Long groupId, Long targetUserId) {
        UserGroup group = findGroup(groupId);
        validateOwner(ownerId, group);

        User targetUser = findUser(targetUserId);
        if (group.getOwner().getId().equals(targetUserId)) {
            throw new IllegalArgumentException("그룹 대표자는 추방할 수 없습니다.");
        }

        GroupMember member = memberRepository.findByGroupAndUser(group, targetUser)
                .orElseThrow(() -> new IllegalArgumentException("해당 그룹의 멤버가 아닙니다."));

        // 탈퇴 멤버의 PENDING approval_steps 취소 처리
        List<ApprovalStep> pendingSteps = approvalStepRepository.findByApproverAndAction(targetUser, "PENDING");
        for (ApprovalStep step : pendingSteps) {
            ApprovalRequest request = step.getRequest();
            if (!"IN_PROGRESS".equals(request.getStatus())) continue;
            if (!request.getGroup().getId().equals(groupId)) continue;

            step.cancel();
            approvalStepRepository.save(step);
            log.info("멤버 추방으로 approval_step 취소 - stepId={}, requestId={}", step.getId(), request.getId());

            // 같은 단계에 PENDING이 남아있는지 확인 → 없으면 다음 단계 진행
            advanceIfAllDone(request);
        }

        memberRepository.delete(member);
        log.info("멤버 추방 완료 - groupId={}, userId={}", groupId, targetUserId);
    }

    private void advanceIfAllDone(ApprovalRequest request) {
        List<ApprovalStep> sameOrderSteps = approvalStepRepository
                .findByRequestAndApprovalOrder(request, request.getCurrentApprovalOrder());

        boolean anyPending = sameOrderSteps.stream().anyMatch(s -> "PENDING".equals(s.getAction()));
        if (anyPending) return;

        boolean anyApproved = sameOrderSteps.stream().anyMatch(s -> "APPROVED".equals(s.getAction()));

        List<GroupRole> nextRoles = roleRepository
                .findByGroupAndApprovalOrderGreaterThanOrderByApprovalOrderAsc(
                        request.getGroup(), request.getCurrentApprovalOrder());

        if (nextRoles.isEmpty()) {
            // 마지막 단계였으면 승인/취소 여부에 따라 최종 처리
            request.updateStatus(anyApproved ? "APPROVED" : "CANCELED");
            log.info("결재 최종 처리 (멤버 추방) - requestId={}, status={}", request.getId(), request.getStatus());
        } else {
            // 다음 단계로 진행
            int nextOrder = nextRoles.get(0).getApprovalOrder();
            request.updateApprovalOrder(nextOrder);
            GroupRole nextRole = nextRoles.get(0);
            List<GroupMember> nextApprovers = memberRepository.findByGroupAndRole(request.getGroup(), nextRole);
            for (GroupMember approver : nextApprovers) {
                approvalStepRepository.save(ApprovalStep.builder()
                        .request(request)
                        .approver(approver.getUser())
                        .approvalOrder(nextOrder)
                        .action("PENDING")
                        .build());
            }
            log.info("결재 다음 단계 진행 (멤버 추방) - requestId={}, nextOrder={}", request.getId(), nextOrder);
        }
        approvalRequestRepository.save(request);
    }

    public GroupResponse assignRole(Long ownerId, Long groupId, AssignRoleRequest request) {
        UserGroup group = findGroup(groupId);
        validateOwner(ownerId, group);

        User targetUser = findUser(request.getUserId());
        GroupRole newRole = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("역할을 찾을 수 없습니다."));
        if (!newRole.getGroup().getId().equals(groupId)) {
            throw new IllegalArgumentException("해당 그룹의 역할이 아닙니다.");
        }

        GroupMember member = memberRepository.findByGroupAndUser(group, targetUser)
                .orElseThrow(() -> new IllegalArgumentException("해당 그룹의 멤버가 아닙니다."));

        int oldOrder = member.getRole().getApprovalOrder();
        int newOrder = newRole.getApprovalOrder();
        member.updateRole(newRole);

        if (oldOrder != newOrder) {
            handleRoleChangeForPendingApprovals(group, targetUser, oldOrder, newOrder);
        }

        return getGroupDetail(groupId);
    }

    private void handleRoleChangeForPendingApprovals(UserGroup group, User targetUser, int oldOrder, int newOrder) {
        List<ApprovalRequest> inProgressRequests = approvalRequestRepository.findByGroupAndStatus(group, "IN_PROGRESS");

        for (ApprovalRequest req : inProgressRequests) {
            int currentOrder = req.getCurrentApprovalOrder();

            // 강등: 기존 역할(oldOrder)의 PENDING 스텝 보유 중이고 현재 결재 단계가 oldOrder면 CANCELED
            if (currentOrder == oldOrder) {
                approvalStepRepository.findByRequestAndApproverAndApprovalOrder(req, targetUser, oldOrder)
                        .filter(s -> "PENDING".equals(s.getAction()))
                        .ifPresent(s -> {
                            s.cancel();
                            approvalStepRepository.save(s);
                            log.info("역할 변경으로 PENDING 스텝 취소 - requestId={}, userId={}", req.getId(), targetUser.getId());
                            advanceIfAllDone(req);
                        });
            }

            // 승급: 새 역할(newOrder)이 현재 결재 단계이면 PENDING 스텝 추가
            if (currentOrder == newOrder) {
                boolean alreadyHasStep = approvalStepRepository
                        .findByRequestAndApproverAndApprovalOrder(req, targetUser, newOrder)
                        .isPresent();
                if (!alreadyHasStep) {
                    approvalStepRepository.save(ApprovalStep.builder()
                            .request(req)
                            .approver(targetUser)
                            .approvalOrder(newOrder)
                            .action("PENDING")
                            .build());
                    log.info("역할 변경으로 PENDING 스텝 추가 - requestId={}, userId={}", req.getId(), targetUser.getId());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupDetail(Long groupId) {
        UserGroup group = findGroup(groupId);
        List<GroupRole> roles = roleRepository.findByGroupOrderByApprovalOrderAsc(group);
        List<GroupMember> members = memberRepository.findByGroup(group);

        return GroupResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .inviteCode(group.getInviteCode())
                .ownerName(group.getOwner().getName())
                .roles(roles.stream()
                        .map(r -> GroupResponse.RoleSummary.builder()
                                .roleId(r.getId())
                                .roleName(r.getRoleName())
                                .approvalOrder(r.getApprovalOrder())
                                .build())
                        .toList())
                .members(members.stream()
                        .map(m -> GroupResponse.MemberSummary.builder()
                                .userId(m.getUser().getId())
                                .name(m.getUser().getName())
                                .email(m.getUser().getEmail())
                                .roleName(m.getRole().getRoleName())
                                .approvalOrder(m.getRole().getApprovalOrder())
                                .build())
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(Long userId) {
        User user = findUser(userId);
        return memberRepository.findByUser(user).stream()
                .map(m -> getGroupDetail(m.getGroup().getId()))
                .toList();
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private UserGroup findGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("그룹을 찾을 수 없습니다."));
    }

    private void validateOwner(Long userId, UserGroup group) {
        if (!group.getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("그룹 대표자만 권한을 변경할 수 있습니다.");
        }
    }
}
