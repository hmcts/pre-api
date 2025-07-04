package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingServiceIT extends IntegrationTestBase {
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

        var findAllSharedWithUser = bookingService.searchBy(
            null,
            null,
            null,
            Optional.empty(),
            null,
            null,
            null,
            null,
            Pageable.unpaged(Sort.by(Sort.Order.asc("scheduledFor")))
        );
        assertEquals(1, findAllSharedWithUser.toList().size(), "Should find 1 booking");
        assertEquals(booking1.getId(), findAllSharedWithUser.toList().getFirst().getId(), "Should find booking 1");
    }

    @Transactional
    @Test
    public void testSearchBookings() {
        mockAdminUser();

        var court1 = HelperFactory.createCourt(CourtType.CROWN, "Foo Court", "1234");
        var court2 = HelperFactory.createCourt(CourtType.CROWN, "Foo Court 2", "5678");
        entityManager.persist(court1);
        entityManager.persist(court2);

        var region = HelperFactory.createRegion("Foo Region", Set.of(court1, court2));
        entityManager.persist(region);

        var caseEntity1 = HelperFactory.createCase(court1, "1234_Alpha", false, null);
        var caseEntity2 = HelperFactory.createCase(court2, "1234_Beta", false, null);
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

        var captureSession = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(
            captureSession,
            null,
            1,
            "example",
            null
        );
        entityManager.persist(recording);

        var findByCaseResult = bookingService.findAllByCaseId(caseEntity1.getId(), null);
        assertEquals(1, findByCaseResult.toList().size(), "Should find 1 booking");
        assertEquals(booking1.getId(), findByCaseResult.toList().getFirst().getId(), "Should find booking 1");
        var findByCaseResult2 = bookingService.findAllByCaseId(caseEntity2.getId(), null);
        assertEquals(1, findByCaseResult2.toList().size(), "Should find 1 booking");
        assertEquals(booking2.getId(), findByCaseResult2.toList().getFirst().getId(), "Should find booking 2");

        var findByCaseReferenceResult = bookingService.searchBy(
            null,
            "1234",
            null,
            Optional.empty(),
            null,
            null,
            null,
            null,
            Pageable.unpaged(Sort.by(Sort.Order.asc("scheduledFor")))
        );
        assertEquals(2, findByCaseReferenceResult.getContent().size(), "Should find 2 bookings");
        assertEquals(booking1.getId(), findByCaseReferenceResult.getContent().get(0).getId(), "Should find booking 1");
        assertEquals(booking2.getId(), findByCaseReferenceResult.getContent().get(1).getId(), "Should find booking 2");

        var findByScheduledForResult = bookingService.searchBy(
            null,
            null,
            null,
            Optional.of(Timestamp.from(Instant.parse("2024-06-28T00:00:00.000Z"))),
            null,
            null,
            null,
            null,
            Pageable.unpaged(Sort.by(Sort.Order.asc("scheduledFor")))
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
                                                              null,
                                                              null,
                                                              null,
                                                              null);
        assertEquals(1, findByParticipantResult.getContent().size());
        assertEquals(
            booking1.getId(),
            findByCaseReferenceResult.getContent().getFirst().getId(),
            "Should find booking 1"
        );

        var findByCourtIdResult = bookingService.searchBy(null,
                                                          null,
                                                          court1.getId(),
                                                          Optional.empty(),
                                                          null,
                                                          null,
                                                          null,
                                                          null,
                                                          null).toList();
        assertEquals(1, findByCourtIdResult.size());
        assertEquals(
            booking1.getId(),
            findByCourtIdResult.getFirst().getId(),
            "Should find booking 1"
        );

        var findByHasRecordingsFalse = bookingService.searchBy(
            null,
            null,
            null,
            Optional.empty(),
            null,
            false,
            null,
            null,
            Pageable.unpaged(Sort.by(Sort.Order.asc("scheduledFor")))
        ).toList();
        assertEquals(findByHasRecordingsFalse.size(), 1);
        assertEquals(
            booking1.getId(),
            findByHasRecordingsFalse.getFirst().getId(),
            "Should find booking 1"
        );
        var findByHasRecordingsTrue = bookingService.searchBy(
            null,
            null,
            null,
            Optional.empty(),
            null,
            true,
            null,
            null,
            Pageable.unpaged(Sort.by(Sort.Order.asc("scheduledFor")))
        ).toList();
        assertEquals(findByHasRecordingsTrue.size(), 1);
        assertEquals(
            booking2.getId(),
            findByHasRecordingsTrue.getFirst().getId(),
            "Should find booking 2"
        );
    }

    @Test
    @Transactional
    void testSearchByCaptureSessionStatus() {
        var mockAuth = mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Foo Court", "1234");
        entityManager.persist(court);
        when(mockAuth.getCourtId()).thenReturn(court.getId());

        var region = HelperFactory.createRegion("Foo Region", Set.of(court));
        entityManager.persist(region);

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
                                                   Timestamp.from(Instant.now()),
                                                   null,
                                                   Set.of(participant1, participant2));


        var booking2 = HelperFactory.createBooking(caseEntity,
                                                   Timestamp.from(Instant.now()),
                                                   null,
                                                   Set.of(participant1, participant2));

        entityManager.persist(booking1);
        entityManager.persist(booking2);

        var captureSession1 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.STANDBY,
            null
        );

        var captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.PROCESSING,
            null
        );
        entityManager.persist(captureSession1);
        entityManager.persist(captureSession2);

        var findOnlyWithStandby = bookingService.searchBy(null,
                                                          null,
                                                          null,
                                                          Optional.empty(),
                                                          null,
                                                          null,
                                                          List.of(RecordingStatus.STANDBY),
                                                          null,
                                                          null).getContent();
        assertThat(findOnlyWithStandby).hasSize(1);
        assertThat(findOnlyWithStandby.getFirst().getId()).isEqualTo(booking1.getId());

        var findOnlyWithProcessing = bookingService.searchBy(null,
                                                          null,
                                                          null,
                                                          Optional.empty(),
                                                          null,
                                                          null,
                                                          List.of(RecordingStatus.PROCESSING),
                                                          null,
                                                          null).getContent();
        assertThat(findOnlyWithProcessing).hasSize(1);
        assertThat(findOnlyWithProcessing.getFirst().getId()).isEqualTo(booking2.getId());

        var findEitherStandbyOrProcessing = bookingService.searchBy(null,
                                                             null,
                                                             null,
                                                             Optional.empty(),
                                                             null,
                                                             null,
                                                             List.of(
                                                                 RecordingStatus.STANDBY,
                                                                 RecordingStatus.PROCESSING
                                                             ),
                                                             null,
                                                             null).getContent();
        assertThat(findEitherStandbyOrProcessing).hasSize(2);
        assertThat(findEitherStandbyOrProcessing.stream().map(BookingDTO::getId).anyMatch(b ->  b == booking1.getId()))
            .isTrue();
        assertThat(findEitherStandbyOrProcessing.stream().map(BookingDTO::getId).anyMatch(b ->  b == booking2.getId()))
            .isTrue();

        var findOnlyWithNoRecording = bookingService.searchBy(null,
                                                             null,
                                                             null,
                                                             Optional.empty(),
                                                             null,
                                                             null,
                                                             List.of(RecordingStatus.NO_RECORDING),
                                                             null,
                                                             null).getContent();
        assertThat(findOnlyWithNoRecording).hasSize(0);

        var findOnlyNotStandby = bookingService.searchBy(null,
                                                         null,
                                                         null,
                                                         Optional.empty(),
                                                         null,
                                                         null,
                                                         null,
                                                         List.of(RecordingStatus.STANDBY),
                                                         null).getContent();
        assertThat(findOnlyWithProcessing).hasSize(1);
        assertThat(findOnlyWithProcessing.getFirst().getId()).isEqualTo(booking2.getId());

        var findNotEitherStandbyOrProcessing = bookingService.searchBy(null,
                                                                       null,
                                                                       null,
                                                                       Optional.empty(),
                                                                       null,
                                                                       null,
                                                                       null,
                                                                       List.of(
                                                                           RecordingStatus.STANDBY,
                                                                           RecordingStatus.PROCESSING
                                                                       ),
                                                                       null).getContent();
        assertThat(findNotEitherStandbyOrProcessing).hasSize(0);
    }

    @Transactional
    @Test
    void testUndelete() {
        var mockAuth = mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Foo Court", "1234");
        entityManager.persist(court);
        when(mockAuth.getCourtId()).thenReturn(court.getId());

        var region = HelperFactory.createRegion("Foo Region", Set.of(court));
        entityManager.persist(region);

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

    @Test
    @Transactional
    void testFindAllPastBookings() {
        var mockAuth = mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Foo Court", "1234");
        entityManager.persist(court);
        when(mockAuth.getCourtId()).thenReturn(court.getId());

        var region = HelperFactory.createRegion("Foo Region", Set.of(court));
        entityManager.persist(region);

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
                                                   Timestamp.valueOf("2024-06-28 12:00:00"),
                                                   null,
                                                   Set.of(participant1, participant2));

        var captureSession = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        entityManager.persist(booking1);
        entityManager.persist(booking2);
        entityManager.persist(captureSession);
        entityManager.flush();

        List<BookingDTO> bookings = bookingService.findAllPastBookings();

        assertThat(bookings.size()).isEqualTo(1);
        assertThat(bookings.getFirst().getId()).isEqualTo(booking1.getId());
    }
}
