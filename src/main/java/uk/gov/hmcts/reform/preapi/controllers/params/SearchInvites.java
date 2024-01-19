package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;

@Data
public class SearchInvites {
    private String firstName;
    private String lastName;
    private String email;
    private String organisation;
}
