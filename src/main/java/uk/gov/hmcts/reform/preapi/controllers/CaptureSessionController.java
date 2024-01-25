package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.util.UUID;

@RestController
@RequestMapping("/capture-sessions")
public class CaptureSessionController extends PreApiController {

    private final CaptureSessionService captureSessionService;

    @Autowired
    public CaptureSessionController(CaptureSessionService captureSessionService) {
        this.captureSessionService = captureSessionService;
    }

    @GetMapping("/{captureSessionId}")
    @Operation
    public ResponseEntity<CaptureSessionDTO> getCaptureSessionById(@PathVariable UUID captureSessionId) {
        return ResponseEntity.ok(captureSessionService.findById(captureSessionId));
    }

    @PutMapping("/{captureSessionId}")
    @Operation(operationId = "upsertCaptureSession", summary = "Create or Update a Capture Session")
    public ResponseEntity<Void> upsertCaptureSession(
        @PathVariable UUID captureSessionId,
        @Valid @RequestBody CreateCaptureSessionDTO createCaptureSessionDTO
    ) {
        if (!captureSessionId.equals(createCaptureSessionDTO.getId())) {
            throw new PathPayloadMismatchException("id", "createCaptureSessionDTO.id");
        }
        return getUpsertResponse(
            captureSessionService.upsert(createCaptureSessionDTO),
            createCaptureSessionDTO.getId()
        );
    }
}
