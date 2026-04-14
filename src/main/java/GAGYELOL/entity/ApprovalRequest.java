package GAGYELOL.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_requests")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private UserGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id")
    private Evidence evidence;

    // 현재 양식지 내용 (JSON)
    @Column(name = "filled_fields", columnDefinition = "TEXT")
    private String filledFields;

    // 현재 결재 진행 단계 (approval_order 기준)
    @Column(name = "current_approval_order", nullable = false)
    private Integer currentApprovalOrder;

    // DRAFT / IN_PROGRESS / APPROVED / REJECTED
    @Column(nullable = false)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateFilledFields(String filledFields) {
        this.filledFields = filledFields;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateApprovalOrder(Integer order) {
        this.currentApprovalOrder = order;
        this.updatedAt = LocalDateTime.now();
    }
}
