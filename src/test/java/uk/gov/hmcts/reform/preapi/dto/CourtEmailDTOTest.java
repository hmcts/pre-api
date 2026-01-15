package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CourtEmailDTOTest {

    @DisplayName("Should be able to create from court")
    @Test
    public void testConstructor() {
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "123");
        court.setGroupEmail("random.email@co.uk");
        var dto = new CourtEmailDTO(court);

        assertEquals("Example Court", dto.getName());
        assertEquals("random.email@co.uk", dto.getGroupEmail());
    }

}
