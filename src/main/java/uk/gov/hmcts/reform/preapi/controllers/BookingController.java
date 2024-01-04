package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
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
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@RestController
public class BookingController extends PreApiController {

    private final CaseService caseService;
    private final BookingService bookingService;

    @Autowired
    public BookingController(final CaseService caseService, final BookingService bookingService) {
        super();
        this.caseService = caseService;
        this.bookingService = bookingService;
    }

    @GetMapping("/bookings")
    @Operation(operationId = "searchBookings", summary = "Search for Bookings by case reference or case id")
    public ResponseEntity<List<BookingDTO>> search(@RequestParam Map<String,String> params) {
        var searchParams = SearchBookings.from(params);
        return ok(bookingService.searchBy(searchParams.caseId(), searchParams.caseReference()));
    }

    @GetMapping("/cases/{caseId}/bookings")
    @Operation(operationId = "getBookingsByCaseId", summary = "Get all Bookings for a Case")
    public HttpEntity<PagedModel<EntityModel<BookingDTO>>> searchByCaseId(
        @PathVariable UUID caseId,
        Pageable pageable,
        PagedResourcesAssembler<BookingDTO> assembler) {
        validateRequest(caseId);

        final Page<BookingDTO> resultPage = bookingService.findAllByCaseId(caseId, pageable);
        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }
        return ok(assembler.toModel(resultPage));
    }

    @GetMapping("/cases/{caseId}/bookings/{bookingId}")
    @Operation(operationId = "getBookingById", summary = "Get a Booking by Id")
    public ResponseEntity<BookingDTO> get(@PathVariable UUID caseId,
                                          @PathVariable UUID bookingId) {
        validateRequest(caseId);

        return ok(bookingService.findById(bookingId));
    }

    @PutMapping("/cases/{caseId}/bookings/{bookingId}")
    @Operation(operationId = "putBooking", summary = "Create or Update a Booking")
    public ResponseEntity<Void> upsert(@PathVariable UUID caseId,
                                       @PathVariable UUID bookingId,
                                       @RequestBody CreateBookingDTO createBookingDTO) {
        this.validateRequestWithBody(caseId, bookingId, createBookingDTO);

        return getUpsertResponse(bookingService.upsert(createBookingDTO), createBookingDTO.getId());
    }

    @DeleteMapping("/cases/{caseId}/bookings/{bookingId}")
    @Operation(operationId = "deleteBooking", summary = "Delete a Booking")
    public ResponseEntity<Void> delete(@PathVariable UUID caseId,
                                       @PathVariable UUID bookingId) {
        validateRequest(caseId);
        bookingService.markAsDeleted(bookingId);
        return noContent().build();
    }

    private void validateRequest(UUID caseId) {
        if (caseService.findById(caseId) == null) {
            throw new NotFoundException("CaseDTO " + caseId);
        }
    }

    private void validateRequestWithBody(UUID caseId, UUID bookingId, CreateBookingDTO createBookingDTO) {
        validateRequest(caseId);
        if (!caseId.equals(createBookingDTO.getCaseId())) {
            throw new PathPayloadMismatchException("caseId", "bookingDTO.caseId");
        }
        if (!bookingId.equals(createBookingDTO.getId())) {
            throw new PathPayloadMismatchException("bookingId", "bookingDTO.id");
        }
    }
}
