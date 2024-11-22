package uk.gov.hmcts.reform.preapi.dto.batch;

import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TransformedDataDTO {
    private UUID courtId;
    private String caseReference;
    private Boolean test;
    private Timestamp createdAt;
    private CaseState state;
    private Timestamp closedAt;
    private Timestamp scheduledFor;
    private List<Map<String, String>> participantDetails;
    private List<Map<String, String>> userContacts;
}


