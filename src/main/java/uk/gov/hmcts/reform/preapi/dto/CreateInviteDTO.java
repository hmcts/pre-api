package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateInviteDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateInviteDTO {
    @Schema (description = "InviteUserId")
    protected UUID userId;

    @Schema(description = "InviteFirstName")
    protected String firstName;

    @Schema(description = "InviteLastName")
    protected String lastName;

    @Schema(description = "InviteEmail")
    protected String email;

    @Schema(description = "InviteOrganisation")
    protected String organisation;

    @Schema(description = "InvitePhone")
    protected String phone;

    @Schema(description = "InviteCode")
    protected String code;
}

