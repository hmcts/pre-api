package uk.gov.hmcts.reform.preapi.model;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Court;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseTest {

    private static uk.gov.hmcts.reform.preapi.entities.Case caseEntity;

    @BeforeAll
    static void setUp() {
        caseEntity = new uk.gov.hmcts.reform.preapi.entities.Case();
        caseEntity.setId(UUID.randomUUID());
        var court = new Court();
        court.setId(UUID.randomUUID());
        caseEntity.setCourt(court);
        caseEntity.setReference("1234567890");
        caseEntity.setTest(false);
        caseEntity.setDeletedAt(null);
        caseEntity.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        caseEntity.setModifiedAt(Timestamp.from(java.time.Instant.now()));
    }

    @DisplayName("Should create a case from entity")
    @Test
    void createCaseFromEntity() {
        var model = new Case(caseEntity);

        assertThat(model.getId()).isEqualTo(caseEntity.getId());
        assertThat(model.getCourtId()).isEqualTo(caseEntity.getCourt().getId());
    }
}
