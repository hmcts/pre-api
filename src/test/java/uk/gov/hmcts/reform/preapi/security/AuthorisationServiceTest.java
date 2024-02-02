package uk.gov.hmcts.reform.preapi.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AuthorisationService.class)
public class AuthorisationServiceTest {

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private CaseRepository caseRepository;

    @MockBean
    private ParticipantRepository participantRepository;

    @MockBean
    private CaptureSessionRepository captureSessionRepository;

    @MockBean
    private RecordingRepository recordingRepository;

    @Autowired
    private AuthorisationService authorisationService;
    private UserAuthentication authenticationUser;

    @BeforeEach
    void setUp() {
        authenticationUser = mock(UserAuthentication.class);
    }

    @DisplayName("Should grant access to booking when booking id is null")
    @Test
    void hasBookingAccessIdNull() {
        assertTrue(authorisationService.hasBookingAccess(authenticationUser, null));
    }

    @DisplayName("Should grant access to booking when user is super user")
    @Test
    void hasBookingAccessSuperUser() {
        when(authenticationUser.isAdmin()).thenReturn(true);

        assertTrue(authorisationService.hasBookingAccess(authenticationUser, UUID.randomUUID()));
    }

    @DisplayName("Should grant access to booking when booking does not exist")
    @Test
    void hasBookingAccessBookingNotFound() {
        var id = UUID.randomUUID();
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(bookingRepository.existsById(id)).thenReturn(false);

        assertTrue(authorisationService.hasBookingAccess(authenticationUser, id));
    }

    @DisplayName("Should grant access to booking when booking is shared with the user (when user is portal user)")
    @Test
    void hasBookingAccessBookingShared() {
        var booking = new Booking();
        var id = UUID.randomUUID();
        booking.setId(id);
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(true);
        when(bookingRepository.existsById(id)).thenReturn(true);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));
        when(authenticationUser.getSharedBookings()).thenReturn(List.of(id));

        assertTrue(authorisationService.hasBookingAccess(authenticationUser, id));
    }

    @DisplayName("Should grant access to booking when booking is shared with the user (when user is app user)")
    @Test
    void hasBookingAccessBookingAppUser() {
        var court = new Court();
        court.setId(UUID.randomUUID());
        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);
        var booking = new Booking();
        var id = UUID.randomUUID();
        booking.setId(id);
        booking.setCaseId(aCase);

        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(false);
        when(authenticationUser.isAppUser()).thenReturn(true);
        when(authenticationUser.getCourtId()).thenReturn(court.getId());
        when(bookingRepository.existsById(id)).thenReturn(true);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        assertTrue(authorisationService.hasBookingAccess(authenticationUser, id));
    }

    @DisplayName("Should not grant access to booking when booking not shared with the user (when user is portal user)")
    @Test
    void hasBookingAccessNotShared() {
        var booking = new Booking();
        var id = UUID.randomUUID();
        booking.setId(id);
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(true);
        when(bookingRepository.existsById(id)).thenReturn(true);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));
        when(authenticationUser.getSharedBookings()).thenReturn(List.of(UUID.randomUUID()));

        assertFalse(authorisationService.hasBookingAccess(authenticationUser, id));
    }

    @DisplayName("Should grant access to court when courtId is null")
    @Test
    void hasCourtAccessIdNull() {
        assertTrue(authorisationService.hasCourtAccess(authenticationUser, null));
    }

    @DisplayName("Should grant access to court when user is super user")
    @Test
    void hasCourtAccessSuperUser() {
        when(authenticationUser.isAdmin()).thenReturn(true);

        assertTrue(authorisationService.hasCourtAccess(authenticationUser, UUID.randomUUID()));
    }

    @DisplayName("Should grant access to court when courtId matches user's courtId")
    @Test
    void hasCourtAccessMatchingCourtId() {
        UUID courtId = UUID.randomUUID();
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.getCourtId()).thenReturn(courtId);

        assertTrue(authorisationService.hasCourtAccess(authenticationUser, courtId));
    }

    @DisplayName("Should not grant access to court when courtId does not match user's courtId")
    @Test
    void hasCourtAccessNotMatchingCourtId() {
        UUID userCourtId = UUID.randomUUID();
        UUID otherCourtId = UUID.randomUUID();
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.getCourtId()).thenReturn(userCourtId);

        assertFalse(authorisationService.hasCourtAccess(authenticationUser, otherCourtId));
    }

    @DisplayName("Should grant access to participant when participantId is null")
    @Test
    void hasParticipantAccessIdNull() {
        assertTrue(authorisationService.hasParticipantAccess(authenticationUser, null));
    }

    @DisplayName("Should grant access to participant when user is super user")
    @Test
    void hasParticipantAccessSuperUser() {
        when(authenticationUser.isAdmin()).thenReturn(true);

        assertTrue(authorisationService.hasParticipantAccess(authenticationUser, UUID.randomUUID()));
    }

    @DisplayName("Should grant access to participant when participant does not exist")
    @Test
    void hasParticipantAccessNotFound() {
        UUID participantId = UUID.randomUUID();
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(participantRepository.findById(participantId)).thenReturn(java.util.Optional.empty());

        assertTrue(authorisationService.hasParticipantAccess(authenticationUser, participantId));
    }

    @DisplayName("Should grant access to case when caseId is null")
    @Test
    void hasCaseAccessIdNull() {
        assertTrue(authorisationService.hasCaseAccess(authenticationUser, null));
    }

    @DisplayName("Should grant access to case when user is super user")
    @Test
    void hasCaseAccessSuperUser() {
        when(authenticationUser.isAdmin()).thenReturn(true);

        assertTrue(authorisationService.hasCaseAccess(authenticationUser, UUID.randomUUID()));
    }

    @DisplayName("Should grant access to case when case does not exist")
    @Test
    void hasCaseAccessNotFound() {
        UUID caseId = UUID.randomUUID();
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertTrue(authorisationService.hasCaseAccess(authenticationUser, caseId));
    }

    @DisplayName("Should grant access to case when user's courtId matches case's courtId")
    @Test
    void hasCaseAccessMatchingCourtId() {
        UUID userCourtId = UUID.randomUUID();
        var court = new Court();
        court.setId(userCourtId);
        final var caseEntity = new Case();
        caseEntity.setCourt(court);
        UUID caseId = UUID.randomUUID();
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.getCourtId()).thenReturn(userCourtId);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));

        assertTrue(authorisationService.hasCaseAccess(authenticationUser, caseId));
    }

    @DisplayName("Should not grant access to case when user's courtId does not match case's courtId")
    @Test
    void hasCaseAccessNotMatchingCourtId() {
        UUID otherCourtId = UUID.randomUUID();
        var court = new Court();
        court.setId(otherCourtId);
        final var caseEntity = new Case();
        caseEntity.setCourt(court);
        UUID caseId = UUID.randomUUID();
        UUID userCourtId = UUID.randomUUID();
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.getCourtId()).thenReturn(userCourtId);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));

        assertFalse(authorisationService.hasCaseAccess(authenticationUser, caseId));
    }

    @DisplayName("Should grant access to booking creation when user has access to all entities specified in dto")
    @Test
    void hasUpsertAccessBooking() {
        when(authenticationUser.isAdmin()).thenReturn(true);

        var dto = new CreateBookingDTO();
        dto.setId(UUID.randomUUID());
        dto.setCaseId(UUID.randomUUID());
        dto.setParticipants(Set.of());

        assertTrue(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should grant access to capture session when capture session id is null")
    @Test
    void hasCaptureSessionAccessIdNull() {
        assertTrue(authorisationService.hasCaptureSessionAccess(authenticationUser, null));
    }

    @DisplayName("Should grant access to capture session when user is super user")
    @Test
    void hasCaptureSessionAccessSuperUser() {
        when(authenticationUser.isAdmin()).thenReturn(true);

        assertTrue(authorisationService.hasCaptureSessionAccess(authenticationUser, UUID.randomUUID()));
    }

    @DisplayName("Should grant access to capture session when capture session does not exist")
    @Test
    void hasCaptureSessionAccessSessionNotFound() {
        var id = UUID.randomUUID();
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(captureSessionRepository.findById(id)).thenReturn(Optional.empty());

        assertTrue(authorisationService.hasCaptureSessionAccess(authenticationUser, id));
    }

    @DisplayName("Should grant access to capture session when booking access is granted")
    @Test
    void hasCaptureSessionAccessBookingAccessGranted() {
        var captureSessionId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var captureSession = new CaptureSession();
        captureSession.setId(captureSessionId);
        var booking = new Booking();
        booking.setId(bookingId);
        captureSession.setBooking(booking);

        when(authenticationUser.isAdmin()).thenReturn(false);
        when(captureSessionRepository.findById(captureSessionId)).thenReturn(Optional.of(captureSession));
        when(bookingRepository.existsById(bookingId)).thenReturn(false);

        assertTrue(authorisationService.hasCaptureSessionAccess(authenticationUser, captureSessionId));
    }

    @DisplayName("Should not grant access to capture session when booking access is not granted")
    @Test
    void hasCaptureSessionAccessBookingAccessNotGranted() {
        var captureSessionId = UUID.randomUUID();
        var captureSession = new CaptureSession();
        captureSession.setId(captureSessionId);
        var bookingId = UUID.randomUUID();
        var booking = new Booking();
        booking.setId(bookingId);
        captureSession.setBooking(booking);

        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(true);
        when(authenticationUser.getSharedBookings()).thenReturn(List.of());
        when(captureSessionRepository.findById(captureSessionId)).thenReturn(Optional.of(captureSession));
        when(bookingRepository.existsById(bookingId)).thenReturn(true);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertFalse(authorisationService.hasCaptureSessionAccess(authenticationUser, captureSessionId));
    }

    @DisplayName("Should grant access to recording when recording id is null")
    @Test
    void hasRecordingAccessIdNull() {
        assertTrue(authorisationService.hasRecordingAccess(authenticationUser, null));
    }

    @DisplayName("Should grant access to recording when user is super user")
    @Test
    void hasRecordingAccessSuperUser() {
        when(authenticationUser.isAdmin()).thenReturn(true);

        assertTrue(authorisationService.hasRecordingAccess(authenticationUser, UUID.randomUUID()));
    }

    @DisplayName("Should grant access to recording when recording does not exist")
    @Test
    void hasRecordingAccessRecordingNotFound() {
        var id = UUID.randomUUID();
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(recordingRepository.findById(id)).thenReturn(Optional.empty());

        assertTrue(authorisationService.hasRecordingAccess(authenticationUser, id));
    }

    @DisplayName("Should grant access to recording when capture session access is granted")
    @Test
    void hasRecordingAccessCaptureSessionAccessGranted() {
        var recordingId = UUID.randomUUID();
        var recording = new Recording();
        recording.setId(recordingId);
        var captureSession = new CaptureSession();
        recording.setCaptureSession(captureSession);

        when(authenticationUser.isAdmin()).thenReturn(false);
        when(recordingRepository.findById(recordingId)).thenReturn(Optional.of(recording));

        assertTrue(authorisationService.hasRecordingAccess(authenticationUser, recordingId));
    }

    @DisplayName("Should not grant access to recording when capture session access is not granted")
    @Test
    void hasRecordingAccessCaptureSessionAccessNotGranted() {
        var recordingId = UUID.randomUUID();
        var captureSessionId = UUID.randomUUID();
        var recording = new Recording();
        recording.setId(recordingId);
        var captureSession = new CaptureSession();
        captureSession.setId(captureSessionId);
        var bookingId = UUID.randomUUID();
        var booking = new Booking();
        booking.setId(bookingId);
        captureSession.setBooking(booking);
        recording.setCaptureSession(captureSession);

        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(true);
        when(authenticationUser.getSharedBookings()).thenReturn(List.of());
        when(recordingRepository.findById(recordingId)).thenReturn(Optional.of(recording));
        when(captureSessionRepository.findById(captureSessionId)).thenReturn(Optional.of(captureSession));
        when(bookingRepository.existsById(bookingId)).thenReturn(true);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertFalse(authorisationService.hasRecordingAccess(authenticationUser, recordingId));
    }

}
