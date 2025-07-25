package uk.gov.hmcts.reform.preapi.batch.entities;

import java.time.LocalDateTime;

public record ResolvedMigrationRecord(
    String archiveId,
    String archiveName,
    LocalDateTime createTime,
    Integer duration,
    String courtReference,
    String urn,
    String exhibitReference,
    String defendantName,
    String witnessName,
    String recordingVersion,
    String recordingVersionNumber,
    String mp4FileName,
    String fileSizeMb
) {}