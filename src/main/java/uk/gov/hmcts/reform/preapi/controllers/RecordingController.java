package uk.gov.hmcts.reform.preapi.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.model.Recording;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.UUID;

@RestController
@RequestMapping("/bookings/{bookingId}/recordings")
public class RecordingController {

    @Autowired
    private RecordingService recordingService;

    @GetMapping("/{recordingId}")
    public ResponseEntity<Recording> getRecordingById(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId
    ) {
        Recording recording = recordingService.getRecordingById(bookingId, recordingId);
        if (recording == null) {
            throw new NotFoundException(String.format("Recording: " + recordingId));
        }
        return ResponseEntity.ok(recording);
    }
}
