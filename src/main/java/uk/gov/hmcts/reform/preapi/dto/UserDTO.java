package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private List<BaseAppAccessDTO> appAccess;

    @Schema(description = "UserPortalAccess")
    private List<PortalAccessDTO> portalAccess;

    @Schema(description = "UserCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "UserModifiedAt")
    private Timestamp modifiedAt;

    @Schema(description = "UserDeletedAt")
    protected Timestamp deletedAt;

    @Schema(description = "UserTermsAccepted")
    private Map<TermsAndConditionsType, Boolean> termsAccepted;

    public UserDTO(User user, Set<TermsAndConditions> latestTermsAndConditions) {
        super();
        id = user.getId();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        email = user.getEmail();
        alternativeEmail = user.getAlternativeEmail();
        phoneNumber = user.getPhone();
        organisation = user.getOrganisation();
        createdAt = user.getCreatedAt();
        modifiedAt = user.getModifiedAt();
        deletedAt = user.getDeletedAt();
        appAccess = Stream.ofNullable(user.getAppAccess())
            .flatMap(access -> access.stream()
                .filter(a -> a.getDeletedAt() == null)
                .sorted(Comparator.comparing(AppAccess::getLastAccess, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(BaseAppAccessDTO::new))
            .collect(Collectors.toList());
        portalAccess = Stream.ofNullable(user.getPortalAccess())
            .flatMap(access -> access.stream()
                .filter(a -> a.getDeletedAt() == null)
                .map(PortalAccessDTO::new))
            .collect(Collectors.toList());
        if (latestTermsAndConditions != null) {
            termsAccepted = new HashMap<>();
            Arrays.stream(TermsAndConditionsType.values())
                .forEach(type -> {
                    termsAccepted.put(type, latestTermsAndConditions
                        .stream()
                        .filter(t -> t != null && t.getType() == type)
                        .anyMatch(t -> Stream.ofNullable(user.getUserTermsAccepted())
                            .flatMap(Collection::stream)
                            .anyMatch(userAcceptedTsCs -> userAcceptedTsCs.isValid()
                                && userAcceptedTsCs.getTermsAndConditions().getId() == t.getId())));
                });
        } else {
            termsAccepted = new HashMap<>();
            Arrays.stream(TermsAndConditionsType.values()).forEach(type -> {
                termsAccepted.put(type, false);
            });
        }
    }
}
