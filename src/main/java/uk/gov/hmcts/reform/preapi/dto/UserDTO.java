package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserDTO {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String organisation;
    private CourtDTO court;
    private RoleDTO role;
    private Timestamp createdAt;
    private Timestamp modifiedAt;
    private Timestamp deletedAt;

    public UserDTO(AppAccess access) {
        var user = access.getUser();
        id = user.getId();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        email = user.getEmail();
        phoneNumber = user.getPhone();
        organisation = user.getOrganisation();
        createdAt = user.getCreatedAt();
        modifiedAt = user.getModifiedAt();
        court = new CourtDTO(access.getCourt());
        role = new RoleDTO(access.getRole());
    }
}
