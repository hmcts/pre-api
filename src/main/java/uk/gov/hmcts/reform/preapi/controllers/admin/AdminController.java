package uk.gov.hmcts.reform.preapi.controllers.admin;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.services.admin.AdminService;

import java.util.UUID;

/**
 * Helper endpoints intended for developers to use in troubleshooting/investigating
 * incidents.
 * Your user will need to have the role ROLE_SUPER_USER to access these endpoints.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_SUPER_USER')")
public class AdminController {

    private final AdminService adminService;

    @Autowired
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Endpoint for getting back what type of item a UUID relates to.
     * @param id UUID to search for
     * @return returns a string
     */
    @GetMapping("/{id}")
    @Operation(operationId = "checkUuid", summary = "Check if a UUID exists in the system",
        description = "Checks if a UUID exists in any of the tables: User, Recording, CaptureSession, "
            + "Booking, Case, Court.")
    public ResponseEntity<String> checkUuidExists(@PathVariable UUID id) {
        return ResponseEntity.ok("Uuid relates to a " + adminService.findUuidType(id));
    }
}
