package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.AzureMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;

import java.util.List;
import uk.gov.hmcts.reform.preapi.media.MediaKind;

@RestController
@RequestMapping("/media-service")
public class MediaServiceController extends PreApiController {

    private final AzureMediaService mediaService;
    private final MediaKind mediaKind;

    // todo change when moving to mk
    @Autowired
    public MediaServiceController(
        AzureMediaService mediaService,
        MediaKind mediaKind
    ) {
        this.mediaService = mediaService;
        this.mediaKind = mediaKind;
    }

    // todo remove - temporary to check AMS integration is working
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<String> mediaService() {
        mediaService.getAssets();
        return ResponseEntity.ok("successfully connected to media service (ams)");
    }

    // todo remove - temporary to check MK integration is working
    @GetMapping("/health-mk")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<String> mediaServiceMk() {
        mediaKind.getAssets();
        return ResponseEntity.ok("successfully connected to media service (mk)");
    }

    @GetMapping("/assets")
    @Operation(operationId = "getAssets", summary = "Get all media service assets")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<List<AssetDTO>> getAssets() {
        return ResponseEntity.ok(mediaService.getAssets());
    }

    @GetMapping("/assets/{assetName}")
    @Operation(operationId = "getAssetsByName", summary = "Get a media service asset by name")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<AssetDTO> getAsset(@PathVariable String assetName) {
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
        return ResponseEntity.ok(mediaService.getLiveEvents());
    }

    @GetMapping("/live-events/{liveEventName}")
    @Operation(operationId = "getLiveEventsByName", summary = "Get a media service live event by name")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<LiveEventDTO> getLiveEvents(@PathVariable String liveEventName) {
        var data = mediaService.getLiveEvent(liveEventName);
        if (data == null) {
            throw new NotFoundException("Live event: " + liveEventName);
        }
        return ResponseEntity.ok(data);
    }
}
