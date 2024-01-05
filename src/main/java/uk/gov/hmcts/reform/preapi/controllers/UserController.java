package uk.gov.hmcts.reform.preapi.controllers;


import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController extends PreApiController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        super();
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    @Operation(operationId = "getUserById", summary = "Get a User by Id")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }

    @GetMapping
    @Operation(
        operationId = "getUsers",
        summary = "Search for Users by first name, last name, email, organisation, court or role"
    )
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public ResponseEntity<List<UserDTO>> getUsers(
        @RequestParam(required = false) String firstName,
        @RequestParam(required = false) String lastName,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String organisation,
        @RequestParam(required = false) UUID courtId,
        @RequestParam(required = false) UUID roleId
    ) {
        return ResponseEntity.ok(userService.findAllBy(firstName, lastName, email, organisation, courtId, roleId));
    }

    @PutMapping("/{userId}")
    @Operation(operationId = "putUser", summary = "Create or Update a User")
    public ResponseEntity<Void> upsertUser(@PathVariable UUID userId, @RequestBody CreateUserDTO createUserDTO) {
        if (!userId.equals(createUserDTO.getId())) {
            throw new PathPayloadMismatchException("userId", "createUserDTO.userId");
        }

        return getUpsertResponse(userService.upsert(createUserDTO), userId);
    }

    @DeleteMapping("/{userId}")
    @Operation(operationId = "deleteUser", summary = "Delete a User")
    public ResponseEntity<Void> deleteUserById(@PathVariable UUID userId) {
        userService.deleteById(userId);
        return ResponseEntity.ok().build();
    }
}
