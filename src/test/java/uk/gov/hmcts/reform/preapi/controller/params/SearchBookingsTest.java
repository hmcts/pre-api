package uk.gov.hmcts.reform.preapi.controller.params;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchBookings;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchBookingsTest {
    @DisplayName("Should not error when empty Map provided")
    @Test
    void emptyMap() {
        var empty = new HashMap<String, String>();
        var searchBookings = SearchBookings.from(empty);
        assertThat(searchBookings.caseId()).isNull();
    }
}
