package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

@Data
public class SearchInvites {
    private String firstName;
    private String lastName;
    private String email;
    private String organisation;
    private AccessStatus status;

    public String getFirstName() {
        return firstName != null && !firstName.isEmpty() ? firstName : null;
    }

    public String getLastName() {
        return lastName != null && !lastName.isEmpty() ? lastName : null;
    }

    public String getEmail() {
        return email != null && !email.isEmpty() ? email : null;
    }

    public String getOrganisation() {
        return organisation != null && !organisation.isEmpty() ? organisation : null;
    }
}
