package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.media.AzureMediaService;

import java.util.logging.Logger;

@RestController
@RequestMapping("/media-service")
public class MediaServiceController extends PreApiController {

    private final AzureMediaService mediaService;

    @Autowired
    public MediaServiceController(AzureMediaService mediaService) {
        this.mediaService = mediaService;
    }

    // todo remove - temporary to check AMS integration is working
    @GetMapping("/health")
    public ResponseEntity<String> mediaService() {
        mediaService.getAssets();
        return ResponseEntity.ok("successfully connected to media service");
    }
}
