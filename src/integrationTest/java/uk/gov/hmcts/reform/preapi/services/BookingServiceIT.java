package uk.gov.hmcts.reform.preapi.services;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = Application.class)
class BookingServiceIT {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BookingService bookingService;

    @Transactional
    @Test
    public void testSearchBookingsNotSuperUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(false);

        var court = HelperFactory.createCourt(CourtType.CROWN, "Foo Court", "1234");
        entityManager.persist(court);

        var region = HelperFactory.createRegion("Foo Region", Set.of(court));
        entityManager.persist(region);

        var room = HelperFactory.createRoom("Foo Room", Set.of(court));
        entityManager.persist(room);

        var caseEntity = HelperFactory.createCase(court, "1234_Alpha", false, null);
        entityManager.persist(caseEntity);

        var participant1 = HelperFactory.createParticipant(caseEntity,
                                                           ParticipantType.WITNESS,
                                                           "John",
                                                           "Smith",
                                                           null);
        var participant2 = HelperFactory.createParticipant(caseEntity,
                                                           ParticipantType.DEFENDANT,
                                                           "Jane",
                                                           "Doe",
                                                           null);
        entityManager.persist(participant1);
        entityManager.persist(participant2);


        var booking1 = HelperFactory.createBooking(caseEntity,
                                                   Timestamp.valueOf("2024-06-28 12:00:00"),
                                                   null,
                                                   Set.of(participant1, participant2));
        var booking2 = HelperFactory.createBooking(caseEntity,
                                                   Timestamp.valueOf("2024-06-29 12:00:00"),
                                                   null,
                                                   Set.of(participant1, participant2));
        entityManager.persist(booking1);
        entityManager.persist(booking2);

        when(mockAuth.getSharedBookings()).thenReturn(List.of(booking1.getId()));
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var findAllSharedWithUser = bookingService.searchBy(null, null, null, Optional.empty(), null, null);
        assertEquals(1, findAllSharedWithUser.toList().size(), "Should find 1 booking");
        assertEquals(booking1.getId(), findAllSharedWithUser.toList().getFirst().getId(), "Should find booking 1");
    }

    @Transactional
    @Test
    public void testSearchBookings() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);

        SecurityContextHolder.getContext().setAuthentication(mockAuth);

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

        var findByCaseResult = bookingService.findAllByCaseId(caseEntity1.getId(), null);
        assertEquals(1, findByCaseResult.toList().size(), "Should find 1 booking");
        assertEquals(booking1.getId(), findByCaseResult.toList().getFirst().getId(), "Should find booking 1");
        var findByCaseResult2 = bookingService.findAllByCaseId(caseEntity2.getId(), null);
        assertEquals(1, findByCaseResult2.toList().size(), "Should find 1 booking");
        assertEquals(booking2.getId(), findByCaseResult2.toList().getFirst().getId(), "Should find booking 2");

        var findByCaseReferenceResult = bookingService.searchBy(null, "1234", null, Optional.empty(), null, null);
        assertEquals(2, findByCaseReferenceResult.getContent().size(), "Should find 2 bookings");
        assertEquals(booking1.getId(), findByCaseReferenceResult.getContent().get(0).getId(), "Should find booking 1");
        assertEquals(booking2.getId(), findByCaseReferenceResult.getContent().get(1).getId(), "Should find booking 2");

        var findByScheduledForResult = bookingService.searchBy(
            null,
            null,
            null,
            Optional.of(Timestamp.from(Instant.parse("2024-06-28T00:00:00.000Z"))),
            null,
            null
        );
        assertEquals(1, findByScheduledForResult.getContent().size(), "Should find 1 bookings");
        assertEquals(
            booking1.getId(),
            findByCaseReferenceResult.getContent().getFirst().getId(),
            "Should find booking 1"
        );

        var findByParticipantResult = bookingService.searchBy(null,
                                                              null,
                                                              null,
                                                              Optional.empty(),
                                                              participant1.getId(),
                                                              null);
        assertEquals(1, findByParticipantResult.getContent().size());
        assertEquals(
            booking1.getId(),
            findByCaseReferenceResult.getContent().getFirst().getId(),
            "Should find booking 1"
        );

        var findByCourtIdResult = bookingService.searchBy(null,
                                                          null,court.getId(),
                                                          Optional.empty(),
                                                          null,
                                                          null);
        assertEquals(1, findByParticipantResult.getContent().size());
        assertEquals(
            booking1.getId(),
            findByCaseReferenceResult.getContent().getFirst().getId(),
            "Should find booking 1"
        );
    }

    @Transactional
    @Test
    void testUndelete() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var court = HelperFactory.createCourt(CourtType.CROWN, "Foo Court", "1234");
        entityManager.persist(court);
        when(mockAuth.getCourtId()).thenReturn(court.getId());

        var region = HelperFactory.createRegion("Foo Region", Set.of(court));
        entityManager.persist(region);

        var room = HelperFactory.createRoom("Foo Room", Set.of(court));
        entityManager.persist(room);

        var caseEntity = HelperFactory.createCase(court, "1234_Alpha", false, null);
        entityManager.persist(caseEntity);

        var participant1 = HelperFactory.createParticipant(caseEntity,
                                                           ParticipantType.WITNESS,
                                                           "John",
                                                           "Smith",
                                                           null);
        var participant2 = HelperFactory.createParticipant(caseEntity,
                                                           ParticipantType.DEFENDANT,
                                                           "Jane",
                                                           "Doe",
                                                           null);
        entityManager.persist(participant1);
        entityManager.persist(participant2);


        var booking1 = HelperFactory.createBooking(caseEntity,
                                                   Timestamp.valueOf("2024-06-28 12:00:00"),
                                                   Timestamp.from(Instant.now()),
                                                   Set.of(participant1, participant2));
        entityManager.persist(booking1);
        entityManager.flush();
        entityManager.refresh(booking1);

        bookingService.undelete(booking1.getId());

        entityManager.flush();
        entityManager.refresh(booking1);

        assertFalse(booking1.isDeleted());

        bookingService.undelete(booking1.getId());
        assertFalse(booking1.isDeleted());

        var randomId = UUID.randomUUID();
        var message = assertThrows(
            NotFoundException.class,
            () -> bookingService.undelete(randomId)
        ).getMessage();

        assertEquals(message, "Not found: Booking: " + randomId);
    }
}
