package uk.gov.hmcts.reform.preapi.controllers;


import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareRecordingDTO;
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
    @Operation(operationId = "getRecordingById", summary = "Get a Recording by Id")
    public ResponseEntity<RecordingDTO> getRecordingById(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId
    ) {
        // TODO Recordings returned need to be shared with the current user
        return ResponseEntity.ok(recordingService.findById(bookingId, recordingId));
    }

    @GetMapping
    @Operation(operationId = "getRecordingsByBookingId", summary = "Get all Recordings by Booking Id")
    public ResponseEntity<List<RecordingDTO>> getAllRecordingsByBookingId(
        @PathVariable UUID bookingId,
        @RequestParam(required = false) UUID captureSessionId,
        @RequestParam(required = false) UUID parentRecordingId
    ) {
        // TODO Recordings returned need to be shared with the user
        return ResponseEntity.ok(recordingService.findAllByBookingId(bookingId, captureSessionId, parentRecordingId));
    }

    @PutMapping("/{recordingId}")
    @Operation(operationId = "putRecordings", summary = "Create or Update a Recording")
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
    @Operation(operationId = "deleteRecording", summary = "Delete a Recording")
    public ResponseEntity<Void> deleteRecordingById(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId
    ) {
        // TODO Ensure user has access to the recording
        recordingService.deleteById(bookingId, recordingId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{recordingId}/share")
    public ResponseEntity<Void> shareRecordingById(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId,
        @RequestBody ShareRecordingDTO shareRecordingDTO
    ) {
        // TODO Ensure user has access to share the recording
        if (!recordingId.equals(shareRecordingDTO.getCaptureSessionId())) {
            throw new PathPayloadMismatchException("recordingId", "shareRecordingDTO.captureSessionId");
        }

        return getUpsertResponse(recordingService.shareRecordingById(bookingId, shareRecordingDTO), shareRecordingDTO.getId());
    }
}
