package uk.gov.hmcts.reform.entities;


import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class BookingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveBooking() {
        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, false);
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(testCase, Timestamp.valueOf(LocalDateTime.now()), false);
        entityManager.persist(booking);
        entityManager.flush();

        Booking retrievedBooking = entityManager.find(Booking.class, booking.getId());

        assertEquals(booking.getId(), retrievedBooking.getId(), "Id should match");
        assertEquals(booking.getCaseId(), retrievedBooking.getCaseId(), "Case should match");
        assertEquals(booking.getScheduledFor(), retrievedBooking.getScheduledFor(), "Scheduled for should match");
        assertEquals(booking.isDeleted(), retrievedBooking.isDeleted(), "Deleted status should match");
        assertEquals(booking.getCreatedAt(), retrievedBooking.getCreatedAt(), "Created at should match");
        assertEquals(booking.getModifiedAt(), retrievedBooking.getModifiedAt(), "Modified at should match");
    }
}
