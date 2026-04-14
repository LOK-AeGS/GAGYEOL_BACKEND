package GAGYELOL.service;

import GAGYELOL.dto.group.AssignRoleRequest;
import GAGYELOL.dto.group.CreateGroupRequest;
import GAGYELOL.dto.group.GroupResponse;
import GAGYELOL.entity.*;
import GAGYELOL.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private final UserRepository userRepository;
    private final UserGroupRepository groupRepository;
    private final GroupRoleRepository roleRepository;
    private final GroupMemberRepository memberRepository;

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

    public GroupResponse assignRole(Long ownerId, Long groupId, AssignRoleRequest request) {
        UserGroup group = findGroup(groupId);
        validateOwner(ownerId, group);

        User targetUser = findUser(request.getUserId());
        GroupRole newRole = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("역할을 찾을 수 없습니다."));

        GroupMember member = memberRepository.findByGroupAndUser(group, targetUser)
                .orElseThrow(() -> new IllegalArgumentException("해당 그룹의 멤버가 아닙니다."));

        member.updateRole(newRole);
        return getGroupDetail(groupId);
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
