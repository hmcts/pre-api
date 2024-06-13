package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AssetsClient;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.LiveEventsClient;
import com.azure.resourcemanager.mediaservices.fluent.LiveOutputsClient;
import com.azure.resourcemanager.mediaservices.fluent.LiveEventsClient;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveOutputInner;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureMediaService.class)
public class AzureMediaServiceTest {
    @MockBean
    private AzureMediaServices amsClient;

    @MockBean
    private CaptureSessionRepository captureSessionRepository;

    @MockBean
    private UserRepository userRepository;

    private final String resourceGroup = "test-resource-group";
    private final String accountName = "test-account-name";
    private final String ingestSa = "test-sa";
    private final String env = "test-env";

    private AzureMediaService mediaService;

    private User user;

    private CaptureSession captureSession;

    @BeforeEach
    void setUp() {
        mediaService = new AzureMediaService(
            resourceGroup,
            accountName,
            ingestSa,
            env,
            amsClient,
            captureSessionRepository,
            userRepository
        );

        user = HelperFactory.createUser(
            "Test",
            "User",
            "example@example.com",
            null,
            null,
            null
        );
        var court = new Court();
        court.setId(UUID.randomUUID());
        court.setName("EXAMPLE COURT");

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setReference("TESTCASE123");
        aCase.setCourt(court);

        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);

        captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
    }

    @DisplayName("Should get a valid asset and return an AssetDTO")
    @Test
    void getAssetSuccess() {
        var name = "test-asset-name";
        var mockAssetsClient = mock(AssetsClient.class);
        var asset = mock(AssetInner.class);
        when(asset.name()).thenReturn(name);
        when(asset.description()).thenReturn("description");
        when(asset.container()).thenReturn("container");
        when(asset.storageAccountName()).thenReturn("storage-account-name");

        when(amsClient.getAssets()).thenReturn(mockAssetsClient);
        when(amsClient.getAssets().get(resourceGroup, accountName, name)).thenReturn(asset);

        var model = mediaService.getAsset(name);
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo(name);
        assertThat(model.getDescription()).isEqualTo("description");
        assertThat(model.getContainer()).isEqualTo("container");
        assertThat(model.getStorageAccountName()).isEqualTo("storage-account-name");
    }

    @DisplayName("Should throw 404 error when azure returns a 404 error")
    @Test
    void getAssetNotFound() {
        var name = "test-asset-name";
        var mockAssetsClient = mock(AssetsClient.class);
        var amsError = mockAmsError(404);
        when(amsClient.getAssets()).thenReturn(mockAssetsClient);
        when(amsClient.getAssets().get(resourceGroup, accountName, name))
            .thenThrow(amsError);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.getAsset(name)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Asset with name: " + name);
    }

    @DisplayName("Should throw any other management exception when not 404 response from Azure")
    @Test
    void getAssetManagementException() {
        var name = "test-asset-name";
        var mockAssetsClient = mock(AssetsClient.class);
        var amsError = mockAmsError(400);

        when(amsClient.getAssets()).thenReturn(mockAssetsClient);
        when(amsClient.getAssets().get(resourceGroup, accountName, name))
            .thenThrow(amsError);

        var message = assertThrows(
            ManagementException.class,
            () -> mediaService.getAsset(name)
        ).getMessage();

        assertThat(message).isEqualTo("error");
    }

    @DisplayName("Should get a list of all assets from Azure")
    @Test
    void getAssetsListSuccess() {
        var asset = mock(AssetInner.class);
        when(asset.name()).thenReturn("name");
        when(asset.description()).thenReturn("description");
        when(asset.container()).thenReturn("container");
        when(asset.storageAccountName()).thenReturn("storage-account-name");

        var mockedClient = mock(AssetsClient.class);
        when(amsClient.getAssets()).thenReturn(mockedClient);
        when(mockedClient.list(resourceGroup, accountName)).thenReturn(mock());
        when(mockedClient.list(resourceGroup, accountName).stream()).thenReturn(Stream.of(asset));

        var models = mediaService.getAssets();
        assertThat(models).isNotNull();
        assertThat(models.size()).isEqualTo(1);
        assertThat(models.getFirst().getName()).isEqualTo("name");
        assertThat(models.getFirst().getDescription()).isEqualTo("description");
        assertThat(models.getFirst().getContainer()).isEqualTo("container");
        assertThat(models.getFirst().getStorageAccountName()).isEqualTo("storage-account-name");
    }

    @DisplayName("Should throw Unsupported Operation Exception when method is not defined")
    @Test
    void unsupportedOperationException() {
        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.playAsset("test-asset-name")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.importAsset("test-asset-name")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.startLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.playLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.stopLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.getLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.getLiveEvents()
        );
    }

    @DisplayName("Should return a valid live event by name")
    @Test
    void getLiveEventByNameSuccess() {
        var name = "test-live-event-name";
        var mockLiveEventClient = mock(LiveEventsClient.class);
        var liveEvent = mock(LiveEventInner.class);
        when(liveEvent.id()).thenReturn("id");
        when(liveEvent.name()).thenReturn(name);
        when(liveEvent.description()).thenReturn("description");
        when(liveEvent.resourceState()).thenReturn(LiveEventResourceState.STOPPED);
        when(liveEvent.input())
            .thenReturn(new LiveEventInput()
                            .withEndpoints(List.of(new LiveEventEndpoint()
                                                       .withProtocol("RTMP")
                                                       .withUrl("rtmps://example"))));
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);
        when(amsClient.getLiveEvents().get(resourceGroup, accountName, name)).thenReturn(liveEvent);

        var model = mediaService.getLiveEvent(name);
        assertThat(model).isNotNull();
        assertThat(model.getId()).isEqualTo(liveEvent.id());
        assertThat(model.getName()).isEqualTo(name);
        assertThat(model.getDescription()).isEqualTo("description");
        assertThat(model.getResourceState()).isEqualTo("Stopped");
        assertThat(model.getInputRtmp()).isEqualTo("rtmps://example");
    }

    @DisplayName("Should throw not found error when AMS returns 404")
    @Test
    void getLiveEventByNameNotFound() {
        var name = "test-live-event-name";
        var httpResponse = mock(HttpResponse.class);
        var mockLiveEventClient = mock(LiveEventsClient.class);
        when(httpResponse.getStatusCode()).thenReturn(404);
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);
        when(amsClient.getLiveEvents().get(resourceGroup, accountName, name))
            .thenThrow(new ManagementException("not found", httpResponse));

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.getLiveEvent(name)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Live event: " + name);
    }

    @DisplayName("Should throw any other management exception when not 404 response from Azure (live event)")
    @Test
    void getLiveEventManagementException() {
        var name = "test-live-event-name";
        var httpResponse = mock(HttpResponse.class);
        var mockClient = mock(LiveEventsClient.class);
        when(httpResponse.getStatusCode()).thenReturn(400);

        when(amsClient.getLiveEvents()).thenReturn(mockClient);
        when(amsClient.getLiveEvents().get(resourceGroup, accountName, name))
            .thenThrow(new ManagementException("bad request", httpResponse));

        var message = assertThrows(
            ManagementException.class,
            () -> mediaService.getLiveEvent(name)
        ).getMessage();

        assertThat(message).isEqualTo("bad request");
    }

    @DisplayName("Should return a list of live events")
    @Test
    void getLiveEventsSuccess() {
        var name = "test-live-event-name";
        var mockLiveEventClient = mock(LiveEventsClient.class);
        var liveEvent = mock(LiveEventInner.class);
        when(liveEvent.id()).thenReturn("id");
        when(liveEvent.name()).thenReturn(name);
        when(liveEvent.description()).thenReturn("description");
        when(liveEvent.resourceState()).thenReturn(LiveEventResourceState.STOPPED);
        when(liveEvent.input())
            .thenReturn(new LiveEventInput()
                            .withEndpoints(List.of(new LiveEventEndpoint()
                                                       .withProtocol("RTMP")
                                                       .withUrl("rtmps://example"))));
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);
        when(mockLiveEventClient.list(resourceGroup, accountName)).thenReturn(mock());
        when(amsClient.getLiveEvents().list(resourceGroup, accountName).stream()).thenReturn(Stream.of(liveEvent));

        var results = mediaService.getLiveEvents();
        assertThat(results).isNotNull();
        assertThat(results).hasSize(1);

        var model = results.getFirst();
        assertThat(model).isNotNull();
        assertThat(model.getId()).isEqualTo(liveEvent.id());
        assertThat(model.getName()).isEqualTo(name);
        assertThat(model.getDescription()).isEqualTo("description");
        assertThat(model.getResourceState()).isEqualTo("Stopped");
        assertThat(model.getInputRtmp()).isEqualTo("rtmps://example");
    }

    @DisplayName("Should return 404 error when starting live event for a capture session that does not exist")
    @Test
    void startLiveEventCaptureSessionNotFound() {
        var id = UUID.randomUUID();
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.startLiveEvent(id)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Capture Session: " + id);
    }

    @DisplayName("Should return 409 error when starting live event that has already finished")
    @Test
    void startLiveEventFinished() {
        captureSession.setStartedAt(Timestamp.from(Instant.now()));
        captureSession.setFinishedAt(Timestamp.from(Instant.now()));
        captureSession.setStartedByUser(user);
        captureSession.setFinishedByUser(user);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));

        var message = assertThrows(
            ConflictException.class,
            () -> mediaService.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Capture Session: " + captureSession.getId() + " has already been finished");
    }

    @DisplayName("Should return the capture session if already started and not finished")
    @Test
    void startLiveEventAlreadyRunning() {
        captureSession.setIngestAddress("valid ingest address");
        captureSession.setStartedAt(Timestamp.from(Instant.now()));
        captureSession.setStartedByUser(user);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));

        var model = mediaService.startLiveEvent(captureSession.getId());
        assertThat(model).isNotNull();
        assertThat(model.getId()).isEqualTo(captureSession.getId());
        assertThat(model.getStartedAt()).isNotNull();
    }

    @DisplayName("Should return the capture session when successfully started the live event")
    @Test
    void startLiveEventSuccess() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        when(mockLiveEvent.resourceState())
            .thenReturn(
                LiveEventResourceState.STARTING,
                LiveEventResourceState.STARTING,
                LiveEventResourceState.RUNNING,
                LiveEventResourceState.RUNNING
            );
        when(mockLiveEvent.input())
            .thenReturn(
                new LiveEventInput()
                    .withEndpoints(List.of(
                        new LiveEventEndpoint()
                            .withProtocol("RTMP")
                            .withUrl("rtmp://some-rtmp-address"),
                        new LiveEventEndpoint()
                            .withProtocol("RTMP")
                            .withUrl("rtmps://some-rtmp-address")
                    ))
            );

        var model = mediaService.startLiveEvent(captureSession.getId());

        assertThat(model).isNotNull();
        assertThat(model.getStatus()).isEqualTo(RecordingStatus.STANDBY);
        assertThat(model.getIngestAddress()).isEqualTo("rtmps://some-rtmp-address");
        assertThat(model.getStartedAt()).isNotNull();
        assertThat(model.getStartedByUserId()).isEqualTo(user.getId());

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(4)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).start(any(), any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should return the capture session when successfully started the live event")
    @Test
    void startLiveEventLiveEventConflictSuccess() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        var amsError = mockAmsError(409);
        when(liveEventClient.create(eq(resourceGroup), eq(accountName), eq(liveEventName), any()))
            .thenThrow(amsError);
        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        when(mockLiveEvent.resourceState())
            .thenReturn(
                LiveEventResourceState.STARTING,
                LiveEventResourceState.STARTING,
                LiveEventResourceState.RUNNING,
                LiveEventResourceState.RUNNING
            );
        when(mockLiveEvent.input())
            .thenReturn(
                new LiveEventInput()
                    .withEndpoints(List.of(
                        new LiveEventEndpoint()
                            .withProtocol("RTMP")
                            .withUrl("rtmp://some-rtmp-address"),
                        new LiveEventEndpoint()
                            .withProtocol("RTMP")
                            .withUrl("rtmps://some-rtmp-address")
                    ))
            );

        var model = mediaService.startLiveEvent(captureSession.getId());

        assertThat(model).isNotNull();
        assertThat(model.getStatus()).isEqualTo(RecordingStatus.STANDBY);
        assertThat(model.getIngestAddress()).isEqualTo("rtmps://some-rtmp-address");
        assertThat(model.getStartedAt()).isNotNull();
        assertThat(model.getStartedByUserId()).isEqualTo(user.getId());

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(4)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).start(any(), any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should throw not found error when live event cannot be found after creation")
    @Test
    void startLiveEventNotFoundAfterCreate() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var liveEventClient = mockLiveEventClient();

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        var amsError = mockAmsError(404);
        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenThrow(amsError);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.startLiveEvent(captureSession.getId())
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Live event: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should throw 409 error when asset already exists")
    @Test
    void startLiveEventAssetConflict() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        var amsError = mockAmsError(409);
        when(assetsClient.createOrUpdate(eq(resourceGroup), eq(accountName), eq(liveEventName), any(AssetInner.class)))
            .thenThrow(amsError);

        var message = assertThrows(
            ConflictException.class,
            () -> mediaService.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Asset: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should throw 409 error when live output already exists")
    @Test
    void startLiveEventLiveOutputConflict() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        var amsError = mockAmsError(409);
        when(liveOutputClient.create(eq(resourceGroup), eq(accountName), eq(liveEventName), eq(liveEventName), any(
            LiveOutputInner.class))).thenThrow(amsError);

        var message = assertThrows(
            ConflictException.class,
            () -> mediaService.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Live Output: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }


    @DisplayName("Should throw 404 error when creating a live output but cannot find live event")
    @Test
    void startLiveEventLiveOutputLiveEventNotFound() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        var amsError = mockAmsError(404);
        when(liveOutputClient.create(eq(resourceGroup), eq(accountName), eq(liveEventName), eq(liveEventName), any(
            LiveOutputInner.class))).thenThrow(amsError);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should throw 404 error when attempting to start live event that cannot be found (after setup)")
    @Test
    void startLiveEventStartNotFound() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        var amsError = mockAmsError(404);
        doThrow(amsError).when(liveEventClient).start(resourceGroup, accountName, liveEventName);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).start(any(), any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    private UserAuthentication mockAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.getUserId()).thenReturn(user.getId());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
        return mockAuth;
    }

    private LiveEventsClient mockLiveEventClient() {
        var client = mock(LiveEventsClient.class);
        when(amsClient.getLiveEvents()).thenReturn(client);
        return client;
    }

    private AssetsClient mockAssetsClient() {
        var client = mock(AssetsClient.class);
        when(amsClient.getAssets()).thenReturn(client);
        return client;
    }

    private LiveOutputsClient mockLiveOutputClient() {
        var client = mock(LiveOutputsClient.class);
        when(amsClient.getLiveOutputs()).thenReturn(client);
        return client;
    }

    private ManagementException mockAmsError(int status) {
        var response = mock(HttpResponse.class);
        when(response.getStatusCode()).thenReturn(status);
        return new ManagementException("error", response);
    }
}
