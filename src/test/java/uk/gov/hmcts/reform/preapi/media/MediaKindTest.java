package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveOutput;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = MediaKind.class)
public class MediaKindTest {
    @MockBean
    private CaptureSessionRepository captureSessionRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MediaKindClient mockClient;

    @Autowired
    private MediaKind mediaKind;

    private User user;

    private CaptureSession captureSession;

    @BeforeEach
    void setUp() {
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

    @DisplayName("Should get a list of assets")
    @Test
    void getAssetsSuccess() {
        var asset1 = createMkAsset("asset1");
        var asset2 = createMkAsset("asset2");
        var mockAssets = MkGetListResponse.<MkAsset>builder()
            .value(List.of(asset1, asset2))
            .build();

        when(mockClient.getAssets(eq(0))).thenReturn(mockAssets);

        var assets = mediaKind.getAssets();

        assertThat(assets).hasSize(2);
        assertThat(assets.get(0).getName()).isEqualTo(asset1.getName());
        assertThat(assets.get(1).getName()).isEqualTo(asset2.getName());
    }

    @DisplayName("Should fail to get a list of assets and throw a media kind exception")
    @Test
    void getAssetsFeignExceptionThrown() {
        when(mockClient.getAssets(0)).thenThrow(FeignException.class);

        assertThrows(MediaKindException.class, () -> mediaKind.getAssets());
    }


    @Test
    void getAllPaginatedMkListEmpty() {
        MediaKind.GetListFunction<String> mockFunction = mock((skip ->
            MkGetListResponse.<String>builder().value(List.of()).build()));

        var result = mediaKind.getAllMkList(mockFunction).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void getAllPaginatedMkListOnePage() {
        MediaKind.GetListFunction<String> mockFunction = mock((skip ->
            MkGetListResponse.<String>builder().value(List.of("item 1", "item 2")).build()));

        var result = mediaKind.getAllMkList(mockFunction).toList();

        assertThat(result).hasSize(2);
        assertThat(result).contains("item 1");
        assertThat(result).contains("item 2");
    }

    @Test
    void getAllPaginatedMkListMultiPage() {
        MediaKind.GetListFunction<String> mockFunction = mock((skip ->
            MkGetListResponse.<String>builder().build())
        );

        when(mockFunction.get(0)).thenReturn(
            MkGetListResponse.<String>builder()
                .value(List.of("item 1", "item 2"))
                .nextLink("next link skip token = 2")
                .supplemental(MkGetListResponse.Supplemental.builder()
                                  .pagination(MkGetListResponse.MkPagination.builder()
                                                  .end(2).build())
                                  .build())
                .build()
        );
        when(mockFunction.get(2)).thenReturn(
            MkGetListResponse.<String>builder()
                .value(List.of("item 3", "item 4"))
                .build()
        );

        var result = mediaKind.getAllMkList(mockFunction).toList();

        assertThat(result).hasSize(4);
        assertThat(result).contains("item 1");
        assertThat(result).contains("item 2");
        assertThat(result).contains("item 3");
        assertThat(result).contains("item 4");
    }

    @DisplayName("Should get asset by asset name")
    @Test
    void getAssetByAssetNameSuccess() {
        var assetName = UUID.randomUUID().toString();
        var asset = createMkAsset(assetName);

        when(mockClient.getAsset(eq(assetName))).thenReturn(asset);

        var result = mediaKind.getAsset(assetName);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(assetName);
    }

    @DisplayName("Should throw on MediaKind exception on feign client errors")
    @Test
    void getAssetByAssetNameFeignExceptionThrown() {
        when(mockClient.getAsset(anyString())).thenThrow(FeignException.class);

        assertThrows(
            MediaKindException.class,
            () -> mediaKind.getAsset("asset1")
        );
        verify(mockClient, times(1)).getAsset(anyString());
    }

    @DisplayName("Should return null when get asset returns 404")
    @Test
    void getAssetByAssetNameNotFound() {
        var mockError = mock(FeignException.NotFound.class);
        when(mockClient.getAsset(anyString())).thenThrow(mockError);

        assertThat(mediaKind.getAsset("asset1")).isNull();

        verify(mockClient, times(1)).getAsset(anyString());
    }

    @DisplayName("Should get live event by name")
    @Test
    void getLiveEventByNameSuccess() {
        var liveEventName = UUID.randomUUID().toString();
        var liveEvent = createLiveEvent(liveEventName);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(liveEvent);

        var result = mediaKind.getLiveEvent(liveEventName);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(liveEventName);
        assertThat(result.getDescription()).isEqualTo(liveEvent.getProperties().getDescription());
        assertThat(result.getResourceState()).isEqualTo(liveEvent.getProperties().getResourceState());
        assertThat(result.getId()).isEqualTo(liveEvent.getId());
        assertThat(result.getInputRtmp()).isEqualTo(liveEvent.getProperties().getInput().endpoints().getFirst().url());
    }

    @DisplayName("Should return throw not found error when get live event returns 404")
    @Test
    void getLiveEventNotFound() {
        var mockError = mock(FeignException.NotFound.class);

        when(mockClient.getLiveEvent(anyString())).thenThrow(mockError);

        assertThrows(
            NotFoundException.class,
            () -> mediaKind.getLiveEvent("not-found")
        );

        verify(mockClient, times(1)).getLiveEvent("not-found");
    }

    @DisplayName("Should return a list of all live events")
    @Test
    void getLiveEventListSuccess() {
        var liveEventName1 = UUID.randomUUID().toString();
        var liveEventName2 = UUID.randomUUID().toString();
        var liveEvents = List.of(
            createLiveEvent(liveEventName1),
            createLiveEvent(liveEventName2)
        );

        when(mockClient.getLiveEvents(0))
            .thenReturn(MkGetListResponse.<MkLiveEvent>builder()
                            .value(liveEvents)
                            .build()
            );

        var results = mediaKind.getLiveEvents();
        assertThat(results).hasSize(2);

        assertThat(results.getFirst().getName()).isEqualTo(liveEventName1);
        assertThat(results.getLast().getName()).isEqualTo(liveEventName2);
    }

    private MkAsset createMkAsset(String name) {
        return MkAsset.builder()
            .name(name)
            .properties(MkAssetProperties.builder()
                            .description("description: " + name)
                            .storageAccountName("example storage account")
                            .container("container name")
                            .build())
            .build();
    }

    private MkLiveEvent createLiveEvent(String name) {
        return MkLiveEvent.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .location("UK South")
            .properties(MkLiveEvent.MkLiveEventProperties.builder()
                            .description("description: " + name)
                            .useStaticHostname(true)
                            .resourceState("Stopped")
                            .input(new LiveEventInput()
                                       .withEndpoints(List.of(new LiveEventEndpoint()
                                                                  .withProtocol("RTMP")
                                                                  .withUrl("rtmps://example url"))))
                            .preview(new LiveEventPreview())
                            .build())
            .build();
    }

    @DisplayName("Should return 404 error when starting live event for a capture session that does not exist")
    @Test
    void startLiveEventCaptureSessionNotFound() {
        var id = UUID.randomUUID();
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.startLiveEvent(id)
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
            () -> mediaKind.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message)
            .isEqualTo("Conflict: Capture Session: " + captureSession.getId() + " has already been finished");
    }

    @DisplayName("Should return the capture session if already started and not finished live event")
    @Test
    void startLiveEventAlreadyRunning() {
        captureSession.setIngestAddress("valid ingest address");
        captureSession.setStartedAt(Timestamp.from(Instant.now()));
        captureSession.setStartedByUser(user);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));

        var model = mediaKind.startLiveEvent(captureSession.getId());
        assertThat(model).isNotNull();
        assertThat(model.getId()).isEqualTo(captureSession.getId());
        assertThat(model.getStartedAt()).isNotNull();
    }

    @DisplayName("Should return the capture session when successfully started the live event")
    @Test
    void startLiveEventSuccess() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEvent.MkLiveEventProperties.builder()
                    .resourceState("Starting")
                    .build(),
                MkLiveEvent.MkLiveEventProperties.builder()
                    .resourceState("Starting")
                    .build(),
                MkLiveEvent.MkLiveEventProperties.builder()
                    .resourceState("Running")
                    .build(),
                MkLiveEvent.MkLiveEventProperties.builder()
                    .resourceState("Running")
                    .input(
                        new LiveEventInput()
                            .withEndpoints(List.of(
                                new LiveEventEndpoint()
                                    .withProtocol("RTMP")
                                    .withUrl("rtmps://some-rtmp-address"),
                                new LiveEventEndpoint()
                                    .withProtocol("RTMP")
                                    .withUrl("rtmp://some-rtmp-address")
                            )))
                    .build()
            );

        var model = mediaKind.startLiveEvent(captureSession.getId());

        assertThat(model).isNotNull();
        assertThat(model.getStatus()).isEqualTo(RecordingStatus.STANDBY);
        assertThat(model.getIngestAddress()).isEqualTo("rtmps://some-rtmp-address");
        assertThat(model.getStartedAt()).isNotNull();
        assertThat(model.getStartedByUserId()).isEqualTo(user.getId());

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(4)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, times(1)).startLiveEvent(any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should return the capture session when successfully started the live event")
    @Test
    void startLiveEventLiveEventConflictSuccess() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(mockClient.putLiveEvent(any(), any()))
            .thenThrow(mock(FeignException.Conflict.class));
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEvent.MkLiveEventProperties.builder()
                    .resourceState("Starting")
                    .build(),
                MkLiveEvent.MkLiveEventProperties.builder()
                    .resourceState("Starting")
                    .build(),
                MkLiveEvent.MkLiveEventProperties.builder()
                    .resourceState("Running")
                    .build(),
                MkLiveEvent.MkLiveEventProperties.builder()
                    .resourceState("Running")
                    .input(
                        new LiveEventInput()
                            .withEndpoints(List.of(
                                new LiveEventEndpoint()
                                    .withProtocol("RTMP")
                                    .withUrl("rtmps://some-rtmp-address"),
                                new LiveEventEndpoint()
                                    .withProtocol("RTMP")
                                    .withUrl("rtmp://some-rtmp-address")
                            )))
                    .build()
            );

        var model = mediaKind.startLiveEvent(captureSession.getId());

        assertThat(model).isNotNull();
        assertThat(model.getStatus()).isEqualTo(RecordingStatus.STANDBY);
        assertThat(model.getIngestAddress()).isEqualTo("rtmps://some-rtmp-address");
        assertThat(model.getStartedAt()).isNotNull();
        assertThat(model.getStartedByUserId()).isEqualTo(user.getId());

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(4)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, times(1)).startLiveEvent(any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should throw not found error when live event cannot be found after creation")
    @Test
    void startLiveEventNotFoundAfterCreate() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(mockClient.getLiveEvent(liveEventName)).thenThrow(mock(FeignException.NotFound.class));

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.startLiveEvent(captureSession.getId())
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should throw 409 error when asset already exists")
    @Test
    void startLiveEventAssetConflict() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockClient.putAsset(eq(liveEventName), any(MkAsset.class)))
            .thenThrow(mock(FeignException.Conflict.class));

        var message = assertThrows(
            ConflictException.class,
            () -> mediaKind.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Asset: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should throw 409 error when live output already exists")
    @Test
    void startLiveEventLiveOutputConflict() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockClient.putLiveOutput(eq(liveEventName), eq(liveEventName), any()))
            .thenThrow(mock(FeignException.Conflict.class));

        var message = assertThrows(
            ConflictException.class,
            () -> mediaKind.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Live Output: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should throw 404 error when creating a live output but cannot find live event")
    @Test
    void startLiveEventLiveOutputLiveEventNotFound() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockClient.putLiveOutput(eq(liveEventName), eq(liveEventName), any(MkLiveOutput.class)))
            .thenThrow(FeignException.NotFound.class);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    @DisplayName("Should throw 404 error when attempting to start live event that cannot be found (after setup)")
    @Test
    void startLiveEventStartNotFound() {
        mockAdminUser();
        var liveEventName = captureSession.getId().toString().replaceAll("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));
        when(userRepository.findById(user.getId()))
            .thenReturn(Optional.of(user));
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        doThrow(mock(FeignException.NotFound.class)).when(mockClient).startLiveEvent(liveEventName);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.startLiveEvent(captureSession.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        assertThat(captureSession.getStartedAt()).isNotNull();
        assertThat(captureSession.getStartedByUser().getId()).isEqualTo(user.getId());
        assertThat(captureSession.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, times(1)).startLiveEvent(any());
        verify(captureSessionRepository, times(2)).saveAndFlush(any());
    }

    private void mockAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.getUserId()).thenReturn(user.getId());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }
}
