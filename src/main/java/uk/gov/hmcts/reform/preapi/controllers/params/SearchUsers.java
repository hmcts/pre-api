package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;

import java.util.UUID;

@Data
public class SearchUsers {
    private String firstName;
    private String lastName;
    private String email;
    private String organisation;
    private UUID courtId;
    private UUID roleId;
}
