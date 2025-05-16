package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = Application.class)
@SuppressWarnings("PMD.JUnit5TestShouldBePackagePrivate")
class BookingTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AuditRepository auditRepository;

    @Test
    @Transactional
    public void testSaveAndRetrieveBooking() {
        var booking = setupBooking();
        entityManager.flush();

        Booking retrievedBooking = entityManager.find(Booking.class, booking.getId());

        assertEquals(booking.getId(), retrievedBooking.getId(), "Id should match");
        assertEquals(booking.getCaseId(), retrievedBooking.getCaseId(), "CaseDTO should match");
        assertEquals(booking.getScheduledFor(), retrievedBooking.getScheduledFor(), "Scheduled for should match");
        assertEquals(booking.getDeletedAt(), retrievedBooking.getDeletedAt(), "Deleted at should match");
        assertEquals(booking.getCreatedAt(), retrievedBooking.getCreatedAt(), "Created at should match");
        assertEquals(booking.getModifiedAt(), retrievedBooking.getModifiedAt(), "Modified at should match");
    }

    @Test
    @Transactional
    public void testAuditNoParticipants() {
        var booking = setupBooking();
        entityManager.flush();

        var audit = auditRepository.findAll()
            .stream()
            .filter(e -> e.getTableName().equals("bookings") && e.getTableRecordId().equals(booking.getId()))
            .findFirst();

        assertTrue(audit.isPresent());
        assertTrue(audit.get().getAuditDetails().get("participants").isArray());
        assertTrue(audit.get().getAuditDetails().get("participants").isEmpty());
    }

    @Test
    @Transactional
    public void testAuditWithParticipants() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, now);
        entityManager.persist(testCase);

        var participant1 = HelperFactory.createParticipant(
            testCase,
            ParticipantType.WITNESS,
            "Example",
            "Example",
            null
        );
        participant1.setId(UUID.randomUUID());
        entityManager.persist(participant1);

        var participant2 = HelperFactory.createParticipant(
            testCase,
            ParticipantType.DEFENDANT,
            "Example2",
            "Example2",
            null
        );
        participant2.setId(UUID.randomUUID());
        entityManager.persist(participant2);

        Booking booking = HelperFactory.createBooking(testCase, now, now);
        booking.setId(UUID.randomUUID());
        booking.setParticipants(Set.of(participant1, participant2));
        entityManager.persist(booking);
        entityManager.flush();

        var audit = auditRepository.findAll()
            .stream()
            .filter(e -> e.getTableName().equals("bookings") && e.getTableRecordId().equals(booking.getId()))
            .findFirst();

        assertTrue(audit.isPresent());
        assertTrue(audit.get().getAuditDetails().get("participants").isArray());

        audit.get().getAuditDetails().get("participants")
            .forEach(node -> {
                assertThat(node.asText()).isIn(List.of(
                    participant1.getId().toString(), participant2.getId().toString()));
            });
    }

    private Booking setupBooking() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, now);
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(testCase, now, now);
        booking.setId(UUID.randomUUID());
        entityManager.persist(booking);

        return booking;
    }
}
