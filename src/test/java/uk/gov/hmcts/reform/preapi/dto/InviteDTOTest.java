package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class InviteDTOTest {

    private static uk.gov.hmcts.reform.preapi.entities.Invite inviteEntity;

    @BeforeAll
    static void setUp() {
        inviteEntity = new uk.gov.hmcts.reform.preapi.entities.Invite();
        inviteEntity.setId(UUID.randomUUID());
        inviteEntity.setFirstName("Firstname");
        inviteEntity.setLastName("Lastname");
        inviteEntity.setEmail("example@example.com");
        inviteEntity.setOrganisation("Organisation");
        inviteEntity.setPhone("0123456789");
        inviteEntity.setCode("ABCDE");
        inviteEntity.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        inviteEntity.setModifiedAt(Timestamp.from(java.time.Instant.now()));
    }

    @DisplayName("Should create a invite from entity")
    @Test
    void createInviteFromEntity() {
        var model = new InviteDTO(inviteEntity);

        assertThat(model.getId()).isEqualTo(inviteEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(inviteEntity.getFirstName());
        assertThat(model.getLastName()).isEqualTo(inviteEntity.getLastName());
        assertThat(model.getEmail()).isEqualTo(inviteEntity.getEmail());
        assertThat(model.getOrganisation()).isEqualTo(inviteEntity.getOrganisation());
        assertThat(model.getPhone()).isEqualTo(inviteEntity.getPhone());
        assertThat(model.getCode()).isEqualTo(inviteEntity.getCode());
    }
}
