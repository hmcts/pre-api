package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.exception.UnsupportedMediaTypeException;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/edits")
public class EditController extends PreApiController {
    private final EditRequestService editRequestService;

    public static final String CSV_FILE_TYPE = "text/csv";

    @Autowired
    public EditController(EditRequestService editRequestService) {
        this.editRequestService = editRequestService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_3')")
    public ResponseEntity<EditRequestDTO> getEditRequestById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(editRequestService.findById(id));
    }

    @GetMapping
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
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_3')")
    public HttpEntity<PagedModel<EntityModel<EditRequestDTO>>> searchRecordings(
        @Parameter(schema = @Schema(implementation = UUID.class)) UUID sourceRecordingId,
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<EditRequestDTO> assembler
    ) {
        var resultPage = editRequestService.findAll(
            sourceRecordingId,
            pageable
        );

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_3')")
    public ResponseEntity<Void> upsertEditRequest(
        @PathVariable("id") UUID id,
        @Valid @RequestBody CreateEditRequestDTO createEditRequestDTO
    ) {
        if (!id.equals(createEditRequestDTO.getId())) {
            throw new PathPayloadMismatchException("editRequestId", "createEditRequestDTO.id");
        }

        return getUpsertResponse(editRequestService.upsert(createEditRequestDTO), id);
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER')")
    @PostMapping(value = "/from-csv/{sourceRecordingId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EditRequestDTO> createEditFromCsv(@PathVariable UUID sourceRecordingId,
                                                     @RequestParam("file") MultipartFile file) {
        var fileType = file.getContentType();
        if (fileType == null || !fileType.equals(CSV_FILE_TYPE)) {
            throw new UnsupportedMediaTypeException("Unsupported media type: Only CSV files are supported");
        }

        if (file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        return ResponseEntity.ok(editRequestService.upsert(sourceRecordingId, file));
    }
}
