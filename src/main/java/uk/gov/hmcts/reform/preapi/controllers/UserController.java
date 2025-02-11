package uk.gov.hmcts.reform.preapi.controllers;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchUsers;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.enums.AccessType;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.services.UserService;

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

    @GetMapping("/by-email/{email}")
    @Operation(
        operationId = "getUserAccessByEmail",
        summary = "Get a User's App Access by Email"
    )
    public ResponseEntity<AccessDTO> getUserAccessById(@PathVariable String email) {
        return ResponseEntity.ok(userService.findByEmail(email));
    }

    @GetMapping("/{userId}")
    @Operation(operationId = "getUserById", summary = "Get a User by Id")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }

    @GetMapping
    @Operation(
        operationId = "getUsers",
        summary = "Search for Users by first name, last name, email, organisation, court or role"
    )
    @Parameter(
        name = "name",
        description = "The name of the user to search by",
        schema = @Schema(implementation = String.class)
    )
    @Parameter(
        name = "firstName",
        description = "The first name of the user to search by",
        schema = @Schema(implementation = String.class)
    )
    @Parameter(
        name = "lastName",
        description = "The last name of the user to search by",
        schema = @Schema(implementation = String.class)
    )
    @Parameter(
        name = "email",
        description = "The email of the user to search by",
        example = "example@example.com",
        schema = @Schema(implementation = String.class)
    )
    @Parameter(
        name = "organisation",
        description = "The organisation of the user to search by",
        schema = @Schema(implementation = String.class)
    )
    @Parameter(
        name = "courtId",
        description = "The court id of the user to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "roleId",
        description = "The role id of the user to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "accessType",
        description = "Get Users by their access type",
        schema = @Schema(implementation = AccessType.class)
    )
    @Parameter(
        name = "includeDeleted",
        description = "Include users marked as deleted",
        schema = @Schema(implementation = Boolean.class)
    )
    @Parameter(
        name = "appActive",
        description = "Filter by users with active app access",
        schema = @Schema(implementation = Boolean.class)
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
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<PagedModel<EntityModel<UserDTO>>> getUsers(
        @Parameter(hidden = true) @ModelAttribute SearchUsers params,
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<UserDTO> assembler
    ) {
        var resultPage = userService.findAllBy(
            params.getName(),
            params.getFirstName(),
            params.getLastName(),
            params.getEmail(),
            params.getOrganisation(),
            params.getCourtId(),
            params.getRoleId(),
            params.getAccessType(),
            params.getIncludeDeleted() != null && params.getIncludeDeleted(),
            params.getAppActive(),
            pageable
        );

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));
    }

    @PutMapping("/{userId}")
    @Operation(operationId = "putUser", summary = "Create or Update a User")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1')")
    public ResponseEntity<Void> upsertUser(@PathVariable UUID userId, @RequestBody @Valid CreateUserDTO createUserDTO) {
        if (!userId.equals(createUserDTO.getId())) {
            throw new PathPayloadMismatchException("userId", "createUserDTO.id");
        }

        if (createUserDTO.getAppAccess()
            .stream()
            .map(CreateAppAccessDTO::getUserId)
            .anyMatch(id -> !id.equals(userId))
        ) {
            throw new PathPayloadMismatchException("userId", "createUserDTO.appAccess[].userId");
        }

        return getUpsertResponse(userService.upsert(createUserDTO), userId);
    }

    @DeleteMapping("/{userId}")
    @Operation(operationId = "deleteUser", summary = "Delete a User")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> deleteUserById(@PathVariable UUID userId) {
        userService.deleteById(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/undelete")
    @Operation(operationId = "undeleteUser", summary = "Revert deletion of user")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1')")
    public ResponseEntity<Void> undeleteUser(@PathVariable UUID userId) {
        userService.undelete(userId);
        return ResponseEntity.ok().build();
    }
}
