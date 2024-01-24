package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchCaptureSessions;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/capture-sessions")
public class CaptureSessionController {

    private final CaptureSessionService captureSessionService;

    @Autowired
    public CaptureSessionController(CaptureSessionService captureSessionService) {
        this.captureSessionService = captureSessionService;
    }

    @GetMapping("/{captureSessionId}")
    @Operation(operationId = "getCaptureSessionById", summary = "Get a Capture Session by Id")
    public ResponseEntity<CaptureSessionDTO> getCaptureSessionById(@PathVariable UUID captureSessionId) {
        return ResponseEntity.ok(captureSessionService.findById(captureSessionId));
    }


    @GetMapping
    @Operation(operationId = "searchCaptureSessions", summary = "Search All Capture Sessions")
    @Parameter(
        name = "caseReference",
        description = "The case reference to search for",
        schema = @Schema(implementation = String.class),
        example = "1234567890123456"
    )
    @Parameter(
        name = "bookingId",
        description = "The booking id to search for",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "origin",
        description = "The origin of the capture session to search for",
        schema = @Schema(implementation = RecordingOrigin.class)
    )
    @Parameter(
        name = "recordingStatus",
        description = "The recording status to search for",
        schema = @Schema(implementation = RecordingStatus.class)
    )
    @Parameter(
        name = "scheduledFor",
        description = "The Date the Booking was scheduled for",
        schema = @Schema(implementation = String.class, format = "date"),
        example = "2024-04-27"
    )
    @Parameter(
        name = "page",
        description = "The page number of search result to return",
        schema = @Schema(implementation = Integer.class),
        example = "0"
    )
    @Parameter(
        name = "size",
        description = "The number of search results to return per page",
        schema = @Schema(implementation = Integer.class),
        example = "10"
    )
    public HttpEntity<PagedModel<EntityModel<CaptureSessionDTO>>> searchCaptureSessions(
        @Parameter(hidden = true) @ModelAttribute SearchCaptureSessions params,
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true)PagedResourcesAssembler<CaptureSessionDTO> assembler
    ) {
        var resultPage = captureSessionService.searchBy(
            params.getCaseReference(),
            params.getBookingId(),
            params.getOrigin(),
            params.getRecordingStatus(),
            params.getScheduledFor() != null
                ? Optional.of(Timestamp.from(params.getScheduledFor().toInstant()))
                : Optional.empty(),
            pageable
        );

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }
        return ok(assembler.toModel(resultPage));
    }

    @DeleteMapping("/{captureSessionId}")
    @Operation(operationId = "deleteCaptureSessionById", summary = "Delete Capture Session by Id")
    public ResponseEntity<Void> deleteCaptureSessionById(@PathVariable UUID captureSessionId) {
        captureSessionService.deleteById(captureSessionId);
        return ResponseEntity.ok().build();
    }
}
