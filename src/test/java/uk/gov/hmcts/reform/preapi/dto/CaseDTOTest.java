package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("PMD.LawOfDemeter")
class CaseDTOTest {

    private static Case caseEntity;

    @BeforeAll
    static void setUp() {
        caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        var court = new Court();
        court.setId(UUID.randomUUID());
        court.setRegions(Set.of(new Region()));
        caseEntity.setCourt(court);
        caseEntity.setReference("1234567890");
        caseEntity.setTest(false);
        caseEntity.setOrigin(RecordingOrigin.PRE);
        caseEntity.setDeletedAt(null);
        caseEntity.setCreatedAt(Timestamp.from(Instant.now()));
        caseEntity.setModifiedAt(Timestamp.from(Instant.now()));
    }

    @Test
    @DisplayName("CaseDTO.participants should be sorted by participant first name")
    public void testParticipantSorting() {
        var aCase = HelperFactory.createCase(
            HelperFactory.createCourt(CourtType.CROWN, "Example Court", "123"),
            "1234567890",
            false,
            null
        );
        aCase.setParticipants(Set.of(
            HelperFactory.createParticipant(new Case(), ParticipantType.WITNESS, "BBB", "BBB", null),
            HelperFactory.createParticipant(new Case(), ParticipantType.DEFENDANT, "CCC", "CCC", null),
            HelperFactory.createParticipant(new Case(), ParticipantType.DEFENDANT, "AAA", "AAA", null)
        ));

        var dto = new CaseDTO(aCase);

        var participants = dto.getParticipants();
        assertEquals("AAA", participants.get(0).getFirstName());
        assertEquals("BBB", participants.get(1).getFirstName());
        assertEquals("CCC", participants.get(2).getFirstName());
    }

    @Test
    @DisplayName("Should create a case from entity")
    void createCaseFromEntity() {
        var model = new CaseDTO(caseEntity);

        assertThat(model.getId()).isEqualTo(caseEntity.getId());
        assertThat(model.getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());
    }
}
