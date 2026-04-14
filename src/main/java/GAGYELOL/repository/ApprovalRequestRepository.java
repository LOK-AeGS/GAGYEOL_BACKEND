package GAGYELOL.repository;

import GAGYELOL.entity.ApprovalRequest;
import GAGYELOL.entity.User;
import GAGYELOL.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    List<ApprovalRequest> findByGroupOrderByCreatedAtDesc(UserGroup group);
    List<ApprovalRequest> findByRequesterOrderByCreatedAtDesc(User requester);
}
