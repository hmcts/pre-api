package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;

import java.util.UUID;

@Data
public class SearchSharedReport {
    private UUID courtId;
    private UUID bookingId;
    private UUID sharedWithId;
    private String sharedWithEmail;

    public String getSharedWithEmail() {
        return sharedWithEmail != null && !sharedWithEmail.isEmpty() ? sharedWithEmail : null;
    }

}
