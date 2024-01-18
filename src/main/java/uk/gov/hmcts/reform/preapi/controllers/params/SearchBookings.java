package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;

import java.util.Date;
import java.util.UUID;

@Data
public class SearchBookings {
    private UUID caseId;
    private String caseReference;
    private Date scheduledFor;
}
