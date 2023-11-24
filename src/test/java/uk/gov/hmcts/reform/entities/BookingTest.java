package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;

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

@SpringBootTest(classes = Application.class)
public class BookingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveBooking() {
        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, false);
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(testCase, new Date(System.currentTimeMillis()), false);
        entityManager.persist(booking);
        entityManager.flush();

        Booking retrievedBooking = entityManager.find(Booking.class, booking.getId());

        assertEquals(booking.getId(), retrievedBooking.getId());
        assertEquals(booking.getCaseId(), retrievedBooking.getCaseId());
        assertEquals(booking.getDate(), retrievedBooking.getDate());
        assertEquals(booking.isDeleted(), retrievedBooking.isDeleted());
        assertEquals(booking.getCreatedOn(), retrievedBooking.getCreatedOn());
        assertEquals(booking.getModifiedOn(), retrievedBooking.getModifiedOn());
    }
}
