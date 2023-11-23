package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedOnEntity;

import java.sql.Date;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "bookings")
public class Booking extends CreatedModifiedOnEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", referencedColumnName = "id")
    private Case caseId;

    @Column(nullable = false)
    private Date date;

    @Column
    private boolean deleted = false;
}
