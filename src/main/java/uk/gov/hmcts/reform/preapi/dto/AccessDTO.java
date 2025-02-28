package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "AccessDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccessDTO {

    @Schema(description = "AccessUser")
    private BaseUserDTO user;

    @Schema(description = "AccessAppAccess")
    private Set<BaseAppAccessDTO> appAccess;

    @Schema(description = "AccessPortalAccess")
    private Set<PortalAccessDTO> portalAccess;

    @Schema(description = "UserTermsAccepted")
    private Map<TermsAndConditionsType, Boolean> termsAccepted;

    public AccessDTO(User entity, Set<TermsAndConditions> latestTermsAndConditions) {
        user = new BaseUserDTO(entity);
        appAccess = Stream.ofNullable(entity.getAppAccess())
            .flatMap(access ->
                access
                    .stream()
                    .filter(a -> !a.isDeleted() && a.isActive())
                    .map(BaseAppAccessDTO::new))
            .collect(Collectors.toSet());
        portalAccess = Stream.ofNullable(entity.getPortalAccess())
            .flatMap(access ->
                access
                    .stream()
                    .filter(a -> !a.isDeleted() && a.getStatus() == AccessStatus.ACTIVE)
                    .map(PortalAccessDTO::new))
            .collect(Collectors.toSet());
        if (latestTermsAndConditions != null) {
            termsAccepted = new HashMap<>();
            Arrays.stream(TermsAndConditionsType.values())
                  .forEach(type -> {
                      termsAccepted.put(type, latestTermsAndConditions
                          .stream()
                          .filter(t -> t != null && t.getType() == type)
                          .anyMatch(t -> Stream.ofNullable(entity.getUserTermsAccepted())
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
