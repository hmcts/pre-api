package uk.gov.hmcts.reform.preapi.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.services.CourtService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/courts")
public class CourtController {
    private final CourtService courtService;

    @Autowired
    public CourtController(CourtService courtService) {
        this.courtService = courtService;
    }

    @GetMapping("/{courtId}")
    public ResponseEntity<CourtDTO> getCourtById(@PathVariable UUID courtId) {
        return ResponseEntity.ok(courtService.findById(courtId));
    }

    @GetMapping
    public ResponseEntity<List<CourtDTO>> getCourts(
        @RequestParam(required = false) CourtType courtType,
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String locationCode,
        @RequestParam(required = false) String regionName
    ) {
        return ResponseEntity.ok(courtService.findAllBy(courtType, name, locationCode, regionName));
    }
}
