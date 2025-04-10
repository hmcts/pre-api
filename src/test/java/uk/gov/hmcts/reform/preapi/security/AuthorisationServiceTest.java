package uk.gov.hmcts.reform.preapi.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
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

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private CaseRepository caseRepository;

    @MockitoBean
    private ParticipantRepository participantRepository;

    @MockitoBean
    private CaptureSessionRepository captureSessionRepository;

    @MockitoBean
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

    @DisplayName("Should grant upsert access when capture session access and booking access are granted")
    @Test
    void hasUpsertAccessCaptureSessionDTO() {
        var dto = new CreateCaptureSessionDTO();
        dto.setId(null);
        dto.setBookingId(null);

        assertTrue(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }


    @DisplayName("Should grant upsert access when capture session access and recording access are granted")
    @Test
    void hasUpsertAccessRecordingDTO() {
        var dto = new CreateRecordingDTO();
        dto.setParentRecordingId(UUID.randomUUID());
        dto.setCaptureSessionId(UUID.randomUUID());

        assertTrue(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should not grant upsert access when capture session access is not granted")
    @Test
    void hasUpsertAccessRecordingDTOAccessNotGranted() {
        var dto = new CreateRecordingDTO();
        dto.setParentRecordingId(null);
        dto.setCaptureSessionId(UUID.randomUUID());
        var court = new Court();
        court.setId(UUID.randomUUID());
        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);

        var captureSession = new CaptureSession();
        captureSession.setId(dto.getCaptureSessionId());
        captureSession.setBooking(booking);

        when(captureSessionRepository.findById(captureSession.getId())).thenReturn(Optional.of(captureSession));
        when(bookingRepository.existsById(booking.getId())).thenReturn(true);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(authenticationUser.isAppUser()).thenReturn(true);
        when(authenticationUser.getCourtId()).thenReturn(UUID.randomUUID());
        when(authenticationUser.isPortalUser()).thenReturn(false);

        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should not grant upsert access when recording access is not granted")
    @Test
    void hasUpsertAccessCaptureSessionDTORecordingAccessNotGranted() {
        var dto = new CreateRecordingDTO();
        dto.setParentRecordingId(UUID.randomUUID());
        dto.setCaptureSessionId(UUID.randomUUID());
        var court = new Court();
        court.setId(UUID.randomUUID());
        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);

        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);

        var parentRecording = new Recording();
        parentRecording.setId(dto.getParentRecordingId());
        parentRecording.setCaptureSession(captureSession);


        when(captureSessionRepository.findById(dto.getCaptureSessionId())).thenReturn(Optional.empty());
        when(captureSessionRepository.findById(captureSession.getId())).thenReturn(Optional.of(captureSession));
        when(recordingRepository.findById(dto.getParentRecordingId())).thenReturn(Optional.of(parentRecording));
        when(bookingRepository.existsById(booking.getId())).thenReturn(true);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(authenticationUser.isAppUser()).thenReturn(true);
        when(authenticationUser.getCourtId()).thenReturn(UUID.randomUUID());
        when(authenticationUser.isPortalUser()).thenReturn(false);


        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should grant upsert access for all participants when each participant has upsert access")
    @Test
    void hasUpsertAccessForAllParticipants() {
        var participant1 = new CreateParticipantDTO();
        participant1.setId(UUID.randomUUID());
        var participant2 = new CreateParticipantDTO();
        participant2.setId(UUID.randomUUID());
        var participant3 = new CreateParticipantDTO();
        participant3.setId(UUID.randomUUID());

        when(authenticationUser.isAdmin()).thenReturn(true);

        var participants = Set.of(
            participant1,
            participant2,
            participant3
        );


        assertTrue(authorisationService.hasUpsertAccess(authenticationUser, participants));
    }

    @DisplayName("Should not grant upsert access for any participant when at least one participant lacks upsert access")
    @Test
    void hasUpsertAccessForAnyParticipantAccessNotGranted() {
        var participant1 = new CreateParticipantDTO();
        participant1.setId(UUID.randomUUID());
        var participant2 = new CreateParticipantDTO();
        participant2.setId(UUID.randomUUID());
        var participant3 = new CreateParticipantDTO();
        participant3.setId(UUID.randomUUID());

        var court = new Court();
        court.setId(UUID.randomUUID());

        var court2 = new Court();
        court2.setId(UUID.randomUUID());

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);

        var aCase2 = new Case();
        aCase2.setId(UUID.randomUUID());
        aCase2.setCourt(court2);

        var entity = new Participant();
        entity.setCaseId(aCase);

        var entity2 = new Participant();
        entity2.setCaseId(aCase2);

        when(participantRepository.findById(participant1.getId())).thenReturn(Optional.of(entity));
        when(participantRepository.findById(participant2.getId())).thenReturn(Optional.of(entity2));
        when(caseRepository.findById(aCase.getId())).thenReturn(Optional.of(aCase));
        when(caseRepository.findById(aCase2.getId())).thenReturn(Optional.of(aCase2));
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(false);
        when(authenticationUser.getCourtId()).thenReturn(court.getId());

        var participants = Set.of(
            participant1,
            participant2,
            participant3
        );

        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, participants));
    }

    @DisplayName("Should grant upsert access for an empty set of participants")
    @Test
    void hasUpsertAccessEmptyParticipantSet() {
        assertTrue(authorisationService.hasUpsertAccess(authenticationUser, Set.of()));
    }

    @DisplayName("Should grant upsert access when case access, court access, and participant access are all granted")
    @Test
    void hasUpsertAccessAllAccessGranted() {
        var dto = new CreateCaseDTO();
        dto.setId(UUID.randomUUID());
        dto.setCourtId(UUID.randomUUID());
        var participant = new CreateParticipantDTO();
        participant.setId(UUID.randomUUID());
        dto.setParticipants(Set.of(participant));

        when(authenticationUser.isAdmin()).thenReturn(true);

        assertTrue(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should not grant upsert access when case access is not granted")
    @Test
    void hasUpsertAccessCaseAccessNotGranted() {
        var court = new Court();
        court.setId(UUID.randomUUID());

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);

        var dto = new CreateCaseDTO();
        dto.setId(aCase.getId());
        dto.setCourtId(court.getId());
        var participant = new CreateParticipantDTO();
        participant.setId(UUID.randomUUID());
        dto.setParticipants(Set.of(participant));

        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(false);
        when(authenticationUser.getCourtId()).thenReturn(UUID.randomUUID());
        when(caseRepository.findById(dto.getId())).thenReturn(Optional.of(aCase));

        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should grant upsert access when case attempting to update a case status as correct role")
    @Test
    void hasUpsertAccessCaseAccessGrantedForUpdateStatus() {
        var court = new Court();
        court.setId(UUID.randomUUID());

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);
        aCase.setState(CaseState.OPEN);

        var dto = new CreateCaseDTO();
        dto.setId(aCase.getId());
        dto.setCourtId(court.getId());
        dto.setState(CaseState.PENDING_CLOSURE);
        var participant = new CreateParticipantDTO();
        participant.setId(UUID.randomUUID());
        dto.setParticipants(Set.of(participant));

        when(authenticationUser.isAdmin()).thenReturn(true);
        when(authenticationUser.isPortalUser()).thenReturn(false);
        when(authenticationUser.getCourtId()).thenReturn(UUID.randomUUID());
        when(caseRepository.findById(dto.getId())).thenReturn(Optional.of(aCase));

        assertTrue(authorisationService.hasUpsertAccess(authenticationUser, dto));

        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_LEVEL_2")));
        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should not grant upsert access when case attempting to update a case status as invalid user")
    @Test
    void hasUpsertAccessCaseAccessNotGrantedForUpdateStatus() {
        var court = new Court();
        court.setId(UUID.randomUUID());

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);
        aCase.setState(CaseState.OPEN);

        var dto = new CreateCaseDTO();
        dto.setId(aCase.getId());
        dto.setCourtId(court.getId());
        dto.setState(CaseState.PENDING_CLOSURE);
        var participant = new CreateParticipantDTO();
        participant.setId(UUID.randomUUID());
        dto.setParticipants(Set.of(participant));

        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(false);
        when(authenticationUser.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_LEVEL_3")));
        when(authenticationUser.getCourtId()).thenReturn(UUID.randomUUID());
        when(caseRepository.findById(dto.getId())).thenReturn(Optional.of(aCase));

        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, dto));

        when(authenticationUser.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_LEVEL_4")));
        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should not grant upsert access when court access is not granted")
    @Test
    void hasUpsertAccessCourtAccessNotGranted() {
        var dto = new CreateCaseDTO();
        dto.setId(UUID.randomUUID());
        dto.setCourtId(UUID.randomUUID());
        var participant = new CreateParticipantDTO();
        participant.setId(UUID.randomUUID());
        dto.setParticipants(Set.of(participant));

        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(false);
        when(authenticationUser.getCourtId()).thenReturn(UUID.randomUUID());

        when(caseRepository.findById(dto.getId())).thenReturn(Optional.empty());

        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should grant upsert access when the user is the one sharing the booking and has booking access")
    @Test
    void hasUpsertAccessUserIsSharingAndHasBookingAccess() {
        var dto = new CreateShareBookingDTO();
        var userId = UUID.randomUUID();

        dto.setSharedByUser(userId);
        dto.setBookingId(UUID.randomUUID());

        when(authenticationUser.getUserId()).thenReturn(userId);
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(bookingRepository.existsById(dto.getBookingId())).thenReturn(false);

        assertTrue(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should not grant upsert access when the authenticated user is not the one sharing the booking")
    @Test
    void hasUpsertAccessUserIsNotSharing() {
        var dto = new CreateShareBookingDTO();

        dto.setSharedByUser(UUID.randomUUID());
        dto.setBookingId(UUID.randomUUID());

        when(authenticationUser.getUserId()).thenReturn(UUID.randomUUID());

        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should grant upsert access when the authenticated user is an admin")
    @Test
    void hasUpsertAccessUserIsAdmin() {
        var dto = new CreateShareBookingDTO();
        var userId = UUID.randomUUID();

        dto.setSharedByUser(userId);
        dto.setBookingId(UUID.randomUUID());

        when(authenticationUser.getUserId()).thenReturn(userId);
        when(authenticationUser.isAdmin()).thenReturn(true);

        assertTrue(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @DisplayName("Should not grant upsert access when the user is not admin and does not have booking access")
    @Test
    void hasUpsertAccessUserIsNotAdminAndNoBookingAccess() {
        var dto = new CreateShareBookingDTO();
        var userId = UUID.randomUUID();

        dto.setSharedByUser(userId);
        dto.setBookingId(UUID.randomUUID());

        var booking = new Booking();
        booking.setId(dto.getBookingId());

        when(authenticationUser.getUserId()).thenReturn(userId);
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.isAppUser()).thenReturn(false);
        when(authenticationUser.isPortalUser()).thenReturn(true);
        when(authenticationUser.getSharedBookings()).thenReturn(List.of(UUID.randomUUID()));
        when(bookingRepository.existsById(booking.getId())).thenReturn(true);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertFalse(authorisationService.hasUpsertAccess(authenticationUser, dto));
    }

    @Test
    @DisplayName("Should grant access when caseOpen param is null")
    void canSearchByCaseClosedCaseOpenNull() {
        assertTrue(authorisationService.canSearchByCaseClosed(authenticationUser, null));
    }

    @Test
    @DisplayName("Should grant access when caseOpen param is true")
    void canSearchByCaseClosedCaseOpenTrue() {
        assertTrue(authorisationService.canSearchByCaseClosed(authenticationUser, true));
    }

    @Test
    @DisplayName("Should grant access when caseOpen param is false and user is admin")
    void canSearchByCaseClosedCaseOpenFalseIsAdmin() {
        when(authenticationUser.isAdmin()).thenReturn(true);
        assertTrue(authorisationService.canSearchByCaseClosed(authenticationUser, false));
    }

    @Test
    @DisplayName("Should grant access when caseOpen param is false and user is level 2")
    void canSearchByCaseClosedCaseOpenFalseIsLevel2() {
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_LEVEL_2")));
        assertTrue(authorisationService.canSearchByCaseClosed(authenticationUser, false));
    }

    @Test
    @DisplayName("Should not grant access when caseOpen param is false and user is not admin or level 2")
    void canSearchByCaseClosedCaseOpenFalseIsNotAdminOrLevel2() {
        when(authenticationUser.isAdmin()).thenReturn(false);
        when(authenticationUser.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_LEVEL_3")));
        assertFalse(authorisationService.canSearchByCaseClosed(authenticationUser, false));
    }
}
