package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;

import java.util.UUID;


@Data
public class SearchCases {
    private String reference;
    private UUID courtId;
    private Boolean includeDeleted;

    public String getReference() {
        return reference != null && !reference.isEmpty() ? reference : null;
    }
}
