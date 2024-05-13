package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.media.AzureMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;

@RestController
@RequestMapping("/media-service")
public class MediaServiceController extends PreApiController {

    private final AzureMediaService mediaService;
    private final MediaKind mediaKind;

    @Autowired
    public MediaServiceController(AzureMediaService mediaService, MediaKind mediaKind) {
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
}
