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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings/{bookingId}/recordings")
public class RecordingController {

    private final RecordingService recordingService;

    @Autowired
    public RecordingController(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @GetMapping("/{recordingId}")
    public ResponseEntity<Recording> getRecordingById(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId
    ) {
        // TODO Recordings returned need to be shared with the current user
        Recording recording = recordingService.findById(bookingId, recordingId);
        if (recording == null) {
            throw new NotFoundException("Recording: " + recordingId);
        }
        return ResponseEntity.ok(recording);
    }

    @GetMapping
    public ResponseEntity<List<Recording>> getAllRecordingsByBookingId(
        @PathVariable UUID bookingId
    ) {
        // TODO Recordings returned need to be shared with the user
        return ResponseEntity.ok(recordingService.findAllByBookingId(bookingId));
    }
}
