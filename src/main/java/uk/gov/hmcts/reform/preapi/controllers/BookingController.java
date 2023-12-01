package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.hmcts.reform.preapi.model.Booking;

import static org.springframework.http.ResponseEntity.created;

@RestController
@RequestMapping(path = "/cases/{caseId}/bookings")
public class BookingController {

    @PutMapping("/{bookingId}")
    public ResponseEntity<Booking> create(@PathVariable String caseId, @PathVariable String bookingId, @RequestBody Booking booking) {

        return created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{bookingId}").buildAndExpand(booking.getId()).toUri()).build();
    }
}
