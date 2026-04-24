package GAGYELOL.repository;

import GAGYELOL.entity.GroupMember;
import GAGYELOL.entity.GroupRole;
import GAGYELOL.entity.User;
import GAGYELOL.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    Optional<GroupMember> findByGroupAndUser(UserGroup group, User user);
    List<GroupMember> findByGroup(UserGroup group);
    List<GroupMember> findByGroupAndRole(UserGroup group, GroupRole role);
    List<GroupMember> findByUser(User user);
    boolean existsByGroupAndUser(UserGroup group, User user);
    boolean existsByRole(GroupRole role);
}
