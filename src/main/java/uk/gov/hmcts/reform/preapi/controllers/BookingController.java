package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.data.web.SortDefault;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
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
import uk.gov.hmcts.reform.preapi.controllers.params.SearchBookings;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/bookings")
public class BookingController extends PreApiController {

    private final BookingService bookingService;

    private final ShareBookingService shareBookingService;

    @Autowired
    public BookingController(final BookingService bookingService, final ShareBookingService shareBookingService) {
        super();
        this.bookingService = bookingService;
        this.shareBookingService = shareBookingService;
    }

    @GetMapping
    @Operation(
        operationId = "searchBookings",
        summary = "Search All Bookings using Case Id, Case Ref, or Scheduled For")
    @Parameter(
        name = "caseId",
        description = "The Case Id to search for",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "caseReference",
        description = "The Case Reference to search for",
        schema = @Schema(implementation = String.class),
        example = "1234567890123456"
    )
    @Parameter(
        name = "scheduledFor",
        description = "The Date the Booking is scheduled for",
        schema = @Schema(implementation = LocalDate.class, format = "date"),
        example = "2024-04-27"
    )
    @Parameter(
        name = "participantId",
        description = "The Participant Id to search for",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "courtId",
        description = "The Court Id to search for",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "hasRecordings",
        description = "If the booking has any recordings",
        schema = @Schema(implementation = Boolean.class)
    )
    @Parameter(
        name = "captureSessionStatusIn",
        description = "Search bookings with at least one associated capture session with one of the statuses listed",
        schema = @Schema(implementation = List.class)
    )
    @Parameter(
        name = "captureSessionStatusNotIn",
        description = "Bookings where the associated capture sessions do not match status or bookings without sessions",
        schema = @Schema(implementation = List.class)
    )
    @Parameter(
        name = "sort",
        description = "Sort by",
        schema = @Schema(implementation = String.class),
        example = "createdAt,desc"
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
    public HttpEntity<PagedModel<EntityModel<BookingDTO>>> searchByCaseId(
        @Parameter(hidden = true) @ModelAttribute SearchBookings params,
        @SortDefault.SortDefaults(
            @SortDefault(sort = "scheduledFor", direction = Sort.Direction.ASC)
        ) @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<BookingDTO> assembler) {

        final Page<BookingDTO> resultPage = bookingService.searchBy(
            params.getCaseId(),
            params.getCaseReference(),
            params.getCourtId(),
            params.getScheduledFor() != null
                ? Optional.of(Timestamp.valueOf(params.getScheduledFor().atStartOfDay()))
                : Optional.empty(),
            params.getParticipantId(),
            params.getHasRecordings(),
            params.getCaptureSessionStatusIn() == null || params.getCaptureSessionStatusIn().isEmpty()
                ? null
                : params.getCaptureSessionStatusIn(),
            params.getCaptureSessionStatusNotIn() == null || params.getCaptureSessionStatusNotIn().isEmpty()
                ? null
                : params.getCaptureSessionStatusNotIn(),
            pageable
        );
        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }
        return ok(assembler.toModel(resultPage));
    }

    @GetMapping("/{bookingId}")
    @Operation(operationId = "getBookingById", summary = "Get a Booking by Id")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<BookingDTO> get(@PathVariable UUID bookingId) {
        return ok(bookingService.findById(bookingId));
    }

    @PutMapping("/{bookingId}")
    @Operation(operationId = "putBooking", summary = "Create or Update a Booking")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> upsert(@PathVariable UUID bookingId,
                                       @Valid @RequestBody CreateBookingDTO createBookingDTO) {
        this.validateRequestWithBody(bookingId, createBookingDTO);
        return getUpsertResponse(bookingService.upsert(createBookingDTO), createBookingDTO.getId());
    }

    @DeleteMapping("/{bookingId}")
    @Operation(operationId = "deleteBooking", summary = "Delete a Booking")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> delete(@PathVariable UUID bookingId) {
        bookingService.markAsDeleted(bookingId);
        return noContent().build();
    }

    @PutMapping("/{bookingId}/share")
    @Operation(operationId = "shareBookingById", summary = "Share a Booking")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> shareBookingById(
        @PathVariable UUID bookingId,
        @RequestBody CreateShareBookingDTO createShareBookingDTO
    ) {
        if (!bookingId.equals(createShareBookingDTO.getBookingId())) {
            throw new PathPayloadMismatchException("bookingId", "shareBookingDTO.bookingId");
        }

        if (createShareBookingDTO.getSharedByUser() == null) {
            createShareBookingDTO.setSharedByUser(((UserAuthentication) SecurityContextHolder
                .getContext().getAuthentication()).getUserId());
        }

        return getUpsertResponse(shareBookingService
                                     .shareBookingById(createShareBookingDTO), createShareBookingDTO.getId());
    }

    @DeleteMapping("/{bookingId}/share/{shareId}")
    @Operation(operationId = "deleteShareBookingById")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> deleteShareBookingById(@PathVariable UUID bookingId, @PathVariable UUID shareId) {
        shareBookingService.deleteShareBookingById(bookingId, shareId);
        return noContent().build();
    }

    @GetMapping("/{bookingId}/share")
    @Operation(operationId = "getSharedBookingLogs")
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
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public HttpEntity<PagedModel<EntityModel<ShareBookingDTO>>> getSharedBookingLogs(
        @PathVariable UUID bookingId,
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<ShareBookingDTO> assembler
    ) {
        Page<ShareBookingDTO> resultPage = shareBookingService.getShareLogsForBooking(bookingId, pageable);

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }
        return ok(assembler.toModel(resultPage));
    }

    @PostMapping("/{bookingId}/undelete")
    @Operation(operationId = "undeleteBooking", summary = "Revert deletion of a booking")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1')")
    public ResponseEntity<Void> undeleteBooking(@PathVariable UUID bookingId) {
        bookingService.undelete(bookingId);
        return ok().build();
    }


    private void validateRequestWithBody(UUID bookingId, CreateBookingDTO createBookingDTO) {
        if (!bookingId.equals(createBookingDTO.getId())) {
            throw new PathPayloadMismatchException("bookingId", "bookingDTO.id");
        }
    }
}
