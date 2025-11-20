package uk.gov.hmcts.reform.preapi.dto.migration;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationRecordingVersion;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.dto.validators.AlphanumericConstraint;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateVfMigrationRecordDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateVfMigrationRecordDTO {
    @NotNull
    @Schema(description = "CreateMigrationRecordId")
    private UUID id;

    @Schema(description = "CreateMigrationRecordCourtId")
    private UUID courtId;

    @AlphanumericConstraint
    @Length(min = 9, max = 13)
    @Schema(description = "CreateMigrationRecordUrn")
    private String urn;

    @AlphanumericConstraint
    @Length(min = 0, max = 11)
    @Schema(description = "CreateMigrationRecordExhibitReference")
    private String exhibitReference;

    @Length(max = 25)
    @Schema(description = "CreateMigrationRecordDefendantName")
    private String defendantName;

    @Length(max = 25)
    @Schema(description = "CreateMigrationRecordWitnessName")
    private String witnessName;

    @Schema(description = "CreateMigrationRecordRecordingVersion")
    private VfMigrationRecordingVersion recordingVersion;

    @Min(value = 1)
    @Schema(description = "CreateMigrationRecordRecordingVersion")
    private Double recordingVersionNumber;

    @NotNull
    @Schema(description = "CreateMigrationRecordStatus")
    private VfMigrationStatus status;

    @Schema(description = "CreateMigrationRecordResolvedAt")
    private Timestamp resolvedAt;

    @Schema(description = "CreateMigrationRecordRecordingDate")
    private Timestamp recordingDate;
}
