package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BookingDTOTest {
    @DisplayName("BookingDTO.participants should be sorted by participant first name")
    @Test
    public void testParticipantSorting() {
        var bookingEntity = createBookingEntity();
        var bookingDTO = new BookingDTO(bookingEntity);

        var participants = bookingDTO.getParticipants();
        assertEquals("AAA", participants.get(0).getFirstName());
        assertEquals("BBB", participants.get(1).getFirstName());
        assertEquals("CCC", participants.get(2).getFirstName());
    }

    @DisplayName("BookingDTO.captureSessions should be sorted by capture session id")
    @Test
    public void testCaptureSessionSorting() {
        var bookingEntity = createBookingEntity();
        var bookingDTO = new BookingDTO(bookingEntity);

        var captureSessions = bookingDTO.getCaptureSessions();
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), captureSessions.get(0).getId());
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), captureSessions.get(1).getId());
        assertEquals(UUID.fromString("33333333-3333-3333-3333-333333333333"), captureSessions.get(2).getId());
    }

    @DisplayName("BookingDTO.shares should be sorted by user shared with first name")
    @Test
    public void testShareBookingSorting() {
        var bookingEntity = createBookingEntity();
        var bookingDTO = new BookingDTO(bookingEntity);

        var shares = bookingDTO.getShares();
        assertEquals("AAA", shares.get(0).getSharedWithUser().getFirstName());
        assertEquals("BBB", shares.get(1).getSharedWithUser().getFirstName());
        assertEquals("CCC", shares.get(2).getSharedWithUser().getFirstName());
    }

    private Booking createBookingEntity() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "123");
        var aCase = HelperFactory.createCase(court, "1234567890", false, null);
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);
        booking.setScheduledFor(Timestamp.from(Instant.now()));

        var participantB = HelperFactory.createParticipant(new Case(), ParticipantType.WITNESS, "BBB", "BBB", null);
        var participantC = HelperFactory.createParticipant(new Case(), ParticipantType.DEFENDANT, "CCC", "CCC", null);
        var participantA = HelperFactory.createParticipant(new Case(), ParticipantType.DEFENDANT, "AAA", "AAA", null);
        var participants = Set.of(participantB, participantC, participantA);
        booking.setParticipants(participants);

        var captureSession2 = HelperFactory.createCaptureSession(booking, RecordingOrigin.PRE, null, null, null, null, null, null, null, null);
        captureSession2.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        var captureSession3 = HelperFactory.createCaptureSession(booking, RecordingOrigin.PRE, null, null, null, null, null, null, null, null);
        captureSession3.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        var captureSession1 = HelperFactory.createCaptureSession(booking, RecordingOrigin.PRE, null, null, null, null, null, null, null, null);
        captureSession1.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        booking.setCaptureSessions(Set.of(captureSession2, captureSession3, captureSession1));

        var sharer = HelperFactory.createUser("Sharer", "Sharer", "example@example.com", null, null, null);
        booking.setShares(Set.of(
            HelperFactory.createShareBooking(
                HelperFactory.createUser("BBB", "BBB", "example@example.com", null, null, null),
                sharer,
                booking,
                null
            ),
            HelperFactory.createShareBooking(
                HelperFactory.createUser("CCC", "CCC", "example@example.com", null, null, null),
                sharer,
                booking,
                null
            ),
            HelperFactory.createShareBooking(
                HelperFactory.createUser("AAA", "AAA", "example@example.com", null, null, null),
                sharer,
                booking,
                null
            )
        ));

        return booking;
    }
}
