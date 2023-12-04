package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.model.Booking;
import uk.gov.hmcts.reform.preapi.service.CaseService;

import java.util.UUID;

import static org.springframework.http.ResponseEntity.created;

@RestController
@RequestMapping(path = "/cases/{caseId}/bookings")
public class BookingController {

    private final CaseService caseService;

    @Autowired
    public BookingController(final CaseService caseService) {
        this.caseService = caseService;
    }

    @PutMapping("/{bookingId}")
    public ResponseEntity<Booking> create(@PathVariable UUID caseId,
                                          @PathVariable UUID bookingId,
                                          @RequestBody Booking booking) {

        if (caseService.findById(caseId) == null) {
            throw new NotFoundException("Case " + caseId);
        }

        if (!caseId.equals(booking.getCaseId())) {
            throw new PathPayloadMismatchException("caseId", "booking.caseId");
        }
        if (!bookingId.equals(booking.getId())) {
            throw new PathPayloadMismatchException("bookingId", "booking.id");
        }

        return created(ServletUriComponentsBuilder
                           .fromCurrentRequest()
                           .path("")
                           .buildAndExpand(booking.getId())
                           .toUri())
            .build();
    }
}
