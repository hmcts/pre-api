package uk.gov.hmcts.reform.preapi.dto.base;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.AppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.PortalAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "BaseUserDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BaseUserDTO {
    // Dummy change to test api version bump
    @Schema(description = "UserId")
    protected UUID id;

    @Schema(description = "UserFirstName")
    protected String firstName;

    @Schema(description = "UserLastName")
    protected String lastName;

    @Schema(description = "UserEmail")
    protected String email;

    @Schema(description = "UserPhoneNumber")
    protected String phoneNumber;

    @Schema(description = "UserOrganisation")
    protected String organisation;

    @Schema(description = "active")
    protected Boolean active;

    @Schema(description = "UserAppAccess")
    protected Set<AppAccessDTO> appAccess;

    @Schema(description = "UserPortalAccess")
    protected Set<PortalAccessDTO> portalAccess;

    public BaseUserDTO(User user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.phoneNumber = user.getPhone();
        this.organisation = user.getOrganisation();
        this.active = user.getDeletedAt() != null;
        this.appAccess = Stream.ofNullable(user.getAppAccess())
                               .flatMap(access -> access.stream()
                                                        .filter(a -> a.getDeletedAt() == null)
                                                        .map(AppAccessDTO::new))
                               .collect(Collectors.toSet());
        this.portalAccess = Stream.ofNullable(user.getPortalAccess())
                                  .flatMap(access -> access.stream()
                                                           .filter(a -> a.getDeletedAt() == null)
                                                           .map(PortalAccessDTO::new))
                                  .collect(Collectors.toSet());
    }
}
