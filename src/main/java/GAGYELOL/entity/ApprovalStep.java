package GAGYELOL.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_steps",
       uniqueConstraints = @UniqueConstraint(
           name = "approval_steps_request_approver_order_key",
           columnNames = {"request_id", "approver_id", "approval_order"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Column(name = "approval_order", nullable = false)
    private Integer approvalOrder;

    // PENDING / APPROVED / REJECTED / CANCELED
    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    public void approve(String comment) {
        this.action = "APPROVED";
        this.comment = comment;
        this.actedAt = LocalDateTime.now();
    }

    public void reject(String comment) {
        this.action = "REJECTED";
        this.comment = comment;
        this.actedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.action = "CANCELED";
        this.comment = "승인자 그룹 탈퇴로 자동 취소";
        this.actedAt = LocalDateTime.now();
    }
}
