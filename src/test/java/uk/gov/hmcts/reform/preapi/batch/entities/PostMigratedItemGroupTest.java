package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostMigratedItemGroupTest {
    @Test
    void shouldReturnStringWithNullLists() {
        PostMigratedItemGroup group = PostMigratedItemGroup.builder()
            .shareBookings(null)
            .invites(null)
            .build();

        String expected = "PostMigratedItemGroup{shareBookings=null, invites=null}";
        assertEquals(expected, group.toString());
    }

    @Test
    void shouldReturnStringWithEmptyLists() {
        PostMigratedItemGroup group = PostMigratedItemGroup.builder()
            .shareBookings(Collections.emptyList())
            .invites(Collections.emptyList())
            .build();

        String expected = "PostMigratedItemGroup{shareBookings=[], invites=[]}";
        assertEquals(expected, group.toString());
    }

    @Test
    void shouldReturnStringWithPopulatedShareBookingsList() {
        CreateShareBookingDTO shareBooking = new CreateShareBookingDTO();
        PostMigratedItemGroup group = PostMigratedItemGroup.builder()
            .shareBookings(List.of(shareBooking))
            .invites(null)
            .build();

        String expected = "PostMigratedItemGroup{shareBookings=["
            + shareBooking
            + "], invites=null}";
        assertEquals(expected, group.toString());
    }

    @Test
    void shouldReturnStringWithPopulatedInvitesList() {
        CreateInviteDTO invite = new CreateInviteDTO();
        PostMigratedItemGroup group = PostMigratedItemGroup.builder()
            .shareBookings(null)
            .invites(List.of(invite))
            .build();

        String expected = "PostMigratedItemGroup{shareBookings=null, invites=["
            + invite
            + "]}";
        assertEquals(expected, group.toString());
    }

    @Test
    void shouldReturnStringWithPopulatedLists() {
        CreateShareBookingDTO shareBooking = new CreateShareBookingDTO();
        CreateInviteDTO invite = new CreateInviteDTO();
        PostMigratedItemGroup group = PostMigratedItemGroup.builder()
            .shareBookings(List.of(shareBooking))
            .invites(List.of(invite))
            .build();

        String expected =
            "PostMigratedItemGroup{shareBookings=["
                + shareBooking
                + "], invites=["
                + invite
                + "]}";
        assertEquals(expected, group.toString());
    }
}
