package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class CaseDTOTest {

    private static uk.gov.hmcts.reform.preapi.entities.Case caseEntity;

    @BeforeAll
    static void setUp() {
        caseEntity = new uk.gov.hmcts.reform.preapi.entities.Case();
        caseEntity.setId(UUID.randomUUID());
        var court = new Court();
        court.setId(UUID.randomUUID());
        court.setRegions(new HashSet<>(List.of(new Region())));
        caseEntity.setCourt(court);
        caseEntity.setReference("1234567890");
        caseEntity.setTest(false);;
        caseEntity.setDeletedAt(null);
        caseEntity.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        caseEntity.setModifiedAt(Timestamp.from(java.time.Instant.now()));
    }

    @DisplayName("Should create a case from entity")
    @Test
    void createCaseFromEntity() {
        var model = new CaseDTO(caseEntity);

        assertThat(model.getId()).isEqualTo(caseEntity.getId());
        assertThat(model.getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());
    }
}
