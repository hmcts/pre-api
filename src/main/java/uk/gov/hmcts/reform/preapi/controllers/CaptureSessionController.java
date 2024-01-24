package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.util.UUID;

@RestController
@RequestMapping("/capture-sessions")
public class CaptureSessionController {

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

    @DeleteMapping("/{captureSessionId}")
    @Operation(operationId = "deleteCaptureSessionById", summary = "Delete Capture Session by Id")
    public ResponseEntity<Void> deleteCaptureSessionById(@PathVariable UUID captureSessionId) {
        captureSessionService.deleteById(captureSessionId);
        return ResponseEntity.noContent().build();
    }
}
