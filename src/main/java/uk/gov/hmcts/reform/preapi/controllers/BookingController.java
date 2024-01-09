package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchBookings;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.services.BookingService;

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@RestController
public class BookingController extends PreApiController {

    private final BookingService bookingService;

    @Autowired
    public BookingController(final BookingService bookingService) {
        super();
        this.bookingService = bookingService;
    }

    @GetMapping("/bookings")
    @Operation(operationId = "getBookingsByCaseId", summary = "Search All Bookings using Case Id or Case Ref")
    @Parameter(
        name = "caseId",
        description = "The Case Id to search for",
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "caseReference",
        description = "The Case Reference to search for",
        example = "1234567890123456"
    )
    public HttpEntity<PagedModel<EntityModel<BookingDTO>>> searchByCaseId(
        @RequestParam Map<String,String> params,
        Pageable pageable,
        PagedResourcesAssembler<BookingDTO> assembler) {

        var searchParams = SearchBookings.from(params);

        final Page<BookingDTO> resultPage = bookingService.searchBy(
            searchParams.caseId(),
            searchParams.caseReference(),
            pageable
        );
        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }
        return ok(assembler.toModel(resultPage));
    }

    @GetMapping("/bookings/{bookingId}")
    @Operation(operationId = "getBookingById", summary = "Get a Booking by Id")
    public ResponseEntity<BookingDTO> get(@PathVariable UUID bookingId) {

        return ok(bookingService.findById(bookingId));
    }

    @PutMapping("/bookings/{bookingId}")
    @Operation(operationId = "putBooking", summary = "Create or Update a Booking")
    public ResponseEntity<Void> upsert(@PathVariable UUID bookingId,
                                       @RequestBody CreateBookingDTO createBookingDTO) {
        this.validateRequestWithBody(bookingId, createBookingDTO);

        return getUpsertResponse(bookingService.upsert(createBookingDTO), createBookingDTO.getId());
    }

    @DeleteMapping("/bookings/{bookingId}")
    @Operation(operationId = "deleteBooking", summary = "Delete a Booking")
    public ResponseEntity<Void> delete(@PathVariable UUID bookingId) {
        bookingService.markAsDeleted(bookingId);
        return noContent().build();
    }

    @PutMapping("/{bookingId}/share")
    @Operation(operationId = "shareBookingById", summary = "Share a Booking")
    public ResponseEntity<Void> shareBookingById(
        @PathVariable UUID bookingId,
        @RequestBody ShareBookingDTO shareBookingDTO
    ) {
        // TODO Ensure user has access to share the recording
        if (!bookingId.equals(shareBookingDTO.getBookingId())) {
            throw new PathPayloadMismatchException("bookingId", "shareBookingDTO.bookingId");
        }

        return getUpsertResponse(bookingService.shareBookingById(shareBookingDTO), shareBookingDTO.getId());
    }

    private void validateRequestWithBody(UUID bookingId, CreateBookingDTO createBookingDTO) {
        if (!bookingId.equals(createBookingDTO.getId())) {
            throw new PathPayloadMismatchException("bookingId", "bookingDTO.id");
        }
    }
}
