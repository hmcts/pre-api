package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;

import java.util.Set;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "CreateUserDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateUserDTO extends BaseUserDTO {

    @Schema(description = "UserAppAccess")
    @NotNull
    @Valid
    private Set<CreateAppAccessDTO> appAccess;
}

