package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

import java.util.HashSet;
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

    public AccessDTO(User entity) {
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
    }
}
