package uk.gov.hmcts.reform.preapi.controllers;


import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.services.CourtService;

import java.util.List;
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
    @Operation(operationId = "searchCourts", summary = "Search for Courts by court type, name, location code or region name")
    public ResponseEntity<List<CourtDTO>> getCourts(
        @RequestParam(required = false) CourtType courtType,
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String locationCode,
        @RequestParam(required = false) String regionName
    ) {
        return ResponseEntity.ok(courtService.findAllBy(courtType, name, locationCode, regionName));
    }

    @PutMapping("/{courtId}")
    @Operation(operationId = "putCourt", summary = "Create or Update a Court")
    public ResponseEntity<Void> upsert(@PathVariable UUID courtId, @RequestBody CreateCourtDTO createCourtDTO) {
        if (!createCourtDTO.getId().equals(courtId)) {
            throw new PathPayloadMismatchException("courtId", "createCourtDTO.id");
        }
        return getUpsertResponse(courtService.upsert(createCourtDTO), createCourtDTO.getId());
    }
}
