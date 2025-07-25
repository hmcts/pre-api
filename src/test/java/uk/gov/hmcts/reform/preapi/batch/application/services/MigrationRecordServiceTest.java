package uk.gov.hmcts.reform.preapi.batch.application.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



@SpringBootTest(classes = {MigrationRecordService.class})
public class MigrationRecordServiceTest {

    @Autowired
    private MigrationRecordService migrationRecordService;

    @MockitoBean
    private MigrationRecordRepository migrationRecordRepository;

    @MockitoBean
    private LoggingService loggingService;

    @Test
    @DisplayName("Should upsert new MigrationRecord and return CREATED")
    void upsertShouldCreateNewRecord() {
        String archiveId = "123";
        when(migrationRecordRepository.findByArchiveId(archiveId)).thenReturn(Optional.empty());

        UpsertResult result = migrationRecordService.upsert(
            archiveId,
            "Archive Name",
            Timestamp.from(Instant.now()),
            100,
            "CourtRef",
            "URN123",
            "Exhibit1",
            "Doe",
            "John",
            "ORIG",
            "1",
            "file.mp4",
            "10MB",
            VfMigrationStatus.SUCCESS,
            "Reason",
            "",
            Timestamp.from(Instant.now()),
            UUID.randomUUID()
        );

        assertThat(result).isEqualTo(UpsertResult.CREATED);
        verify(migrationRecordRepository, times(1)).save(any(MigrationRecord.class));
    }

    @Test
    @DisplayName("Should not insert duplicate CSV pending record")
    void insertPendingShouldNotDuplicate() {
        CSVArchiveListData data = new CSVArchiveListData();
        data.setArchiveId("duplicateId");
        data.setArchiveName("duplicateName");

        when(migrationRecordRepository.findByArchiveId("duplicateId")).thenReturn(Optional.of(new MigrationRecord()));

        boolean inserted = migrationRecordService.insertPending(data);

        assertThat(inserted).isFalse();
        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update metadata fields")
    void updateMetadataFieldsShouldSetValues() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("123");
        when(migrationRecordRepository.findByArchiveId("123")).thenReturn(Optional.of(record));

        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setCourtReference("CourtRef");
        metadata.setUrn("URN1");
        metadata.setExhibitReference("EX1");
        metadata.setDefendantLastName("Smith");
        metadata.setWitnessFirstName("Anna");
        metadata.setRecordingVersion("ORIG");
        metadata.setRecordingVersionNumber("1");
        metadata.setFileName("video.mp4");
        metadata.setFileSize("20MB");

        migrationRecordService.updateMetadataFields("123", metadata);

        verify(migrationRecordRepository, times(1)).save(record);
        assertThat(record.getCourtReference()).isEqualTo("CourtRef");
        assertThat(record.getRecordingGroupKey()).isEqualTo("urn1|ex1|anna|smith");
    }

    @Test
    @DisplayName("Should update status to FAILED")
    void updateToFailedShouldSetFields() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("failId");
        when(migrationRecordRepository.findByArchiveId("failId")).thenReturn(Optional.of(record));

        migrationRecordService.updateToFailed("failId", "Parse_Error", "File unreadable");

        assertThat(record.getStatus()).isEqualTo(VfMigrationStatus.FAILED);
        assertThat(record.getReason()).isEqualTo("Parse_Error");
        assertThat(record.getErrorMessage()).isEqualTo("File unreadable");
        verify(migrationRecordRepository).save(record);
    }
}