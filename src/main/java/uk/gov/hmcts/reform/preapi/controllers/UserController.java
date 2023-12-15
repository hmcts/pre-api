package uk.gov.hmcts.reform.preapi.controllers;


import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    @Operation(operationId = "getUserById", summary = "Get a User by Id")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }

    @DeleteMapping("/{userId}")
    @Operation(operationId = "deleteUser", summary = "Delete a User")
    public ResponseEntity<Void> deleteUserById(@PathVariable UUID userId) {
        userService.deleteById(userId);
        return ResponseEntity.ok().build();
    }
}
