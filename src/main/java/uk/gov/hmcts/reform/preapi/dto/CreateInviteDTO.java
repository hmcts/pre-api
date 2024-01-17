package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "CreateInviteDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateInviteDTO{
    @Schema (description = "CreateInviteId")
    private String id;

    @Schema(description = "CreateInviteFirstName")
    private String firstName;

    @Schema(description = "CreateInviteLastName")
    private String lastName;

    @Schema(description = "CreateInviteEmail")
    private String email;

    @Schema(description = "CreateInviteOrganisation")
    private String organisation;

    @Schema(description = "CreateInvitePhone")
    private String phone;

    @Schema(description = "CreateInviteCode")
    private String code;
}

