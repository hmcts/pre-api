package uk.gov.hmcts.reform.preapi.dto.migration;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "VfMigrationRecordDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class VfMigrationRecordDTO {
    @Schema(description = "MigrationRecordId")
    private UUID id;

    @Schema(description = "MigrationRecordArchiveId")
    private String archiveId;

    @Schema(description = "MigrationRecordArchiveName")
    private String archiveName;

    @Schema(description = "MigrationRecordCreateTime")
    private Timestamp createTime;

    @Schema(description = "MigrationRecordDuration")
    private Integer duration;

    @Schema(description = "MigrationRecordCourtId")
    private String courtReference;

    @Schema(description = "MigrationRecordUrn")
    private String urn;

    @Schema(description = "MigrationRecordExhibitReference")
    private String exhibitReference;

    @Schema(description = "MigrationRecordDefendantName")
    private String defendantName;

    @Schema(description = "MigrationRecordWitnessName")
    private String witnessName;

    @Schema(description = "MigrationRecordRecordingVersion")
    private String recordingVersion;

    @Schema(description = "MigrationRecordRecordingVersionNumber")
    private String recordingVersionNumber;

    @Schema(description = "MigrationRecordFilename")
    private String filename;

    @Schema(description = "MigrationRecordFileSize")
    private String fileSize;

    @Schema(description = "MigrationRecordRecordingId")
    private UUID recordingId;

    @Schema(description = "MigrationRecordBookingId")
    private UUID bookingId;

    @Schema(description = "MigrationRecordCaptureSessionId")
    private UUID captureSessionId;

    @Schema(description = "MigrationRecordParentTempId")
    private UUID parentTempId;

    @Schema(description = "MigrationRecordStatus")
    private VfMigrationStatus status;

    @Schema(description = "MigrationRecordReason")
    private String reason;

    @Schema(description = "MigrationRecordErrorMessage")
    private String errorMessage;

    @Schema(description = "MigrationRecordResolvedAt")
    private Timestamp resolvedAt;

    @Schema(description = "MigrationRecordIsMostRecent")
    private Boolean isMostRecent;

    @Schema(description = "MigrationRecordRecordingGroupKey")
    private String recordingGroupKey;

    @Schema(description = "MigrationRecordCreatedAt")
    private Timestamp createdAt;

    public VfMigrationRecordDTO(MigrationRecord entity) {
        id = entity.getId();
        archiveId = entity.getArchiveId();
        archiveName = entity.getArchiveName();
        createTime = entity.getCreateTime();
        duration = entity.getDuration();
        courtReference = entity.getCourtReference();
        urn = entity.getUrn();
        exhibitReference = entity.getExhibitReference();
        defendantName = entity.getDefendantName();
        witnessName = entity.getWitnessName();
        recordingVersion = entity.getRecordingVersion();
        recordingVersionNumber = entity.getRecordingVersionNumber();
        filename = entity.getFileName();
        fileSize = entity.getFileSizeMb();
        recordingId = entity.getRecordingId();
        bookingId = entity.getBookingId();
        captureSessionId = entity.getCaptureSessionId();
        status = entity.getStatus();
        reason = entity.getReason();
        errorMessage = entity.getErrorMessage();
        resolvedAt = entity.getResolvedAt();
        isMostRecent = entity.getIsMostRecent();
        recordingGroupKey = entity.getRecordingGroupKey();
        createdAt = entity.getCreatedAt();
    }
}
