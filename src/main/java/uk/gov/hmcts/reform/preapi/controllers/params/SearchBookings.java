package uk.gov.hmcts.reform.preapi.controllers.params;

import java.util.Map;
import java.util.UUID;

public record SearchBookings(UUID caseId, String caseReference) {
    public static SearchBookings from(Map<String, String> allParams) {
        return new SearchBookings(
            allParams.containsKey("caseId") ? UUID.fromString(allParams.get("caseId")) : null,
            allParams.getOrDefault("caseReference", null)
        );
    }
}
