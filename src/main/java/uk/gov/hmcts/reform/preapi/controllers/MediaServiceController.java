package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/media-service")
public class MediaServiceController extends PreApiController {

    private final MediaServiceBroker mediaServiceBroker;
    private final CaptureSessionService captureSessionService;

    @Autowired
    public MediaServiceController(MediaServiceBroker mediaServiceBroker, CaptureSessionService captureSessionService) {
        super();
        this.mediaServiceBroker = mediaServiceBroker;
        this.captureSessionService = captureSessionService;
    }

    @GetMapping("/health")
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

    @GetMapping("/vod")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<PlaybackDTO> getVod(@RequestParam UUID recordingId) {
        // todo: dont rely on naming convention, link asset name in db
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        var assetName = recordingId.toString().replace("-", "") + "_output";
        var data = mediaService.playAsset(assetName);
        if (data == null) {
            throw new NotFoundException("Asset: " + assetName);
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
}
