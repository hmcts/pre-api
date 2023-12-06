package uk.gov.hmcts.reform.preapi.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
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
    public ResponseEntity<RecordingDTO> getRecordingById(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId
    ) {
        // TODO Recordings returned need to be shared with the current user
        RecordingDTO recordingDTO = recordingService.findById(bookingId, recordingId);
        if (recordingDTO == null) {
            throw new NotFoundException("RecordingDTO: " + recordingId);
        }
        return ResponseEntity.ok(recordingDTO);
    }

    @GetMapping
    public ResponseEntity<List<RecordingDTO>> getAllRecordingsByBookingId(
        @PathVariable UUID bookingId
    ) {
        // TODO Recordings returned need to be shared with the user
        return ResponseEntity.ok(recordingService.findAllByBookingId(bookingId));
    }

    @PutMapping("/{recordingId}")
    public ResponseEntity<RecordingDTO> upsert(
        @PathVariable UUID bookingId,
        @PathVariable UUID recordingId,
        @RequestBody RecordingDTO recordingDTO
    ) {
        if (!recordingId.equals(recordingDTO.getId())) {
            throw new PathPayloadMismatchException("recordingId", "recordingDto.id");
        }

        var result = recordingService.upsert(bookingId, recordingDTO);
        var location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("")
            .buildAndExpand(recordingDTO.getId())
            .toUri();

        if (result == UpsertResult.CREATED) {
            return ResponseEntity.created(location).build();
        } else if (result == UpsertResult.UPDATED) {
            return ResponseEntity.noContent().location(location).build();
        }

        throw new UnknownServerException("Unexpected result: " + result);
    }
}
