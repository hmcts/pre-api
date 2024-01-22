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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.UUID;

@RestController
@RequestMapping("/recordings")
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
        @PathVariable UUID recordingId
    ) {
        // TODO Recordings returned need to be shared with the current user
        return ResponseEntity.ok(recordingService.findById(recordingId));
    }

    @GetMapping
    @Operation(operationId = "getRecordings", summary = "Search all Recordings")
    @Parameter(
        name = "captureSessionId",
        description = "The capture session to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "parentRecordingId",
        description = "The parent recording to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "caseReference",
        description = "The case reference to search by",
        schema = @Schema(implementation = String.class),
        example = "CASE12345"
    )
    @Parameter(
        name = "page",
        description = "The page number of search result to return",
        schema = @Schema(implementation = Integer.class),
        example = "1"
    )
    @Parameter(
        name = "size",
        description = "The number of search results to return per page",
        schema = @Schema(implementation = Integer.class),
        example = "10"
    )
    public HttpEntity<PagedModel<EntityModel<RecordingDTO>>> searchRecordings(
        @Parameter(hidden = true) @ModelAttribute SearchRecordings params,
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<RecordingDTO> assembler
    ) {
        // TODO Recordings returned need to be shared with the user

        var resultPage = recordingService.findAll(
            params.getCaptureSessionId(),
            params.getParentRecordingId(),
            params.getCaseReference(),
            pageable
        );

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));

    }

    @PutMapping("/{recordingId}")
    @Operation(operationId = "putRecordings", summary = "Create or Update a Recording")
    public ResponseEntity<Void> upsert(
        @PathVariable UUID recordingId,
        @RequestBody CreateRecordingDTO createRecordingDTO
    ) {
        // TODO Check user has access to booking and capture session (and recording if is update)
        if (!recordingId.equals(createRecordingDTO.getId())) {
            throw new PathPayloadMismatchException("recordingId", "createRecordingDTO.id");
        }

        return getUpsertResponse(recordingService.upsert(createRecordingDTO), createRecordingDTO.getId());
    }

    @DeleteMapping("/{recordingId}")
    @Operation(operationId = "deleteRecording", summary = "Delete a Recording")
    public ResponseEntity<Void> deleteRecordingById(
        @PathVariable UUID recordingId
    ) {
        // TODO Ensure user has access to the recording
        recordingService.deleteById(recordingId);
        return ResponseEntity.ok().build();
    }
}
