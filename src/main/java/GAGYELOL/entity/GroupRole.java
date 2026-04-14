package GAGYELOL.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_roles",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "approval_order"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private UserGroup group;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    // 0=최하위(작성자), 숫자 높을수록 높은 결재권
    @Column(name = "approval_order", nullable = false)
    private Integer approvalOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
