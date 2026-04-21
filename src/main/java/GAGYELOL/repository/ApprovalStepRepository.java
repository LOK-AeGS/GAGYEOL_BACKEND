package GAGYELOL.repository;

import GAGYELOL.entity.ApprovalRequest;
import GAGYELOL.entity.ApprovalStep;
import GAGYELOL.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {
    List<ApprovalStep> findByRequestAndApprovalOrder(ApprovalRequest request, Integer approvalOrder);
    Optional<ApprovalStep> findByRequestAndApprover(ApprovalRequest request, User approver);
    List<ApprovalStep> findByRequest(ApprovalRequest request);
    List<ApprovalStep> findByApproverAndAction(User approver, String action);
}
