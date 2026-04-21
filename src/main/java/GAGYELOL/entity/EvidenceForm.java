package GAGYELOL.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "evidence_forms")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceForm {

    @EmbeddedId
    private EvidenceFormId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("evidenceId")
    @JoinColumn(name = "evidence_id")
    private Evidence evidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("formId")
    @JoinColumn(name = "form_id")
    private Form form;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class EvidenceFormId implements java.io.Serializable {
        private Long evidenceId;
        private Long formId;
    }
}
