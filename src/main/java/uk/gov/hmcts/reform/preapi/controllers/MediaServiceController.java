package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/media-service")
public class MediaServiceController extends PreApiController {

    private final MediaServiceBroker mediaServiceBroker;
    private final CaptureSessionService captureSessionService;
    private final AzureIngestStorageService azureIngestStorageService;

    @Autowired
    public MediaServiceController(MediaServiceBroker mediaServiceBroker,
                                  CaptureSessionService captureSessionService,
                                  AzureIngestStorageService azureIngestStorageService) {
        super();
        this.mediaServiceBroker = mediaServiceBroker;
        this.captureSessionService = captureSessionService;
        this.azureIngestStorageService = azureIngestStorageService;
    }

    @GetMapping("/health")
    @Operation(operationId = "mediaServiceHealth", summary = "Check the status of the media service connection")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<String> mediaService() {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        mediaService.getAssets();
        return ResponseEntity.ok("successfully connected to media service ("
                                     + mediaService.getClass().getSimpleName()
                                     + ")");
    }

    @GetMapping("/assets")
    @Operation(operationId = "getAssets", summary = "Get all media service assets")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<List<AssetDTO>> getAssets() {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        return ResponseEntity.ok(mediaService.getAssets());
    }

    @GetMapping("/assets/{assetName}")
    @Operation(operationId = "getAssetsByName", summary = "Get a media service asset by name")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<AssetDTO> getAsset(@PathVariable String assetName) {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        var data = mediaService.getAsset(assetName);
        if (data == null) {
            throw new NotFoundException("Asset: " + assetName);
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/live-events")
    @Operation(operationId = "getLiveEvents", summary = "Get a list of media service live events")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<List<LiveEventDTO>> getLiveEvents() {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        return ResponseEntity.ok(mediaService.getLiveEvents());
    }

    @GetMapping("/live-events/{liveEventName}")
    @Operation(operationId = "getLiveEventsByName", summary = "Get a media service live event by name")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<LiveEventDTO> getLiveEvents(@PathVariable String liveEventName) {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        var data = mediaService.getLiveEvent(liveEventName);
        if (data == null) {
            throw new NotFoundException("Live event: " + liveEventName);
        }
        return ResponseEntity.ok(data);
    }

    @PutMapping("/live-event/start/{captureSessionId}")
    @Operation(operationId = "startLiveEvent", summary = "Start a live event")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<CaptureSessionDTO> startLiveEvent(@PathVariable UUID captureSessionId)
        throws InterruptedException {
        var dto = captureSessionService.findById(captureSessionId);

        if (dto.getFinishedAt() != null) {
            throw new ConflictException("Capture Session: " + dto.getId() + " has already been finished");
        }

        if (dto.getStartedAt() != null) {
            return ResponseEntity.ok(dto);
        }

        var mediaService = mediaServiceBroker.getEnabledMediaService();
        String ingestAddress;

        try {
            ingestAddress = mediaService.startLiveEvent(dto);
        } catch (Exception e) {
            captureSessionService.startCaptureSession(captureSessionId, null);
            throw e;
        }

        return ResponseEntity.ok(captureSessionService.startCaptureSession(captureSessionId, ingestAddress));
    }

    @PutMapping("/live-event/end/{captureSessionId}")
    @Operation(operationId = "stopLiveEvent", summary = "Stop a live event")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<CaptureSessionDTO> stopLiveEvent(
        @PathVariable UUID captureSessionId
    ) throws InterruptedException {
        var dto = captureSessionService.findById(captureSessionId);

        if (dto.getFinishedAt() != null) {
            return ResponseEntity.ok(dto);
        }

        if (dto.getStartedAt() == null) {
            throw new ResourceInWrongStateException("Resource: Capture Session("
                                                        + captureSessionId
                                                        + ") has not been started.");
        }

        if (dto.getStatus() != RecordingStatus.STANDBY && dto.getStatus() != RecordingStatus.RECORDING) {
            throw new ResourceInWrongStateException(
                "Capture Session",
                captureSessionId.toString(),
                dto.getStatus().toString(),
                "STANDBY or RECORDING"
            );
        }

        var recordingId = UUID.randomUUID();
        dto = captureSessionService.stopCaptureSession(captureSessionId, RecordingStatus.PROCESSING, recordingId);

        var mediaService = mediaServiceBroker.getEnabledMediaService();
        try {
            var status = mediaService.stopLiveEvent(dto, recordingId);
            dto = captureSessionService.stopCaptureSession(captureSessionId, status, recordingId);
        } catch (Exception e) {
            captureSessionService.stopCaptureSession(captureSessionId, RecordingStatus.FAILURE, recordingId);
            throw e;
        }

        return ResponseEntity.ok(dto);
    }

    @PutMapping("/streaming-locator/live-event/{captureSessionId}")
    @Operation(
        operationId = "createLiveEventStreamingLocator",
        summary = "Create live event streaming endpoint and locator. Update capture session with streaming endpoint."
    )
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<CaptureSessionDTO> createLiveEventStreamingLocator(@PathVariable UUID captureSessionId) {
        // load captureSession
        var captureSession = captureSessionService.findById(captureSessionId);

        // return existing captureSession if currently live
        if (captureSession.getLiveOutputUrl() != null && captureSession.getStatus() == RecordingStatus.RECORDING) {
            return ResponseEntity.ok(captureSession);
        }

        // check if captureSession is in correct state
        if (captureSession.getStatus() != RecordingStatus.STANDBY) {
            throw new ResourceInWrongStateException(captureSession.getClass().getSimpleName(),
                                                    captureSessionId.toString(),
                                                    captureSession.getStatus().name(),
                                                    RecordingStatus.STANDBY.name());
        }

        // play live event
        var liveOutputUrl = mediaServiceBroker.getEnabledMediaService().playLiveEvent(captureSessionId);

        // update captureSession
        captureSession.setLiveOutputUrl(liveOutputUrl);
        captureSession.setStatus(RecordingStatus.RECORDING);
        captureSessionService.upsert(captureSession);

        return ResponseEntity.ok(captureSession);
    }

    @PostMapping("/live-event/check/{captureSessionId}")
    @Operation(
        operationId = "checkStream",
        summary = "Check stream has started"
    )
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<CaptureSessionDTO> checkStream(@PathVariable UUID captureSessionId) {
        var captureSession = captureSessionService.findById(captureSessionId);
        if (captureSession.getStatus() == RecordingStatus.RECORDING) {
            return ResponseEntity.ok(captureSession);
        }

        if (captureSession.getFinishedAt() != null) {
            throw new ResourceInWrongStateException("Resource: Capture Session("
                                                        + captureSessionId
                                                        + ") has been finished.");
        }

        if (captureSession.getStartedAt() == null) {
            throw new ResourceInWrongStateException("Resource: Capture Session("
                                                          + captureSessionId
                                                          + ") has not been started.");
        }

        if (captureSession.getStatus() != RecordingStatus.STANDBY) {
            throw new ResourceInWrongStateException(captureSession.getClass().getSimpleName(),
                                                    captureSessionId.toString(),
                                                    captureSession.getStatus().name(),
                                                    RecordingStatus.STANDBY.name());
        }

        if (azureIngestStorageService.doesIsmFileExist(captureSession.getBookingId().toString())) {
            captureSession = captureSessionService.setCaptureSessionStatus(captureSessionId, RecordingStatus.RECORDING);
        }

        return ResponseEntity.ok(captureSession);
    }
}
