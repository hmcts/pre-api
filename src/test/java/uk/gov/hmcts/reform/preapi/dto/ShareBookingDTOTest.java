package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class ShareBookingDTOTest {

    private static ShareBooking shareBookingEntity;

    @BeforeAll
    static void setUp() {
        var booking = new uk.gov.hmcts.reform.preapi.entities.Booking();
        booking.setId(UUID.randomUUID());

        var sharedWithUser = new uk.gov.hmcts.reform.preapi.entities.User();
        sharedWithUser.setId(UUID.randomUUID());
        sharedWithUser.setFirstName("John");
        sharedWithUser.setLastName("Smith");

        var sharedByUser = new uk.gov.hmcts.reform.preapi.entities.User();
        sharedByUser.setId(UUID.randomUUID());
        sharedByUser.setFirstName("Jane");
        sharedByUser.setLastName("Smith");

        shareBookingEntity = new ShareBooking();
        shareBookingEntity.setId(UUID.randomUUID());
        shareBookingEntity.setBooking(booking);
        shareBookingEntity.setSharedBy(sharedByUser);
        shareBookingEntity.setSharedWith(sharedWithUser);
        shareBookingEntity.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        shareBookingEntity.setDeletedAt(null);
    }

    @DisplayName("Should create a shared recording from entity")
    @Test
    void createParticipantFromEntity() {
        var model = new ShareBookingDTO(shareBookingEntity);

        assertThat(model.getId()).isEqualTo(shareBookingEntity.getId());
        assertThat(model.getBookingId()).isEqualTo(shareBookingEntity.getBooking().getId());
        assertThat(model.getSharedByUserId()).isEqualTo(shareBookingEntity.getSharedBy().getId());
        assertThat(model.getSharedWithUserId()).isEqualTo(shareBookingEntity.getSharedWith().getId());
    }
}
