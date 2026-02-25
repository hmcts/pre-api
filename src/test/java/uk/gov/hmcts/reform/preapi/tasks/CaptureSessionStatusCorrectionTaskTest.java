package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CaptureSessionStatusCorrectionTaskTest {

    private CaptureSessionStatusCorrectionTask captureSessionStatusCorrectionTask;
    private UserService userService;
    private UserAuthenticationService userAuthenticationService;
    private AzureIngestStorageService azureIngestStorageService;
    private CaptureSessionService captureSessionService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);
        azureIngestStorageService = mock(AzureIngestStorageService.class);
        captureSessionService = mock(CaptureSessionService.class);
        captureSessionStatusCorrectionTask = new CaptureSessionStatusCorrectionTask(
            userService,
            userAuthenticationService,
            "robot",
            azureIngestStorageService,
            captureSessionService
        );

        UserDTO userDTO = new UserDTO();
        userDTO.setId(UUID.randomUUID());
        userDTO.setDeletedAt(null);

        AccessDTO accessDTO = new AccessDTO();
        accessDTO.setUser(userDTO);
        BaseAppAccessDTO baseAppAccessDTO = new BaseAppAccessDTO();
        baseAppAccessDTO.setId(UUID.randomUUID());
        accessDTO.setAppAccess(Set.of(baseAppAccessDTO));
        when(userService.findByEmail(anyString())).thenReturn(accessDTO);

        when(userAuthenticationService.validateUser(any()))
            .thenReturn(Optional.of(
                mock(uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication.class)));
    }

    @Test
    void shouldUpdateOnlyUnusedCaptureSessions() {
        Booking booking = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        booking.setId(UUID.fromString("786c62de-a8c9-448d-919c-0038061413d5"));

        Booking booking2 = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        booking2.setId(UUID.fromString("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0"));

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            "ingest",
            "liveOutput",
            Timestamp.from(Instant.now()),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );

        when(captureSessionService.findFailedCaptureSessionsStartedBetween(any(), any())).thenReturn(
            Arrays.asList(captureSession1, captureSession2)
        );
        when(azureIngestStorageService.sectionFileExist("786c62de-a8c9-448d-919c-0038061413d5")).thenReturn(true);
        when(azureIngestStorageService.sectionFileExist("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0")).thenReturn(false);

        captureSessionStatusCorrectionTask.run();

        verify(captureSessionService, never())
            .save(argThat(cs -> cs.getId().equals(captureSession1.getId())));
        verify(captureSessionService, times(1))
            .save(argThat(cs -> {
                assertThat(cs.getId()).isEqualTo(captureSession2.getId());
                assertThat(cs.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);
                return true;
            }));
    }

    @Test
    void shouldDoNothingWhenNoUnusedCaptureSessions() {
        when(captureSessionService.findFailedCaptureSessionsStartedBetween(any(), any())).thenReturn(
            List.of()
        );

        captureSessionStatusCorrectionTask.run();
        verify(captureSessionService, never()).save(any());
    }

    @Test
    void shouldUpdateAllFailedCaptureSessionsIfAllAreUnused() {
        Booking booking = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        booking.setId(UUID.fromString("786c62de-a8c9-448d-919c-0038061413d5"));

        Booking booking2 = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        booking2.setId(UUID.fromString("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0"));

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            "ingest",
            "liveOutput",
            Timestamp.from(Instant.now()),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );

        when(captureSessionService.findFailedCaptureSessionsStartedBetween(any(), any())).thenReturn(
            Arrays.asList(captureSession1, captureSession2)
        );
        when(azureIngestStorageService.sectionFileExist("786c62de-a8c9-448d-919c-0038061413d5")).thenReturn(false);
        when(azureIngestStorageService.sectionFileExist("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0")).thenReturn(false);

        captureSessionStatusCorrectionTask.run();

        ArgumentCaptor<CaptureSession> captor = ArgumentCaptor.forClass(CaptureSession.class);
        verify(captureSessionService, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting("id", "status")
            .containsExactlyInAnyOrder(
                tuple(captureSession1.getId(), RecordingStatus.NO_RECORDING),
                tuple(captureSession2.getId(), RecordingStatus.NO_RECORDING)
            );
    }

    @Test
    void shouldUpdateNoFailedCaptureSessionsIfNoneUnused() {
        Booking booking = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        booking.setId(UUID.fromString("786c62de-a8c9-448d-919c-0038061413d5"));

        Booking booking2 = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        booking2.setId(UUID.fromString("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0"));

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            "ingest",
            "liveOutput",
            Timestamp.from(Instant.now()),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );

        when(captureSessionService.findFailedCaptureSessionsStartedBetween(any(), any())).thenReturn(
            Arrays.asList(captureSession1, captureSession2)
        );
        when(azureIngestStorageService.sectionFileExist("786c62de-a8c9-448d-919c-0038061413d5")).thenReturn(true);
        when(azureIngestStorageService.sectionFileExist("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0")).thenReturn(true);

        captureSessionStatusCorrectionTask.run();

        verify(captureSessionService, never()).save(any());
    }

    @Test
    void shouldHandleSaveThrowingException() {
        Booking booking = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        booking.setId(UUID.fromString("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0"));

        CaptureSession captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );

        when(captureSessionService.findFailedCaptureSessionsStartedBetween(any(), any())).thenReturn(
            List.of(captureSession)
        );
        when(azureIngestStorageService.sectionFileExist("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0")).thenReturn(false);
        when(captureSessionService.save(any())).thenThrow(new RuntimeException("Database error"));

        captureSessionStatusCorrectionTask.run();

        verify(captureSessionService, times(1)).save(any());
    }
}
