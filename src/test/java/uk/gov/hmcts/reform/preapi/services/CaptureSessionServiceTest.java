package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CaptureSessionService.class)
public class CaptureSessionServiceTest {
    @MockBean
    private RecordingService recordingService;

    @MockBean
    private CaptureSessionRepository captureSessionRepository;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private BookingService bookingService;

    @Autowired
    private CaptureSessionService captureSessionService;

    private static CaptureSession captureSession;
    private static Booking booking;
    private static User user;

    @BeforeAll
    static void setUp() {
        booking = HelperFactory.createBooking(
            HelperFactory.createCase(
                HelperFactory.createCourt(CourtType.CROWN, "Foo Court", null),
                "12345678",
                false,
                null
            ),
            Timestamp.from(java.time.Instant.now().plus(java.time.Duration.ofDays(1))),
            null
        );

        user = new User();
        user.setId(UUID.randomUUID());

        captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setOrigin(RecordingOrigin.PRE);
        captureSession.setBooking(booking);
        captureSession.setIngestAddress("example ingest address");
        captureSession.setLiveOutputUrl("example url");
        captureSession.setStartedAt(Timestamp.from(Instant.now()));
        captureSession.setStartedByUser(user);
        captureSession.setFinishedAt(Timestamp.from(Instant.now()));
        captureSession.setFinishedByUser(user);
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);
    }

    @DisplayName("Find a capture session and return a model")
    @Test
    void findByIdSuccess() {
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));

        var model = captureSessionService.findById(captureSession.getId());

        assertThat(model.getId()).isEqualTo(captureSession.getId());
        assertThat(model.getBookingId()).isEqualTo(booking.getId());
        assertThat(model.getOrigin()).isEqualTo(captureSession.getOrigin());
        assertThat(model.getIngestAddress()).isEqualTo(captureSession.getIngestAddress());
        assertThat(model.getLiveOutputUrl()).isEqualTo(captureSession.getLiveOutputUrl());
        assertThat(model.getStartedAt()).isEqualTo(captureSession.getStartedAt());
        assertThat(model.getStartedByUserId()).isEqualTo(user.getId());
        assertThat(model.getFinishedAt()).isEqualTo(captureSession.getFinishedAt());
        assertThat(model.getFinishedByUserId()).isEqualTo(user.getId());
        assertThat(model.getStatus()).isEqualTo(captureSession.getStatus());
        assertThat(model.getDeletedAt()).isEqualTo(captureSession.getDeletedAt());
    }

    @DisplayName("Find a capture session when capture session does not exist")
    @Test
    void findByIdNotFound() {
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.findById(captureSession.getId())
        ).getMessage();

        assertThat(message).isEqualTo("Not found: CaptureSession: " + captureSession.getId());
    }

    @DisplayName("Should delete all attached recordings before marking capture session as deleted")
    @Test
    void deleteCascadeSuccess() {
        when(captureSessionRepository.findAllByBookingAndDeletedAtIsNull(booking)).thenReturn(List.of(captureSession));

        captureSessionService.deleteCascade(booking);

        verify(captureSessionRepository, times(1)).findAllByBookingAndDeletedAtIsNull(booking);
        verify(recordingService, times(1)).deleteCascade(captureSession);
        verify(captureSessionRepository, times(1)).deleteAllByBooking(booking);
    }

    @DisplayName("Find a list of capture sessions and return a list of models")
    @Test
    void searchCaptureSessionsSuccess() {
        when(captureSessionRepository.searchCaptureSessionsBy(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        ).thenReturn(new PageImpl<>(List.of(captureSession)));
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);

        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var modelList = captureSessionService.searchBy(null, null, null, null, Optional.empty(), null,null)
            .getContent();
        assertThat(modelList.size()).isEqualTo(1);
        assertThat(modelList.getFirst().getId()).isEqualTo(captureSession.getId());
    }

    @DisplayName("Find a list of capture sessions and return a list of models when user is non admin")
    @Test
    void searchCaptureSessionsSuccessNonAdmin() {
        var courtId = UUID.randomUUID();
        when(captureSessionRepository.searchCaptureSessionsBy(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        ).thenReturn(new PageImpl<>(List.of(captureSession)));
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.isPortalUser()).thenReturn(false);
        when(mockAuth.getCourtId()).thenReturn(courtId);

        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var modelList = captureSessionService.searchBy(null, null, null, null, Optional.empty(), null,null)
            .getContent();
        assertThat(modelList.size()).isEqualTo(1);
        assertThat(modelList.getFirst().getId()).isEqualTo(captureSession.getId());

        verify(captureSessionRepository, times(1))
            .searchCaptureSessionsBy(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                courtId,
                null
            );
    }

    @DisplayName("Find a list of capture sessions filtered by scheduledFor and return a list of models")
    @Test
    void searchCaptureSessionsScheduledForSuccess() {
        var from = Timestamp.valueOf("2023-01-01 00:00:00");
        var until = Timestamp.valueOf("2023-01-01 23:59:59");
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);

        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        when(captureSessionRepository
                 .searchCaptureSessionsBy(
                     isNull(),
                     isNull(),
                     isNull(),
                     isNull(),
                     isNull(),
                     eq(from),
                     eq(until),
                     isNull(),
                     isNull(),
                     isNull())
        ).thenReturn(new PageImpl<>(List.of(captureSession)));

        var modelList = captureSessionService.searchBy(null, null, null, null, Optional.of(from), null, null)
            .getContent();
        assertThat(modelList.size()).isEqualTo(1);
        assertThat(modelList.getFirst().getId()).isEqualTo(captureSession.getId());
    }

    @DisplayName("Should delete a capture session by id")
    @Test
    void deleteByIdSuccess() {
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));

        captureSessionService.deleteById(captureSession.getId());

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(captureSession.getId());
        verify(recordingService, times(1)).deleteCascade(captureSession);
        verify(captureSessionRepository, times(1)).deleteById(captureSession.getId());
    }

    @DisplayName("Should delete a capture session by id when capture session not found")
    @Test
    void deleteByIdNotFound() {
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.deleteById(captureSession.getId())
        ).getMessage();

        assertThat(message).isEqualTo("Not found: CaptureSession: " + captureSession.getId());

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(captureSession.getId());
        verify(recordingService, never()).deleteCascade(any());
        verify(captureSessionRepository, never()).deleteById(any());
    }

    @DisplayName("Create a capture session")
    @Test
    void createCaptureSessionSuccess() {
        var model = new CreateCaptureSessionDTO();
        model.setId(UUID.randomUUID());
        model.setBookingId(booking.getId());
        model.setOrigin(RecordingOrigin.PRE);
        model.setIngestAddress("example ingest address");
        model.setLiveOutputUrl("url");
        model.setStartedAt(Timestamp.from(Instant.now()));
        model.setStartedByUserId(user.getId());
        model.setFinishedAt(Timestamp.from(Instant.now()));
        model.setFinishedByUserId(user.getId());
        model.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(bookingRepository.findByIdAndDeletedAtIsNull(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));

        assertThat(captureSessionService.upsert(model)).isEqualTo(UpsertResult.CREATED);

        verify(captureSessionRepository, times(1)).findById(model.getId());
        verify(bookingRepository, times(1)).findByIdAndDeletedAtIsNull(booking.getId());
        verify(userRepository, times(2)).findByIdAndDeletedAtIsNull(user.getId());
        verify(captureSessionRepository, times(1)).save(any(CaptureSession.class));
    }

    @DisplayName("Update a capture session")
    @Test
    void updateCaptureSessionSuccess() {
        var model = new CreateCaptureSessionDTO();
        model.setId(UUID.randomUUID());
        model.setBookingId(booking.getId());
        model.setOrigin(RecordingOrigin.PRE);
        model.setIngestAddress("example ingest address");
        model.setLiveOutputUrl("url");
        model.setStartedAt(Timestamp.from(Instant.now()));
        model.setStartedByUserId(user.getId());
        model.setFinishedAt(Timestamp.from(Instant.now()));
        model.setFinishedByUserId(user.getId());
        model.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var entity = new CaptureSession();

        when(captureSessionRepository.findById(model.getId())).thenReturn(Optional.of(entity));
        when(bookingRepository.findByIdAndDeletedAtIsNull(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));

        assertThat(captureSessionService.upsert(model)).isEqualTo(UpsertResult.UPDATED);

        verify(captureSessionRepository, times(1)).findById(model.getId());
        verify(bookingRepository, times(1)).findByIdAndDeletedAtIsNull(booking.getId());
        verify(userRepository, times(2)).findByIdAndDeletedAtIsNull(user.getId());
        verify(captureSessionRepository, times(1)).save(any(CaptureSession.class));
    }

    @DisplayName("Update a capture session when capture session has been deleted")
    @Test
    void updateCaptureSessionDeletedBadRequest() {
        var model = new CreateCaptureSessionDTO();
        model.setId(UUID.randomUUID());
        model.setBookingId(booking.getId());
        model.setOrigin(RecordingOrigin.PRE);
        model.setIngestAddress("example ingest address");
        model.setLiveOutputUrl("url");
        model.setStartedAt(Timestamp.from(Instant.now()));
        model.setStartedByUserId(user.getId());
        model.setFinishedAt(Timestamp.from(Instant.now()));
        model.setFinishedByUserId(user.getId());
        model.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var entity = new CaptureSession();
        entity.setDeletedAt(Timestamp.from(Instant.now()));

        when(captureSessionRepository.findById(model.getId())).thenReturn(Optional.of(entity));

        var message = assertThrows(
            ResourceInDeletedStateException.class,
            () -> captureSessionService.upsert(model)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Resource CaptureSessionDTO("
                           + model.getId()
                           + ") is in a deleted state and cannot be updated"
            );

        verify(captureSessionRepository, times(1)).findById(model.getId());
        verify(captureSessionRepository, never()).save(any(CaptureSession.class));
    }

    @DisplayName("Create/update a capture session when booking not found")
    @Test
    void createCaptureSessionBookingNotFound() {
        var model = new CreateCaptureSessionDTO();
        model.setId(UUID.randomUUID());
        model.setBookingId(UUID.randomUUID());
        model.setOrigin(RecordingOrigin.PRE);
        model.setIngestAddress("example ingest address");
        model.setLiveOutputUrl("url");
        model.setStartedAt(Timestamp.from(Instant.now()));
        model.setStartedByUserId(user.getId());
        model.setFinishedAt(Timestamp.from(Instant.now()));
        model.setFinishedByUserId(user.getId());
        model.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(bookingRepository.findByIdAndDeletedAtIsNull(model.getId())).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.upsert(model)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Not found: Booking: " + model.getBookingId());

        verify(captureSessionRepository, times(1)).findById(model.getId());
        verify(bookingRepository, times(1)).findByIdAndDeletedAtIsNull(model.getBookingId());
        verify(captureSessionRepository, never()).save(any(CaptureSession.class));
    }

    @DisplayName("Create a capture session when started by user is not found")
    @Test
    void createCaptureSessionStartedByNotFound() {
        var model = new CreateCaptureSessionDTO();
        model.setId(UUID.randomUUID());
        model.setBookingId(booking.getId());
        model.setOrigin(RecordingOrigin.PRE);
        model.setIngestAddress("example ingest address");
        model.setLiveOutputUrl("url");
        model.setStartedAt(Timestamp.from(Instant.now()));
        model.setStartedByUserId(UUID.randomUUID());
        model.setFinishedAt(Timestamp.from(Instant.now()));
        model.setFinishedByUserId(user.getId());
        model.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(bookingRepository.findByIdAndDeletedAtIsNull(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByIdAndDeletedAtIsNull(model.getStartedByUserId())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.upsert(model)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Not found: User: " + model.getStartedByUserId());

        verify(captureSessionRepository, times(1)).findById(model.getId());
        verify(bookingRepository, times(1)).findByIdAndDeletedAtIsNull(booking.getId());
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(model.getStartedByUserId());
        verify(captureSessionRepository, never()).save(any(CaptureSession.class));
    }

    @DisplayName("Create a capture session when started by user is not found")
    @Test
    void createCaptureSessionFinishedByNotFound() {
        var model = new CreateCaptureSessionDTO();
        model.setId(UUID.randomUUID());
        model.setBookingId(booking.getId());
        model.setOrigin(RecordingOrigin.PRE);
        model.setIngestAddress("example ingest address");
        model.setLiveOutputUrl("url");
        model.setStartedAt(Timestamp.from(Instant.now()));
        model.setStartedByUserId(user.getId());
        model.setFinishedAt(Timestamp.from(Instant.now()));
        model.setFinishedByUserId(UUID.randomUUID());
        model.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(bookingRepository.findByIdAndDeletedAtIsNull(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByIdAndDeletedAtIsNull(model.getFinishedByUserId())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.upsert(model)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Not found: User: " + model.getFinishedByUserId());

        verify(captureSessionRepository, times(1)).findById(model.getId());
        verify(bookingRepository, times(1)).findByIdAndDeletedAtIsNull(booking.getId());
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(model.getFinishedByUserId());
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(user.getId());
        verify(captureSessionRepository, never()).save(any(CaptureSession.class));
    }

    @DisplayName("Should undelete a capture session successfully when capture session is marked as deleted")
    @Test
    void undeleteSuccess() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setDeletedAt(Timestamp.from(Instant.now()));
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setDeletedAt(Timestamp.from(Instant.now()));
        captureSession.setBooking(booking);

        when(captureSessionRepository.findById(captureSession.getId())).thenReturn(Optional.of(captureSession));

        captureSessionService.undelete(captureSession.getId());

        verify(captureSessionRepository, times(1)).findById(captureSession.getId());
        verify(bookingService, times(1)).undelete(booking.getId());
        verify(captureSessionRepository, times(1)).save(captureSession);
    }

    @DisplayName("Should do nothing when capture session is not deleted")
    @Test
    void undeleteNotDeletedSuccess() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);

        when(captureSessionRepository.findById(captureSession.getId())).thenReturn(Optional.of(captureSession));

        captureSessionService.undelete(captureSession.getId());

        verify(captureSessionRepository, times(1)).findById(captureSession.getId());
        verify(bookingService, times(1)).undelete(booking.getId());
        verify(captureSessionRepository, never()).save(captureSession);
    }

    @DisplayName("Should throw not found exception when capture session cannot be found")
    @Test
    void undeleteNotFound() {
        var captureSessionId = UUID.randomUUID();

        when(captureSessionRepository.findById(captureSessionId)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.undelete(captureSessionId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Capture Session: " + captureSessionId);

        verify(captureSessionRepository, times(1)).findById(captureSessionId);
        verify(captureSessionRepository, never()).save(any());
    }
}
