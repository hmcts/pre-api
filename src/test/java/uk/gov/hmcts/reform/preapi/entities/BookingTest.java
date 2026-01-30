package uk.gov.hmcts.reform.preapi.entities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingTest {

    private Case testCase;
    private Participant witnessParticipant1;
    private Participant witnessParticipant2;
    private Participant defendantParticipant1;
    private Participant defendantParticipant2;

    @BeforeEach
    void setUp() {
        testCase = HelperFactory.createCase(
            new Court(),
            "ref1234", true, Timestamp.valueOf(LocalDateTime.now())
        );
        witnessParticipant1 = HelperFactory.createParticipant(
            testCase, ParticipantType.WITNESS, "Suzy", "Beaker", null
        );

        witnessParticipant2 = HelperFactory.createParticipant(
            testCase, ParticipantType.WITNESS, "Jenny", "Jameson", null
        );

        defendantParticipant1 =
            HelperFactory.createParticipant(
                testCase, ParticipantType.DEFENDANT, "Alfie", "Newman", null
            );

        defendantParticipant2 = HelperFactory.createParticipant(
            testCase, ParticipantType.DEFENDANT, "Bob", "Jones", null
        );
    }

    @Test
    void testGetBookingParticipants() {
        Booking booking = HelperFactory.createBooking(testCase, Timestamp.valueOf(LocalDateTime.now()), null);
        booking.setId(UUID.randomUUID());
        booking.setParticipants(Set.of(witnessParticipant1, witnessParticipant2,
                                       defendantParticipant1, defendantParticipant2));

        String witnessName = booking.getWitnessName();
        assertThat(witnessName).isEqualTo("Jenny, Suzy");

        String defendantName = booking.getDefendantName();
        assertThat(defendantName).isEqualTo("Alfie Newman, Bob Jones");
    }

    @Test
    void testGetBookingParticipantsEmptySet() {
        Booking booking = HelperFactory.createBooking(testCase, Timestamp.valueOf(LocalDateTime.now()), null);
        booking.setId(UUID.randomUUID());
        booking.setParticipants(null);

        String witnessName = booking.getWitnessName();
        assertThat(witnessName).isEmpty();

        String defendantName = booking.getDefendantName();
        assertThat(defendantName).isEmpty();
    }

}
