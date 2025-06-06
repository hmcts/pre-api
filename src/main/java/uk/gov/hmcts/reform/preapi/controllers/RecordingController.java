package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.data.web.SortDefault;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<RecordingDTO> getRecordingById(
        @PathVariable UUID recordingId
    ) {
        return ResponseEntity.ok(recordingService.findById(recordingId));
    }

    @GetMapping
    @Operation(operationId = "getRecordings", summary = "Search all Recordings")
    @Parameter(
        name = "id",
        description = "Partial string of the recording id to search by",
        schema = @Schema(implementation = String.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
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
        name = "participantId",
        description = "The participant to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "witnessName",
        description = "The name of a witness to search by",
        schema = @Schema(implementation = String.class)
    )
    @Parameter(
        name = "defendantName",
        description = "The name of a defendant to search by",
        schema = @Schema(implementation = String.class)
    )
    @Parameter(
        name = "caseReference",
        description = "The case reference to search by",
        schema = @Schema(implementation = String.class),
        example = "CASE12345"
    )
    @Parameter(
        name = "startedAt",
        description = "The Date the recording's capture session was started at",
        schema = @Schema(implementation = String.class, format = "date"),
        example = "2024-04-27"
    )
    @Parameter(
        name = "courtId",
        description = "The court to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "includeDeleted",
        description = "Include recordings marked as deleted",
        schema = @Schema(implementation = Boolean.class)
    )
    @Parameter(
        name = "version",
        description = "The version number to search by",
        schema = @Schema(implementation = Integer.class)
    )
    @Parameter(
        name = "sort",
        description = "Sort by",
        schema = @Schema(implementation = String.class),
        example = "createdAt,desc"
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
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public HttpEntity<PagedModel<EntityModel<RecordingDTO>>> searchRecordings(
        @Parameter(hidden = true) @ModelAttribute SearchRecordings params,
        @SortDefault.SortDefaults(
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
        ) @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<RecordingDTO> assembler
    ) {
        var resultPage = recordingService.findAll(
            params,
            params.getIncludeDeleted() != null && params.getIncludeDeleted(),
            pageable
        );

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));

    }

    @PutMapping("/{recordingId}")
    @Operation(operationId = "putRecordings", summary = "Create or Update a Recording")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> upsert(
        @PathVariable UUID recordingId,
        @Valid @RequestBody CreateRecordingDTO createRecordingDTO
    ) {
        if (!recordingId.equals(createRecordingDTO.getId())) {
            throw new PathPayloadMismatchException("recordingId", "createRecordingDTO.id");
        }

        return getUpsertResponse(recordingService.upsert(createRecordingDTO), createRecordingDTO.getId());
    }

    @DeleteMapping("/{recordingId}")
    @Operation(operationId = "deleteRecording", summary = "Delete a Recording")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> deleteRecordingById(
        @PathVariable UUID recordingId
    ) {
        recordingService.deleteById(recordingId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{recordingId}/undelete")
    @Operation(operationId = "undeleteRecording", summary = "Revert deletion of a recording")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1')")
    public ResponseEntity<Void> undeleteRecording(@PathVariable UUID recordingId) {
        recordingService.undelete(recordingId);
        return ResponseEntity.ok().build();
    }
}
