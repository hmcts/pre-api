package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.model.Booking;

import java.util.UUID;

import static org.springframework.http.ResponseEntity.created;

@RestController
@RequestMapping(path = "/cases/{caseId}/bookings")
public class BookingController {

    @PutMapping("/{bookingId}")
    public ResponseEntity<Booking> create(@PathVariable UUID caseId, @PathVariable UUID bookingId, @RequestBody Booking booking) {

        // @todo check case exists else 404

        if (!caseId.equals(booking.getCaseId())) {
            throw new PathPayloadMismatchException("caseId", "booking.caseId");
        }
        if (!bookingId.equals(booking.getId())) {
            throw new PathPayloadMismatchException("bookingId", "booking.id");
        }

        return created(ServletUriComponentsBuilder.fromCurrentRequest().path("").buildAndExpand(booking.getId()).toUri()).build();
    }
}
