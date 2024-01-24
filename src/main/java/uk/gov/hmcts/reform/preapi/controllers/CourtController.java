package uk.gov.hmcts.reform.preapi.controllers;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchCourts;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.services.CourtService;

import java.util.UUID;

@RestController
@RequestMapping("/courts")
public class CourtController extends PreApiController {
    private final CourtService courtService;

    @Autowired
    public CourtController(CourtService courtService) {
        super();
        this.courtService = courtService;
    }

    @GetMapping("/{courtId}")
    @Operation(operationId = "getCourtById", summary = "Get a Court by Id")
    public ResponseEntity<CourtDTO> getCourtById(@PathVariable UUID courtId) {
        return ResponseEntity.ok(courtService.findById(courtId));
    }

    @GetMapping
    @Operation(
        operationId = "searchCourts",
        summary = "Search for Courts by court type, name, location code or region name"
    )
    @Parameter(
        name = "courtType",
        description = "The type of the court to search by",
        schema = @Schema(implementation = CourtType.class)
    )
    @Parameter(
        name = "name",
        description = "The name of the court to search by",
        schema = @Schema(implementation = String.class),
        example = "Example"
    )
    @Parameter(
        name = "locationCode",
        description = "The location code of the court to search by",
        schema = @Schema(implementation = String.class)
    )
    @Parameter(
        name = "regionName",
        description = "The region name of the court to search by",
        schema = @Schema(implementation = String.class),
        example = "London"
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
    public HttpEntity<PagedModel<EntityModel<CourtDTO>>> getCourts(
        @Parameter(hidden = true) @ModelAttribute() SearchCourts params,
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<CourtDTO> assembler
    ) {
        Page<CourtDTO> resultPage = courtService.findAllBy(
            params.getCourtType(),
            params.getName(),
            params.getLocationCode(),
            params.getRegionName(),
            pageable
        );

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));
    }

    @PutMapping("/{courtId}")
    @Operation(operationId = "putCourt", summary = "Create or Update a Court")
    public ResponseEntity<Void> upsert(@PathVariable UUID courtId,  @Valid @RequestBody CreateCourtDTO createCourtDTO) {
        if (!courtId.equals(createCourtDTO.getId())) {
            throw new PathPayloadMismatchException("courtId", "createCourtDTO.id");
        }
        return getUpsertResponse(courtService.upsert(createCourtDTO), createCourtDTO.getId());
    }
}
