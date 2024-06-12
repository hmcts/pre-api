package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;

@RestController
@RequestMapping("/media-service")
public class MediaServiceController extends PreApiController {

    private final MediaServiceBroker mediaServiceBroker;

    @Autowired
    public MediaServiceController(MediaServiceBroker mediaServiceBroker) {
        super();
        this.mediaServiceBroker = mediaServiceBroker;
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
}
