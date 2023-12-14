package uk.gov.hmcts.reform.preapi.services;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class BookingServiceIT {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BookingService bookingService;

    @Transactional
    @Test
    public void testSearchBookings() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Foo Court", "1234");
        entityManager.persist(court);

        var region = HelperFactory.createRegion("Foo Region", Set.of(court));
        entityManager.persist(region);

        var room = HelperFactory.createRoom("Foo Room", Set.of(court));
        entityManager.persist(room);

        var caseEntity1 = HelperFactory.createCase(court, "1234_Alpha", false, null);
        var caseEntity2 = HelperFactory.createCase(court, "1234_Beta", false, null);
        entityManager.persist(caseEntity1);
        entityManager.persist(caseEntity2);

        var participant1 = HelperFactory.createParticipant(caseEntity1,
                                                           ParticipantType.WITNESS,
                                                           "John",
                                                           "Smith",
                                                           null);
        var participant2 = HelperFactory.createParticipant(caseEntity1,
                                                           ParticipantType.DEFENDANT,
                                                           "Jane",
                                                           "Doe",
                                                           null);
        var participant3 = HelperFactory.createParticipant(caseEntity2,
                                                           ParticipantType.WITNESS,
                                                           "Sponge",
                                                           "Bob",
                                                           null);
        var participant4 = HelperFactory.createParticipant(caseEntity2,
                                                           ParticipantType.DEFENDANT,
                                                           "Sandy",
                                                           "Cheeks",
                                                           null);
        entityManager.persist(participant1);
        entityManager.persist(participant2);
        entityManager.persist(participant3);
        entityManager.persist(participant4);

        var booking1 = HelperFactory.createBooking(caseEntity1,
                                                   Timestamp.valueOf("2024-06-28 12:00:00"),
                                                   null,
                                                   Set.of(participant1, participant2));
        var booking2 = HelperFactory.createBooking(caseEntity2,
                                                   Timestamp.valueOf("2024-06-29 12:00:00"),
                                                   null,
                                                   Set.of(participant3, participant4));
        entityManager.persist(booking1);
        entityManager.persist(booking2);

        var findByCaseResult = bookingService.findAllByCaseId(caseEntity1.getId());
        assertEquals(1, findByCaseResult.size(), "Should find 1 booking");
        assertEquals(booking1.getId(), findByCaseResult.get(0).getId(), "Should find booking 1");
        var findByCaseResult2 = bookingService.findAllByCaseId(caseEntity2.getId());
        assertEquals(1, findByCaseResult2.size(), "Should find 1 booking");
        assertEquals(booking2.getId(), findByCaseResult2.get(0).getId(), "Should find booking 2");

        var findByCaseReferenceResult = bookingService.searchBy("1234");
        assertEquals(2, findByCaseReferenceResult.size(), "Should find 2 bookings");
        assertEquals(booking1.getId(), findByCaseReferenceResult.get(0).getId(), "Should find booking 1");
        assertEquals(booking2.getId(), findByCaseReferenceResult.get(1).getId(), "Should find booking 2");
    }
}
