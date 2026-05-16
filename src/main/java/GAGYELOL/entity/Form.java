package GAGYELOL.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "form")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Form {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private UserGroup group;

    @Column(name = "form_name", nullable = false)
    private String formName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "form_fields", columnDefinition = "TEXT")
    private String formFields;

    @Column(name = "payment_type", length = 10)
    private String paymentType; // CARD, CASH, BOTH

    @Column(name = "generated_fields", columnDefinition = "TEXT")
    private String generatedFields; // JSON 배열: LLM으로 생성할 필드명 목록

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void updateFormName(String formName) {
        this.formName = formName;
    }

    public void updateAnalysis(String description, String formFields, String generatedFields) {
        this.description = description;
        this.formFields = formFields;
        this.generatedFields = generatedFields;
    }
}
