package GAGYELOL.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_edit_history")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_id", nullable = false)
    private User editor;

    @Column(name = "before_fields", columnDefinition = "TEXT")
    private String beforeFields;

    @Column(name = "after_fields", columnDefinition = "TEXT")
    private String afterFields;

    @CreationTimestamp
    @Column(name = "edited_at", updatable = false)
    private LocalDateTime editedAt;
}
