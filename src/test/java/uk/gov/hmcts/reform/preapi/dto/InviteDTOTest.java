package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class InviteDTOTest {

    private static uk.gov.hmcts.reform.preapi.entities.PortalAccess portalAccess;
    private static uk.gov.hmcts.reform.preapi.entities.User user;

    @BeforeAll
    static void setUp() {
        user = new uk.gov.hmcts.reform.preapi.entities.User();
        user.setId(UUID.randomUUID());
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("example@example.com");
        user.setPhone("1234567890");
        user.setOrganisation("Org");

        portalAccess = new uk.gov.hmcts.reform.preapi.entities.PortalAccess();
        portalAccess.setId(UUID.randomUUID());
        portalAccess.setInvitedAt(Timestamp.from(java.time.Instant.now()));
        portalAccess.setUser(user);
        portalAccess.setCode("ABCDE");
        portalAccess.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        portalAccess.setModifiedAt(Timestamp.from(java.time.Instant.now()));
    }

    @DisplayName("Should create a invite from portalAccess entity")
    @Test
    void createInviteFromEntity() {
        var model = new InviteDTO(portalAccess);

        assertThat(model.getUserId()).isEqualTo(user.getId());
        assertThat(model.getFirstName()).isEqualTo(user.getFirstName());
        assertThat(model.getLastName()).isEqualTo(user.getLastName());
        assertThat(model.getEmail()).isEqualTo(user.getEmail());
        assertThat(model.getPhoneNumber()).isEqualTo(user.getPhone());
        assertThat(model.getOrganisation()).isEqualTo(user.getOrganisation());
        assertThat(model.getCode()).isEqualTo(portalAccess.getCode());
        assertThat(model.getInvitedAt()).isEqualTo(portalAccess.getInvitedAt());
    }
}
