package GAGYELOL.repository;

import GAGYELOL.entity.GroupRole;
import GAGYELOL.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRoleRepository extends JpaRepository<GroupRole, Long> {
    List<GroupRole> findByGroupOrderByApprovalOrderAsc(UserGroup group);
    Optional<GroupRole> findByGroupAndApprovalOrder(UserGroup group, Integer approvalOrder);
    List<GroupRole> findByGroupAndApprovalOrderGreaterThanOrderByApprovalOrderAsc(UserGroup group, Integer approvalOrder);
}
