package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;

import java.util.UUID;

@Data
public class SearchRooms {
    private UUID courtId;
}
