package uk.gov.hmcts.reform.preapi.entities;


import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class CreateBookingDTOTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveBooking() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, now);
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(testCase, now, now);
        entityManager.persist(booking);
        entityManager.flush();

        Booking retrievedBooking = entityManager.find(Booking.class, booking.getId());

        assertEquals(booking.getId(), retrievedBooking.getId(), "Id should match");
        assertEquals(booking.getCaseId(), retrievedBooking.getCaseId(), "CaseDTO should match");
        assertEquals(booking.getScheduledFor(), retrievedBooking.getScheduledFor(), "Scheduled for should match");
        assertEquals(booking.getDeletedAt(), retrievedBooking.getDeletedAt(), "Deleted at should match");
        assertEquals(booking.getCreatedAt(), retrievedBooking.getCreatedAt(), "Created at should match");
        assertEquals(booking.getModifiedAt(), retrievedBooking.getModifiedAt(), "Modified at should match");
    }
}
