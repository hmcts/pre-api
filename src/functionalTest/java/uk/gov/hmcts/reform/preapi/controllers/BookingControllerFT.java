package uk.gov.hmcts.reform.preapi.controllers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.Room;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

class BookingControllerFT extends FunctionalTestBase {

    private static final String BOOKINGS_ENDPOINT = "/cases/{}/bookings/";

    @Test
    void shouldRetrieveCourtDetail() {
        var court = new Court();
        court.setId(UUID.randomUUID());
        court.setName("Foo Court");
        court.setCourtType(CourtType.CROWN);
        court.setLocationCode("1234");
        var region = new Region();
        region.setName("Foo Region");
        region.setCourts(Set.of(court));
        court.setRegions(Set.of(region));
        var room = new Room();
        room.setName("Foo Room");
        room.setCourts(Set.of(court));
        court.setRooms(Set.of(room));
        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setReference("1234");
        caseEntity.setCourt(court);

        booking.setId(UUID.randomUUID());
        booking.setCaseDTO();
        final var response = doPutRequest(BOOKINGS_ENDPOINT + "1234", null);
        assertThat(response.statusCode()).isEqualTo(OK.value());

        final OldCourt court = response.as(OldCourt.class);
        assertThat(court.getSlug()).isEqualTo(AYLESBURY_MAGISTRATES_COURT_AND_FAMILY_COURT);
    }
}
