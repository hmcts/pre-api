package uk.gov.hmcts.reform.preapi.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/edits")
public class EditController {
    private final EditRequestService editRequestService;

    @Autowired
    public EditController(EditRequestService editRequestService) {
        this.editRequestService = editRequestService;
    }

    @PostMapping(value = "/{sourceRecordingId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateEdit(@PathVariable UUID sourceRecordingId,
                                        @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(editRequestService.upsert(sourceRecordingId, file));
    }
}
