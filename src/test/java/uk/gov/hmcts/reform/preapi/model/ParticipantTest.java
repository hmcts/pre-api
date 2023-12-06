package uk.gov.hmcts.reform.preapi.model;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class ParticipantTest {

    private static uk.gov.hmcts.reform.preapi.entities.Participant participantEntity;

    @BeforeAll
    static void setUp() {
        participantEntity = new uk.gov.hmcts.reform.preapi.entities.Participant();
        participantEntity.setId(UUID.randomUUID());
        participantEntity.setFirstName("John");
        participantEntity.setLastName("Smith");
        participantEntity.setDeletedAt(null);
        participantEntity.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        participantEntity.setModifiedAt(Timestamp.from(java.time.Instant.now()));
    }

    @DisplayName("Should create a participant from entity")
    @Test
    void createParticipantFromEntity() {
        var model = new Participant(participantEntity);

        assertThat(model.getId()).isEqualTo(participantEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(participantEntity.getFirstName());
    }
}
