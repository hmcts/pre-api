package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import uk.gov.hmcts.reform.preapi.enums.AccessType;

import java.util.UUID;

@Data
public class SearchUsers {
    private String name;
    private String email;
    private String organisation;
    private UUID courtId;
    private UUID roleId;
    private AccessType accessType;
    private Boolean includeDeleted;
    private Boolean appActive;

    public String getName() {
        return name != null && !name.isEmpty() ? name : null;
    }

    public String getEmail() {
        return email != null && !email.isEmpty() ? email : null;
    }

    public String getOrganisation() {
        return organisation != null && !organisation.isEmpty() ? organisation : null;
    }
}
