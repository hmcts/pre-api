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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchInvites;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.InviteDTO;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.services.InviteService;

import java.util.UUID;

@RestController
@RequestMapping("/invites")
public class InviteController extends PreApiController {

    private final InviteService inviteService;

    @Autowired
    public InviteController(InviteService inviteService) {
        super();
        this.inviteService = inviteService;
    }

    @GetMapping
    @Operation(
        operationId = "searchInvites",
        summary = "Search for Invites by first name, last name, email or organisation"
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
        name = "status",
        description = "The access status of the user to search by",
        schema = @Schema(implementation = AccessStatus.class)
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
    public HttpEntity<PagedModel<EntityModel<InviteDTO>>> getInvites(
        @Parameter(hidden = true) @ModelAttribute() SearchInvites params,
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<InviteDTO> assembler
    ) {
        Page<InviteDTO> resultPage = inviteService.findAllBy(
            params.getFirstName(),
            params.getLastName(),
            params.getEmail(),
            params.getOrganisation(),
            params.getStatus(),
            pageable
        );

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));
    }

    @GetMapping("/{userId}")
    @Operation(operationId = "getInviteById", summary = "Get an invite by user id")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<InviteDTO> getInviteByUserId(@PathVariable(name = "userId") UUID userId) {
        return ResponseEntity.ok(inviteService.findByUserId(userId));
    }

    @PutMapping("/{userId}")
    @Operation(operationId = "putInvite", summary = "Create a portal access user and invite them")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> upsertInvite(@PathVariable UUID userId,
                                             @Valid @RequestBody CreateInviteDTO createInviteDTO) {
        if (!userId.equals(createInviteDTO.getUserId())) {
            throw new PathPayloadMismatchException("userId", "createInviteDTO.userId");
        }

        return getUpsertResponse(inviteService.upsert(createInviteDTO), createInviteDTO.getUserId());
    }

    @DeleteMapping("/{userId}")
    @Operation(operationId = "deleteInvite", summary = "Delete the user with invitation sent status")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> deleteInvite(@PathVariable UUID userId) {
        inviteService.deleteByUserId(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/redeem")
    @Operation(operationId = "redeemInvite", summary = "Redeem an invite")
    @Parameter(
        name = "email",
        description = "The email of the user to redeem the invite for",
        example = "example@example.com",
        schema = @Schema(implementation = String.class)
    )
    public ResponseEntity<Void> redeemInvite(@RequestParam String email) {
        return getUpsertResponse(inviteService.redeemInvite(email), null);
    }
}
