package uk.gov.hmcts.reform.preapi.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings/{bookingId}/recordings")
public class RecordingController extends PreApiController {

    private final RecordingService recordingService;

    @Autowired
    public RecordingController(RecordingService recordingService) {
        super();
        this.recordingService = recordingService;
    }

    @GetMapping("/{recordingId}")
    public ResponseEntity<CreateRecordingDTO> getRecordingById(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId
    ) {
        // TODO Recordings returned need to be shared with the current user
        CreateRecordingDTO createRecordingDTO = recordingService.findById(bookingId, recordingId);
        if (createRecordingDTO == null) {
            throw new NotFoundException("CreateRecordingDTO: " + recordingId);
        }
        return ResponseEntity.ok(createRecordingDTO);
    }

    @GetMapping
    public ResponseEntity<List<CreateRecordingDTO>> getAllRecordingsByBookingId(
        @PathVariable UUID bookingId
    ) {
        // TODO Recordings returned need to be shared with the user
        return ResponseEntity.ok(recordingService.findAllByBookingId(bookingId));
    }

    @PutMapping("/{recordingId}")
    public ResponseEntity<Void> upsert(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId,
        @RequestBody CreateRecordingDTO createRecordingDTO
    ) {
        // TODO Check user has access to booking and capture session (and recording if is update)
        if (!recordingId.equals(createRecordingDTO.getId())) {
            throw new PathPayloadMismatchException("recordingId", "createRecordingDTO.id");
        }

        return getUpsertResponse(recordingService.upsert(bookingId, createRecordingDTO), createRecordingDTO.getId());
    }

    @DeleteMapping("/{recordingId}")
    public ResponseEntity<Void> deleteRecordingById(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId
    ) {
        // TODO Ensure user has access to the recording
        recordingService.deleteById(bookingId, recordingId);
        return ResponseEntity.ok().build();
    }
}
