package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.model.Booking;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.UUID;

import static org.springframework.http.ResponseEntity.created;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@RestController
@RequestMapping(path = "/cases/{caseId}/bookings")
public class BookingController {

    private final CaseService caseService;
    private final BookingService bookingService;

    @Autowired
    public BookingController(final CaseService caseService, final BookingService bookingService) {
        this.caseService = caseService;
        this.bookingService = bookingService;
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<Booking> get(@PathVariable UUID caseId,
                                       @PathVariable UUID bookingId) {
        validateRequest(caseId);

        return ok(bookingService.findById(bookingId));
    }

    @PutMapping("/{bookingId}")
    public ResponseEntity<Booking> upsert(@PathVariable UUID caseId,
                                          @PathVariable UUID bookingId,
                                          @RequestBody Booking booking) {
        this.validateRequestWithBody(caseId, bookingId, booking);

        var result = bookingService.upsert(booking);
        var location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("")
            .buildAndExpand(booking.getId())
            .toUri();

        if (result == UpsertResult.CREATED) {
            return created(location).build();
        } else if (result == UpsertResult.UPDATED) {
            return status(HttpStatus.NO_CONTENT).location(location).build();
        }
        throw new UnknownServerException("Unexpected result: " + result);
    }

    private void validateRequest(UUID caseId) {
        if (caseService.findById(caseId) == null) {
            throw new NotFoundException("Case " + caseId);
        }
    }

    private void validateRequestWithBody(UUID caseId, UUID bookingId, Booking booking) {
        validateRequest(caseId);
        if (!caseId.equals(booking.getCaseId())) {
            throw new PathPayloadMismatchException("caseId", "booking.caseId");
        }
        if (!bookingId.equals(booking.getId())) {
            throw new PathPayloadMismatchException("bookingId", "booking.id");
        }
    }
}
