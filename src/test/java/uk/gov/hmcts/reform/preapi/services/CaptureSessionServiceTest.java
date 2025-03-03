package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.*;
import uk.gov.hmcts.reform.preapi.enums.*;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
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
            Timestamp.from(Instant.now().plus(Duration.ofDays(1))),
            null
        );
        booking.setId(UUID.randomUUID());

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
        captureSession.setStatus(RecordingStatus.NO_RECORDING);
        when(captureSessionRepository.findAllByBookingAndDeletedAtIsNull(booking)).thenReturn(List.of(captureSession));

        captureSessionService.deleteCascade(booking);

        verify(captureSessionRepository, times(1)).findAllByBookingAndDeletedAtIsNull(booking);
        verify(recordingService, times(1)).deleteCascade(captureSession);
        verify(captureSessionRepository, times(1)).save(captureSession);
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
        captureSession.setStatus(RecordingStatus.NO_RECORDING);
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));

        captureSessionService.deleteById(captureSession.getId());

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(captureSession.getId());
        verify(recordingService, times(1)).deleteCascade(captureSession);
        verify(captureSessionRepository, times(1)).saveAndFlush(captureSession);
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

    @DisplayName("Create/update a capture session associated case is not open")
    @Test
    void createCaptureSessionBookingCaseNotOpen() {
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

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setState(CaseState.CLOSED);
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);

        when(captureSessionRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(bookingRepository.findByIdAndDeletedAtIsNull(model.getBookingId())).thenReturn(Optional.of(booking));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> captureSessionService.upsert(model)
        ).getMessage();

        assertThat(message).isEqualTo(
            "Resource CaptureSession(" + model.getId()
                + ") is associated with a case in the state CLOSED. Must be in state OPEN."
        );
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

    @DisplayName("Should throw not found when capture session cannot be found when starting capture session")
    @Test
    void startCaptureSessionNotFound() {
        var captureSessionId = UUID.randomUUID();

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSessionId)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.startCaptureSession(captureSessionId, RecordingStatus.FAILURE, null)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Capture Session: " + captureSessionId);

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(captureSessionId);
        verify(captureSessionRepository, never()).save(any());
    }

    @DisplayName("Should set status and ingest address for capture session on start live event")
    @Test
    void startCaptureSessionOnStart() {
        captureSession.setStatus(null);
        captureSession.setIngestAddress(null);
        captureSession.setStartedAt(null);
        captureSession.setStartedByUser(null);
        var mockAuth = mock(UserAuthentication.class);
        var mockAppAccess = mock(AppAccess.class);

        when(mockAuth.getAppAccess()).thenReturn(mockAppAccess);
        when(mockAppAccess.getUser()).thenReturn(user);

        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var model = captureSessionService.startCaptureSession(
            captureSession.getId(),
            RecordingStatus.STANDBY,
            "example ingest"
        );
        assertThat(model.getStartedAt()).isNotNull();
        assertThat(model.getStartedByUserId()).isEqualTo(user.getId());
        assertThat(model.getStatus()).isEqualTo(RecordingStatus.STANDBY);
        assertThat(model.getIngestAddress()).isEqualTo("example ingest");

        verify(captureSessionRepository, times(1)).save(any());
    }

    @DisplayName("Should update capture session when recording is PROCESSING")
    @Test
    void stopCaptureSessionProcessing() {
        captureSession.setStatus(RecordingStatus.STANDBY);
        captureSession.setFinishedAt(null);
        captureSession.setFinishedByUser(null);
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.getUserId()).thenReturn(user.getId());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var model = captureSessionService.stopCaptureSession(
            captureSession.getId(),
            RecordingStatus.PROCESSING,
            UUID.randomUUID()
        );

        assertThat(model.getId()).isEqualTo(captureSession.getId());
        assertThat(model.getStatus()).isEqualTo(RecordingStatus.PROCESSING);
        assertThat(model.getFinishedByUserId()).isEqualTo(user.getId());
        assertThat(model.getFinishedAt()).isNotNull();

        verify(recordingService, never()).upsert(any());
        verify(captureSessionRepository, times(1)).saveAndFlush(any());
    }

    @DisplayName("Should update capture session when status is RECORDING_AVAILABLE MK")
    @Test
    void stopCaptureSessionRecordingAvailableMk() {
        captureSession.setStatus(RecordingStatus.STANDBY);
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.getUserId()).thenReturn(user.getId());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var recordingId = UUID.randomUUID();
        var captureSessionServiceMk = new CaptureSessionService(recordingService,
                                                                captureSessionRepository,
                                                                bookingRepository,
                                                                userRepository,
                                                                bookingService);

        var model = captureSessionServiceMk.stopCaptureSession(
            captureSession.getId(),
            RecordingStatus.RECORDING_AVAILABLE,
            recordingId
        );

        var createRecordingDTOArgument = ArgumentCaptor.forClass(CreateRecordingDTO.class);

        assertThat(model.getId()).isEqualTo(captureSession.getId());
        assertThat(model.getStatus()).isEqualTo(RecordingStatus.RECORDING_AVAILABLE);

        verify(recordingService, times(1)).upsert(createRecordingDTOArgument.capture());
        assertThat(createRecordingDTOArgument.getValue().getFilename()).isEqualTo("index_1280x720_4500k.mp4");
        verify(captureSessionRepository, times(1)).saveAndFlush(any());
    }

    @DisplayName("Should update capture session when status is NO_RECORDING")
    @Test
    void stopCaptureSessionNoRecording() {
        captureSession.setStatus(RecordingStatus.STANDBY);
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.getUserId()).thenReturn(user.getId());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var recordingId = UUID.randomUUID();
        var model = captureSessionService.stopCaptureSession(
            captureSession.getId(),
            RecordingStatus.NO_RECORDING,
            recordingId
        );

        assertThat(model.getId()).isEqualTo(captureSession.getId());
        assertThat(model.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);

        verify(recordingService, never()).upsert(any());
        verify(captureSessionRepository, times(1)).saveAndFlush(any());
    }

    @DisplayName("Should throw not found error when capture session does not exist")
    @Test
    void stopCaptureSessionNotFound() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.getUserId()).thenReturn(user.getId());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.empty());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.stopCaptureSession(captureSession.getId(), RecordingStatus.PROCESSING,
                                                           UUID.randomUUID())
        );
        assertThat(message).hasMessageContaining("Not found: Capture Session: " + captureSession.getId());
    }

    @DisplayName("Should update capture session status and return the capture session")
    @Test
    void setCaptureSessionStatus() {
        captureSession.setStatus(RecordingStatus.STANDBY);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));

        var model = captureSessionService.setCaptureSessionStatus(captureSession.getId(), RecordingStatus.RECORDING);
        assertThat(model.getId()).isEqualTo(captureSession.getId());
        assertThat(model.getStatus()).isEqualTo(RecordingStatus.RECORDING);

        verify(captureSessionRepository, times(1)).save(any());
    }

    @DisplayName("Should throw not found when capture session does not exist")
    @Test
    void setCaptureSessionStatusNotFound() {
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.setCaptureSessionStatus(captureSession.getId(), RecordingStatus.RECORDING)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Capture Session: " + captureSession.getId());
    }

    @DisplayName("Should search for a capture session using a live event id")
    @Test
    void findCaptureSessionByLiveEventId() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.getUserId()).thenReturn(user.getId());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var captureSessionId = UUID.randomUUID();
        var liveEventId = captureSessionId.toString().replace("-", "");

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSessionId))
            .thenReturn(Optional.empty());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.findByLiveEventId(liveEventId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: CaptureSession: " + liveEventId);
    }

    @Test
    @DisplayName("Should throw not found when live event name cannot be turned into a UUID")
    void findCaptureSessionByLiveEventIdNotFoundUnparseable() {
        var liveEventId = "this-is-a-test";

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.findByLiveEventId(liveEventId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: CaptureSession: " + liveEventId);
    }

    @Test
    @DisplayName("Should throw resource wrong stage exception when attempting to delete capture session in wrong state")
    void deleteCaptureSessionWrongState() {
        captureSession.setStatus(RecordingStatus.STANDBY);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> captureSessionService.deleteById(captureSession.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo(
                "Capture Session ("
                    + captureSession.getId()
                    + ") must be in state RECORDING_AVAILABLE or NO_RECORDING to be deleted. Current state is STANDBY");

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(captureSession.getId());
        verify(captureSessionRepository, never()).deleteById(captureSession.getId());
    }

    @Test
    @DisplayName("Should throw resource wrong stage exception when attempting to delete capture session in wrong state")
    void deleteCascadeCaptureSessionWrongState() {
        captureSession.setStatus(RecordingStatus.STANDBY);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(captureSessionRepository.findAllByBookingAndDeletedAtIsNull(booking)).thenReturn(List.of(captureSession));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> captureSessionService.deleteCascade(booking)
        ).getMessage();

        assertThat(message)
            .isEqualTo(
                "Capture Session ("
                    + captureSession.getId()
                    + ") must be in state RECORDING_AVAILABLE or NO_RECORDING to be deleted. Current state is STANDBY");

        verify(captureSessionRepository, times(1)).findAllByBookingAndDeletedAtIsNull(booking);
        verify(captureSessionRepository, never()).deleteById(captureSession.getId());
    }
}
