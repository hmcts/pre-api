package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "edit_requests")
public class EditRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recording_id", nullable = false)
    private Recording recording;

    @Column(nullable = false, columnDefinition = "json")
    private String instruction;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String reason;
}
