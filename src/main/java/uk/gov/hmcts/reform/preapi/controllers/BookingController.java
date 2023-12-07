package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.UUID;

import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping(path = "/cases/{caseId}/bookings")
public class BookingController extends PreApiController {

    private final CaseService caseService;
    private final BookingService bookingService;

    @Autowired
    public BookingController(final CaseService caseService, final BookingService bookingService) {
        super();
        this.caseService = caseService;
        this.bookingService = bookingService;
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingDTO> get(@PathVariable UUID caseId,
                                          @PathVariable UUID bookingId) {
        validateRequest(caseId);

        return ok(bookingService.findById(bookingId));
    }

    @PutMapping("/{bookingId}")
    public ResponseEntity<Void> upsert(@PathVariable UUID caseId,
                                       @PathVariable UUID bookingId,
                                       @RequestBody CreateBookingDTO createBookingDTO) {
        this.validateRequestWithBody(caseId, bookingId, createBookingDTO);

        return getUpsertResponse(bookingService.upsert(createBookingDTO), createBookingDTO.getId());
    }

    @DeleteMapping("/{bookingId}")
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
