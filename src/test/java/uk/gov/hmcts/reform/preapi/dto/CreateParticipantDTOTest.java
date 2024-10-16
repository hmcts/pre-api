package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Participant;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class CreateParticipantDTOTest {

    private static Participant participantEntity;

    @BeforeAll
    static void setUp() {
        participantEntity = new Participant();
        participantEntity.setId(UUID.randomUUID());
        participantEntity.setFirstName("John");
        participantEntity.setLastName("Smith");
        participantEntity.setDeletedAt(null);
        participantEntity.setCreatedAt(Timestamp.from(Instant.now()));
        participantEntity.setModifiedAt(Timestamp.from(Instant.now()));
    }

    @DisplayName("Should create a participant from entity")
    @Test
    void createParticipantFromEntity() {
        var model = new CreateParticipantDTO(participantEntity);

        assertThat(model.getId()).isEqualTo(participantEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(participantEntity.getFirstName());
    }
}
