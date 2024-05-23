package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CourtDTOTest {
    @DisplayName("CourtDTO.regions should be sorted by region name")
    @Test
    public void testRegionSorting() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "123");
        court.setRegions(Set.of(
            HelperFactory.createRegion("Region BBB", Set.of(court)),
            HelperFactory.createRegion("Region CCC", Set.of(court)),
            HelperFactory.createRegion("Region AAA", Set.of(court))
        ));
        var dto = new CourtDTO(court);

        var regions = dto.getRegions();
        assertEquals("Region AAA", regions.get(0).getName());
        assertEquals("Region BBB", regions.get(1).getName());
        assertEquals("Region CCC", regions.get(2).getName());
    }

    @DisplayName("CourtDTO.rooms should be sorted by room name")
    @Test
    public void testRoomSorting() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "123");
        court.setRooms(Set.of(
            HelperFactory.createRoom("Courtroom B", Set.of(court)),
            HelperFactory.createRoom("Courtroom C", Set.of(court)),
            HelperFactory.createRoom("Courtroom A", Set.of(court))
        ));
        var dto = new CourtDTO(court);

        var rooms = dto.getRooms();
        assertEquals("Courtroom A", rooms.get(0).getName());
        assertEquals("Courtroom B", rooms.get(1).getName());
        assertEquals("Courtroom C", rooms.get(2).getName());
    }
}
