package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.sql.Timestamp;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "UserDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserDTO extends BaseUserDTO {
    @Schema(description = "UserAppAccess")
    private Set<BaseAppAccessDTO> appAccess;

    @Schema(description = "UserPortalAccess")
    private Set<PortalAccessDTO> portalAccess;

    @Schema(description = "UserCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "UserModifiedAt")
    private Timestamp modifiedAt;

    @Schema(description = "UserDeletedAt")
    protected Timestamp deletedAt;

    public UserDTO(User user) {
        super();
        id = user.getId();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        email = user.getEmail();
        phoneNumber = user.getPhone();
        organisation = user.getOrganisation();
        createdAt = user.getCreatedAt();
        modifiedAt = user.getModifiedAt();
        deletedAt = user.getDeletedAt();
        appAccess = Stream.ofNullable(user.getAppAccess())
            .flatMap(access -> access.stream()
                .filter(a -> a.getDeletedAt() == null)
                .map(BaseAppAccessDTO::new))
            .collect(Collectors.toSet());
        portalAccess = Stream.ofNullable(user.getPortalAccess())
            .flatMap(access -> access.stream()
                .filter(a -> a.getDeletedAt() == null)
                .map(PortalAccessDTO::new))
            .collect(Collectors.toSet());
    }
}
