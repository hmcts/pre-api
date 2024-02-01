package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.RoleDTO;
import uk.gov.hmcts.reform.preapi.services.RoleService;

import java.util.List;

@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService rolesService;

    @Autowired
    public RoleController(RoleService rolesService) {
        this.rolesService = rolesService;
    }

    @GetMapping
    @Operation(
        operationId = "getRoles",
        summary = "Get a list of all roles"
    )
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<List<RoleDTO>> getRoles() {
        return ResponseEntity.ok(rolesService.getAllRoles());
    }
}
