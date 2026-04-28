package GAGYELOL.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "groups")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "invite_code", unique = true, nullable = false)
    private String inviteCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_policy_id")
    private Policy activePolicy;

    @Column(name = "payer_name", length = 100)
    private String payerName;

    @Column(name = "payer_affiliation", length = 200)
    private String payerAffiliation;

    @Column(name = "payer_student_id", length = 50)
    private String payerStudentId;

    @Column(name = "payer_phone", length = 50)
    private String payerPhone;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void updateActivePolicy(Policy policy) {
        this.activePolicy = policy;
    }

    public void updatePayerInfo(String name, String affiliation, String studentId, String phone) {
        this.payerName = name;
        this.payerAffiliation = affiliation;
        this.payerStudentId = studentId;
        this.payerPhone = phone;
    }
}
