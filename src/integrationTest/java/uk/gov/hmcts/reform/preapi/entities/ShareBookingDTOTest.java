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
class ShareBookingDTOTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveShareBooking() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, new Timestamp(System.currentTimeMillis()));
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(
            testCase,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );
        entityManager.persist(booking);

        User user = HelperFactory.createDefaultTestUser();
        entityManager.persist(user);

        ShareBooking testShareBooking = new ShareBooking();
        testShareBooking.setBooking(booking);
        testShareBooking.setSharedWith(user);
        testShareBooking.setSharedBy(user);
        testShareBooking.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        testShareBooking.setDeletedAt(new Timestamp(System.currentTimeMillis()));
        entityManager.persist(testShareBooking);
        entityManager.flush();

        ShareBooking retrievedShareBooking = entityManager.find(ShareBooking.class, testShareBooking.getId());

        assertEquals(testShareBooking.getId(), retrievedShareBooking.getId(), "Id should match");
        assertEquals(
            testShareBooking.getBooking(),
            retrievedShareBooking.getBooking(),
            "Capture session should match"
        );
        assertEquals(
            testShareBooking.getSharedWith(),
            retrievedShareBooking.getSharedWith(),
            "Shared with should match"
        );
        assertEquals(
            testShareBooking.getSharedBy(),
            retrievedShareBooking.getSharedBy(),
            "Shared by should match"
        );
        assertEquals(
            testShareBooking.getCreatedAt(),
            retrievedShareBooking.getCreatedAt(),
            "Created at should match"
        );
        assertEquals(
            testShareBooking.getDeletedAt(),
            retrievedShareBooking.getDeletedAt(),
            "Deleted at should match"
        );
    }
}
