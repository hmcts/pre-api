package uk.gov.hmcts.reform.preapi.batch.application.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationRecordingVersion;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchMigrationRecords;
import uk.gov.hmcts.reform.preapi.dto.migration.CreateVfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.dto.migration.VfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = MigrationRecordService.class)
public class MigrationRecordServiceTest {
    @MockitoBean
    private MigrationRecordRepository migrationRecordRepository;

    @MockitoBean
    private CourtRepository courtRepository;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private MigrationRecordService migrationRecordService;

    @Test
    @DisplayName("Should return lowercase combined string from non-null parameters")
    void generateRecordingGroupKeyShouldReturnLowercaseCombinedString() {
        String result = MigrationRecordService.generateRecordingGroupKey("URN123", "EXHIBIT1", "John", "Doe");

        assertThat(result).isEqualTo("urn123|exhibit1|john|doe");
    }

    @Test
    @DisplayName("Should handle null values by replacing with empty strings")
    void generateRecordingGroupKeyShouldHandleNullValues() {
        String result = MigrationRecordService.generateRecordingGroupKey(null, "EXHIBIT1", null, "Doe");

        assertThat(result).isEqualTo("|exhibit1||doe");
    }

    @Test
    @DisplayName("Should trim leading and trailing whitespace")
    void generateRecordingGroupKeyShouldTrimWhitespace() {
        String result = MigrationRecordService.generateRecordingGroupKey(" URN123 ", " EXHIBIT1 ", " John ", " Doe ");

        assertThat(result).isEqualTo("urn123 | exhibit1 | john | doe");
    }

    @Test
    @DisplayName("Should return distinct original version numbers for valid baseGroupKey")
    void findOrigVersionsByBaseGroupKeyShouldReturnDistinctVersionsForValidGroupKey() {
        MigrationRecord record1 = new MigrationRecord();
        record1.setRecordingVersion("ORIG");
        record1.setRecordingVersionNumber("1.1");
        record1.setRecordingGroupKey("baseGroupKey");

        MigrationRecord record2 = new MigrationRecord();
        record2.setRecordingVersion("ORIG");
        record2.setRecordingVersionNumber("2.3");
        record2.setRecordingGroupKey("baseGroupKey");

        MigrationRecord record3 = new MigrationRecord();
        record3.setRecordingVersion("COPY");
        record3.setRecordingVersionNumber("3.1");
        record3.setRecordingGroupKey("baseGroupKey");

        when(migrationRecordRepository.findByRecordingGroupKeyStartingWith("baseGroupKey"))
            .thenReturn(List.of(record1, record2, record3));

        List<String> result = migrationRecordService.findOrigVersionsByBaseGroupKey("baseGroupKey");

        assertThat(result).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    @DisplayName("Should return empty list when no records exist for baseGroupKey")
    void findOrigVersionsByBaseGroupKeyShouldReturnEmptyForNoMatchingRecords() {
        when(migrationRecordRepository.findByRecordingGroupKeyStartingWith("nonexistentKey"))
            .thenReturn(List.of());

        List<String> result = migrationRecordService.findOrigVersionsByBaseGroupKey("nonexistentKey");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should filter results to only ORIG versions")
    void findOrigVersionsByBaseGroupKeyShouldFilterToOrigVersionsOnly() {
        MigrationRecord record1 = new MigrationRecord();
        record1.setRecordingVersion("ORIG");
        record1.setRecordingVersionNumber("4.0");
        record1.setRecordingGroupKey("baseGroupKey");

        MigrationRecord record2 = new MigrationRecord();
        record2.setRecordingVersion("COPY");
        record2.setRecordingVersionNumber("4.1");
        record2.setRecordingGroupKey("baseGroupKey");

        when(migrationRecordRepository.findByRecordingGroupKeyStartingWith("baseGroupKey"))
            .thenReturn(List.of(record1, record2));

        List<String> result = migrationRecordService.findOrigVersionsByBaseGroupKey("baseGroupKey");

        assertThat(result).containsExactly("4");
    }

    @Test
    @DisplayName("Should mark non-MP4 records as not preferred when MP4 exists")
    void markNonMp4AsNotPreferredShouldUpdateRecords() {
        MigrationRecord mp4Record = new MigrationRecord();
        mp4Record.setArchiveId("mp4Id");
        mp4Record.setArchiveName("mp4Name.mp4");
        mp4Record.setRecordingGroupKey("groupKey");
        mp4Record.setRecordingVersionNumber("1");
        mp4Record.setFileName("video.mp4");
        mp4Record.setRecordingVersion("ORIG");
        mp4Record.setIsPreferred(false);

        MigrationRecord nonMp4Record = new MigrationRecord();
        nonMp4Record.setArchiveId("nonMp4Id");
        nonMp4Record.setArchiveName("mp4Name");
        nonMp4Record.setRecordingGroupKey("groupKey");
        nonMp4Record.setRecordingVersionNumber("1");
        nonMp4Record.setRecordingVersion("ORIG");
        nonMp4Record.setFileName("video.avi");
        nonMp4Record.setIsPreferred(true);

        when(migrationRecordRepository.findByArchiveId("mp4Id")).thenReturn(Optional.of(mp4Record));
        when(migrationRecordRepository.findByRecordingGroupKey("groupKey"))
            .thenReturn(List.of(mp4Record, nonMp4Record));

        boolean result = migrationRecordService.markNonMp4AsNotPreferred("mp4Id");

        assertThat(result).isTrue();
        assertThat(mp4Record.getIsPreferred()).isTrue();
        assertThat(nonMp4Record.getIsPreferred()).isFalse();

        verify(migrationRecordRepository, times(1)).save(mp4Record);
        verify(migrationRecordRepository, times(1)).save(nonMp4Record);
    }

    @Test
    @DisplayName("Should not update records when no MP4 exists for group key and version")
    void markNonMp4AsNotPreferredShouldDoNothingWhenNoMp4Exists() {
        MigrationRecord nonMp4Record = new MigrationRecord();
        nonMp4Record.setArchiveId("nonMp4Id");
        nonMp4Record.setRecordingGroupKey("groupKey");
        nonMp4Record.setRecordingVersionNumber("1");
        nonMp4Record.setRecordingVersion("ORIG");
        nonMp4Record.setFileName("video.avi");
        nonMp4Record.setIsPreferred(true);

        when(migrationRecordRepository.findByArchiveId("nonMp4Id")).thenReturn(Optional.of(nonMp4Record));
        when(migrationRecordRepository.findByRecordingGroupKey("groupKey"))
            .thenReturn(List.of(nonMp4Record));

        boolean result = migrationRecordService.markNonMp4AsNotPreferred("nonMp4Id");

        assertThat(result).isFalse();
        assertThat(nonMp4Record.getIsPreferred()).isTrue();

        verify(migrationRecordRepository, never()).save(nonMp4Record);
    }

    @Test
    @DisplayName("Should do nothing when archiveId does not exist")
    void markNonMp4AsNotPreferredShouldDoNothingWhenArchiveIdNotFound() {
        when(migrationRecordRepository.findByArchiveId("nonExistentId")).thenReturn(Optional.empty());

        boolean result = migrationRecordService.markNonMp4AsNotPreferred("nonExistentId");

        assertThat(result).isFalse();

        verify(migrationRecordRepository, never()).findByRecordingGroupKey(any());
        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should do nothing when groupKey or version is null")
    void markNonMp4AsNotPreferredShouldDoNothingWhenGroupKeyOrVersionIsNull() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("invalidId");
        record.setRecordingGroupKey(null);
        record.setRecordingVersionNumber(null);

        when(migrationRecordRepository.findByArchiveId("invalidId")).thenReturn(Optional.of(record));

        boolean result = migrationRecordService.markNonMp4AsNotPreferred("invalidId");

        assertThat(result).isFalse();

        verify(migrationRecordRepository, never()).findByRecordingGroupKey(any());
        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update bookingId for an existing record")
    void updateBookingIdShouldSetBookingIdForExistingRecord() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("existingId");
        record.setBookingId(null);
        UUID newBookingId = UUID.randomUUID();

        when(migrationRecordRepository.findByArchiveId("existingId")).thenReturn(Optional.of(record));

        migrationRecordService.updateBookingId("existingId", newBookingId);

        assertThat(record.getBookingId()).isEqualTo(newBookingId);

        verify(migrationRecordRepository, times(1)).save(record);
    }

    @Test
    @DisplayName("Should do nothing if record does not exist for updating booking ID")
    void updateBookingIdShouldNotUpdateForNonexistentRecord() {
        UUID newBookingId = UUID.randomUUID();

        when(migrationRecordRepository.findByArchiveId("nonExistentId")).thenReturn(Optional.empty());

        migrationRecordService.updateBookingId("nonExistentId", newBookingId);

        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return the most recent version number in the group")
    void findMostRecentVersionNumberInGroupShouldReturnMostRecentVersion() {
        MigrationRecord record1 = new MigrationRecord();
        record1.setRecordingVersionNumber("1.2");

        MigrationRecord record2 = new MigrationRecord();
        record2.setRecordingVersionNumber("2.0");

        MigrationRecord record3 = new MigrationRecord();
        record3.setRecordingVersionNumber("1.5");

        when(migrationRecordRepository.findByRecordingGroupKey("groupKey"))
            .thenReturn(List.of(record1, record2, record3));

        Optional<String> result = migrationRecordService.findMostRecentVersionNumberInGroup("groupKey");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("2.0");
    }

    @Test
    @DisplayName("Should return empty when no records exist for a given group")
    void findMostRecentVersionNumberInGroupShouldReturnEmptyWhenGroupIsEmpty() {
        when(migrationRecordRepository.findByRecordingGroupKey("emptyGroupKey"))
            .thenReturn(List.of());

        Optional<String> result = migrationRecordService.findMostRecentVersionNumberInGroup("emptyGroupKey");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should ignore records with blank or null version numbers")
    void findMostRecentVersionNumberInGroupShouldIgnoreBlankOrNullVersions() {
        MigrationRecord record1 = new MigrationRecord();
        record1.setRecordingVersionNumber(null);

        MigrationRecord record2 = new MigrationRecord();
        record2.setRecordingVersionNumber(" ");

        MigrationRecord record3 = new MigrationRecord();
        record3.setRecordingVersionNumber("1.3");

        when(migrationRecordRepository.findByRecordingGroupKey("groupKeyWithBlanks"))
            .thenReturn(List.of(record1, record2, record3));

        Optional<String> result = migrationRecordService.findMostRecentVersionNumberInGroup("groupKeyWithBlanks");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("1.3");
    }

    @Test
    @DisplayName("Should find by archive id")
    void findByArchiveId() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("123");
        when(migrationRecordRepository.findByArchiveId("123")).thenReturn(Optional.of(record));

        migrationRecordService.findByArchiveId("123");

        verify(migrationRecordRepository, times(1)).findByArchiveId("123");
    }

    @Test
    @DisplayName("Should return the original MigrationRecord when parentTempId is present")
    void getOrigFromCopyWithParentId() {
        MigrationRecord copy = new MigrationRecord();
        copy.setParentTempId(UUID.randomUUID());
        MigrationRecord originalRecord = new MigrationRecord();
        originalRecord.setId(copy.getParentTempId());

        when(migrationRecordRepository.findById(copy.getParentTempId())).thenReturn(Optional.of(originalRecord));

        Optional<MigrationRecord> result = migrationRecordService.getOrigFromCopy(copy);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(originalRecord);

        verify(migrationRecordRepository, times(1)).findById(copy.getParentTempId());
    }

    @Test
    @DisplayName("Should return empty when parentTempId is not present")
    void getOrigFromCopyWithoutParentId() {
        MigrationRecord copy = new MigrationRecord();
        copy.setParentTempId(null);

        Optional<MigrationRecord> result = migrationRecordService.getOrigFromCopy(copy);

        assertThat(result).isEmpty();
        verify(migrationRecordRepository, never()).findById(any());
    }

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

        verify(migrationRecordRepository, times(1)).saveAndFlush(any(MigrationRecord.class));
    }

    @Test
    @DisplayName("Should update existing MigrationRecord and return UPDATED")
    void upsertShouldUpdateExistingRecord() {
        String archiveId = "existingId";
        MigrationRecord existingRecord = new MigrationRecord();
        existingRecord.setArchiveId(archiveId);
        existingRecord.setArchiveName("Old Archive");
        when(migrationRecordRepository.findByArchiveId(archiveId)).thenReturn(Optional.of(existingRecord));

        UpsertResult result = migrationRecordService.upsert(
            archiveId,
            "Updated Archive Name",
            Timestamp.from(Instant.now()),
            200,
            "UpdatedCourtRef",
            "URNUpdated",
            "UpdatedExhibit",
            "UpdatedDoe",
            "UpdatedJohn",
            "COPY",
            "2",
            "updated_file.mp4",
            "15MB",
            VfMigrationStatus.SUBMITTED,
            "UpdatedReason",
            "No Error",
            Timestamp.from(Instant.now()),
            UUID.randomUUID()
        );

        assertThat(result).isEqualTo(UpsertResult.UPDATED);
        assertThat(existingRecord.getArchiveName()).isEqualTo("Updated Archive Name");
        assertThat(existingRecord.getStatus()).isEqualTo(VfMigrationStatus.SUBMITTED);

        verify(migrationRecordRepository, times(1)).saveAndFlush(existingRecord);
    }

    @Test
    @DisplayName("Should create new MigrationRecord with null and default values")
    void upsertShouldHandleNullValuesAndDefaults() {
        String archiveId = "newArchiveId";
        when(migrationRecordRepository.findByArchiveId(archiveId)).thenReturn(Optional.empty());

        UpsertResult result = migrationRecordService.upsert(
            archiveId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            VfMigrationStatus.PENDING,
            null,
            null,
            null,
            null
        );

        assertThat(result).isEqualTo(UpsertResult.CREATED);

        verify(migrationRecordRepository, times(1)).saveAndFlush(any(MigrationRecord.class));
    }

    @Test
    @DisplayName("Should insert new CSV pending record")
    void insertPendingShouldInsertNewRecord() {
        CSVArchiveListData data = new CSVArchiveListData();
        data.setArchiveId("newId");
        data.setArchiveName("newName");

        when(migrationRecordRepository.findByArchiveId("newId")).thenReturn(Optional.empty());

        boolean inserted = migrationRecordService.insertPending(data);

        assertThat(inserted).isTrue();

        verify(migrationRecordRepository, times(1)).saveAndFlush(any(MigrationRecord.class));
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

        assertThat(record.getCourtReference()).isEqualTo("CourtRef");
        assertThat(record.getRecordingGroupKey()).isEqualTo("urn1|ex1|anna|smith");

        verify(migrationRecordRepository, times(1)).save(record);
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

    @Test
    @DisplayName("Should do nothing for nonexistent archiveId")
    void deduplicatePreferredByArchiveIdShouldDoNothingForNonexistentArchiveId() {
        when(migrationRecordRepository.findByArchiveId("nonExistentId")).thenReturn(Optional.empty());

        boolean result = migrationRecordService.deduplicatePreferredByArchiveId("nonExistentId");

        assertThat(result).isFalse();

        verify(migrationRecordRepository, never()).findAllByArchiveName(any());
        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return all pending migration records")
    void getPendingMigrationRecordsReturnsAllPendingRecords() {
        MigrationRecord record1 = new MigrationRecord();
        record1.setArchiveId("pending1");
        record1.setStatus(VfMigrationStatus.PENDING);

        MigrationRecord record2 = new MigrationRecord();
        record2.setArchiveId("pending2");
        record2.setStatus(VfMigrationStatus.PENDING);

        when(migrationRecordRepository.findAllByStatus(VfMigrationStatus.PENDING))
            .thenReturn(List.of(record1, record2));

        List<MigrationRecord> result = migrationRecordService.getPendingMigrationRecords();

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(record1, record2);

        verify(migrationRecordRepository, times(1)).findAllByStatus(VfMigrationStatus.PENDING);
    }

    @Test
    @DisplayName("Should return empty list when no pending migration records are available")
    void getPendingMigrationRecordsReturnsEmptyListWhenNoPendingRecords() {
        when(migrationRecordRepository.findAllByStatus(VfMigrationStatus.PENDING))
            .thenReturn(List.of());

        List<MigrationRecord> result = migrationRecordService.getPendingMigrationRecords();

        assertThat(result).isEmpty();

        verify(migrationRecordRepository, times(1)).findAllByStatus(VfMigrationStatus.PENDING);
    }

    @Test
    @DisplayName("Should insert new pending record from XML")
    void insertPendingFromXmlShouldCreateNewRecord() {
        String archiveId = "new_id";
        String archiveName = "new_archive";
        String createTimeEpoch = "1688000000";
        String duration = "300";
        String mp4FileName = "video.mp4";
        String fileSizeMb = "20MB";

        when(migrationRecordRepository.findByArchiveId(archiveId)).thenReturn(Optional.empty());

        boolean result = migrationRecordService.insertPendingFromXml(
            archiveId, archiveName, createTimeEpoch, duration, mp4FileName, fileSizeMb);

        assertThat(result).isTrue();

        verify(migrationRecordRepository, times(2)).findByArchiveId(archiveId);
        verify(migrationRecordRepository, times(1)).saveAndFlush(any(MigrationRecord.class));
    }

    @Test
    @DisplayName("Should not insert duplicate record from XML")
    void insertPendingFromXmlShouldNotCreateDuplicate() {
        String archiveId = "existing_id";
        String archiveName = "existing_archive";
        String createTimeEpoch = "1688000000";
        String duration = "300";
        String mp4FileName = "video.mp4";
        String fileSizeMb = "20MB";

        when(migrationRecordRepository.findByArchiveId(archiveId)).thenReturn(Optional.of(new MigrationRecord()));

        boolean result = migrationRecordService.insertPendingFromXml(
            archiveId, archiveName, createTimeEpoch, duration, mp4FileName, fileSizeMb);

        assertThat(result).isFalse();

        verify(migrationRecordRepository, times(1)).findByArchiveId(archiveId);
        verify(loggingService, times(1)).logInfo("Already processed: %s", archiveName);
    }

    @Test
    @DisplayName("Should update isPreferred to true for a valid record")
    void updateIsPreferredShouldUpdateRecord() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("validId");
        record.setIsPreferred(false);
        when(migrationRecordRepository.findByArchiveId("validId")).thenReturn(Optional.of(record));

        migrationRecordService.updateIsPreferred("validId", true);

        assertThat(record.getIsPreferred()).isTrue();

        verify(migrationRecordRepository, times(1)).save(record);
    }

    @Test
    @DisplayName("Should update isPreferred to false for a valid record")
    void updateIsPreferredShouldUpdateRecordToNotPreferred() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("validId");
        record.setIsPreferred(true);
        when(migrationRecordRepository.findByArchiveId("validId")).thenReturn(Optional.of(record));

        migrationRecordService.updateIsPreferred("validId", false);

        assertThat(record.getIsPreferred()).isFalse();

        verify(migrationRecordRepository, times(1)).save(record);
    }

    @Test
    @DisplayName("Should do nothing if record does not exist")
    void updateIsPreferredShouldDoNothingForNonexistentRecord() {
        when(migrationRecordRepository.findByArchiveId("nonExistentId")).thenReturn(Optional.empty());

        migrationRecordService.updateIsPreferred("nonExistentId", true);

        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update status to SUCCESS and clear reason and errorMessage")
    void updateToSuccessShouldSetStatusToSuccess() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("successId");
        record.setStatus(VfMigrationStatus.PENDING);
        record.setReason("Initial Reason");
        record.setErrorMessage("Initial Error");

        when(migrationRecordRepository.findByArchiveId("successId")).thenReturn(Optional.of(record));

        migrationRecordService.updateToSuccess("successId");

        assertThat(record.getStatus()).isEqualTo(VfMigrationStatus.SUCCESS);
        assertThat(record.getReason()).isEmpty();
        assertThat(record.getErrorMessage()).isEmpty();

        verify(migrationRecordRepository, times(1)).save(record);
    }

    @Test
    @DisplayName("Should do nothing if record does not exist when updating to SUCCESS")
    void updateToSuccessShouldDoNothingForNonexistentRecord() {
        when(migrationRecordRepository.findByArchiveId("nonexistentId")).thenReturn(Optional.empty());

        migrationRecordService.updateToSuccess("nonexistentId");

        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update recordingId for an existing record")
    void updateRecordingIdShouldUpdateForExistingRecord() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("existingId");
        record.setRecordingId(null);
        UUID newRecordingId = UUID.randomUUID();

        when(migrationRecordRepository.findByArchiveId("existingId")).thenReturn(Optional.of(record));

        migrationRecordService.updateRecordingId("existingId", newRecordingId);

        assertThat(record.getRecordingId()).isEqualTo(newRecordingId);

        verify(migrationRecordRepository, times(1)).save(record);
    }

    @Test
    @DisplayName("Should do nothing if record does not exist for updating recordingId")
    void updateRecordingIdShouldDoNothingForNonexistentRecord() {
        UUID newRecordingId = UUID.randomUUID();

        when(migrationRecordRepository.findByArchiveId("nonExistentId")).thenReturn(Optional.empty());

        migrationRecordService.updateRecordingId("nonExistentId", newRecordingId);

        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update CaptureSessionId for an existing record")
    void updateCaptureSessionIdShouldUpdateForExistingRecord() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("existingId");
        record.setCaptureSessionId(null);
        UUID newCaptureSessionId = UUID.randomUUID();

        when(migrationRecordRepository.findByArchiveId("existingId")).thenReturn(Optional.of(record));

        migrationRecordService.updateCaptureSessionId("existingId", newCaptureSessionId);

        assertThat(record.getCaptureSessionId()).isEqualTo(newCaptureSessionId);

        verify(migrationRecordRepository, times(1)).save(record);
    }

    @Test
    @DisplayName("Should do nothing if record does not exist for updating CaptureSessionId")
    void updateCaptureSessionIdShouldDoNothingForNonexistentRecord() {
        UUID newCaptureSessionId = UUID.randomUUID();

        when(migrationRecordRepository.findByArchiveId("nonExistentId")).thenReturn(Optional.empty());

        migrationRecordService.updateCaptureSessionId("nonExistentId", newCaptureSessionId);

        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update parentTempId for a copy record when a matching original exists")
    void updateParentTempIdIfCopyShouldUpdateParentIdForMatchingOriginal() {
        MigrationRecord original = new MigrationRecord();
        original.setId(UUID.randomUUID());
        original.setRecordingVersion("ORIG");
        original.setArchiveName("Archive Name");
        original.setIsPreferred(true);
        original.setRecordingVersionNumber("123.1");
        String recordingGroupKey = "groupKey";

        when(migrationRecordRepository.findByRecordingGroupKey(recordingGroupKey))
            .thenReturn(List.of(original));

        String archiveId = "copyArchiveId";
        String origVersionStr = "123";

        MigrationRecord copy = new MigrationRecord();
        copy.setArchiveId(archiveId);
        when(migrationRecordRepository.findByArchiveId(archiveId))
            .thenReturn(Optional.of(copy));

        migrationRecordService.updateParentTempIdIfCopy(archiveId, recordingGroupKey, origVersionStr);

        assertThat(copy.getParentTempId()).isEqualTo(original.getId());

        verify(migrationRecordRepository, times(1)).save(copy);
    }

    @Test
    @DisplayName("Should do nothing if no matching original exists for the copy")
    void updateParentTempIdIfCopyShouldDoNothingWhenOriginalDoesNotExist() {
        String archiveId = "copyArchiveId";
        String recordingGroupKey = "groupKey";
        String origVersionStr = "123";

        when(migrationRecordRepository.findByRecordingGroupKey(recordingGroupKey))
            .thenReturn(List.of());

        migrationRecordService.updateParentTempIdIfCopy(archiveId, recordingGroupKey, origVersionStr);

        verify(migrationRecordRepository, never()).findByArchiveId(archiveId);
        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should do nothing if copy record does not exist")
    void updateParentTempIdIfCopyShouldDoNothingForNonexistentCopyRecord() {
        MigrationRecord original = new MigrationRecord();
        original.setId(UUID.randomUUID());
        original.setRecordingVersion("ORIG");
        original.setArchiveName("Archive Name");
        original.setIsPreferred(true);
        original.setRecordingVersionNumber("123.1");

        String archiveId = "nonExistentId";
        String recordingGroupKey = "groupKey";
        String origVersionStr = "123";

        when(migrationRecordRepository.findByRecordingGroupKey(recordingGroupKey))
            .thenReturn(List.of(original));

        when(migrationRecordRepository.findByArchiveId(archiveId))
            .thenReturn(Optional.empty());

        migrationRecordService.updateParentTempIdIfCopy(archiveId, recordingGroupKey, origVersionStr);

        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return false if no records exist for the provided archiveId")
    void deduplicateShouldDoNothingForNonexistentArchiveId() {
        when(migrationRecordRepository.findByArchiveId("nonexistentId")).thenReturn(Optional.empty());

        boolean result = migrationRecordService.deduplicatePreferredByArchiveId("nonexistentId");

        assertThat(result).isFalse();

        verify(migrationRecordRepository, never()).findAllByArchiveName(any());
        verify(migrationRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should deduplicate and mark most recent record as preferred")
    void deduplicatePreferredByArchiveIdShouldUpdatePreferredCorrectly() {
        MigrationRecord record1 = new MigrationRecord();
        record1.setArchiveId("id1");
        record1.setArchiveName("archiveA");
        record1.setCreateTime(Timestamp.from(Instant.parse("2023-01-01T10:00:00Z")));

        MigrationRecord record2 = new MigrationRecord();
        record2.setArchiveId("id2");
        record2.setArchiveName("archiveA");
        record2.setCreateTime(Timestamp.from(Instant.parse("2023-01-02T10:00:00Z")));

        when(migrationRecordRepository.findByArchiveId("id2")).thenReturn(Optional.of(record2));
        when(migrationRecordRepository.findAllByArchiveName("archiveA"))
            .thenReturn(new ArrayList<>(List.of(record1, record2)));

        boolean result = migrationRecordService.deduplicatePreferredByArchiveId("id2");

        assertThat(result).isTrue();
        assertThat(record2.getIsPreferred()).isTrue();
        assertThat(record1.getIsPreferred()).isFalse();

        verify(migrationRecordRepository, times(1)).save(record1);
        verify(migrationRecordRepository, times(1)).save(record2);
    }

    @Test
    @DisplayName("Should set isMostRecent true for single ORIG record in group")
    void updateMetadataFieldsShouldSetIsMostRecentForOrig() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("id1");
        record.setRecordingVersion("ORIG");
        record.setRecordingGroupKey("urn|ex|wit|def"); // Set the groupKey that will be generated
        record.setRecordingVersionNumber("1");
        record.setIsMostRecent(false); // Initialize to false

        when(migrationRecordRepository.findByArchiveId("id1")).thenReturn(Optional.of(record));
        when(migrationRecordRepository.findByRecordingGroupKey("urn|ex|wit|def")).thenReturn(List.of(record));

        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setCourtReference("Court");
        metadata.setUrn("urn");
        metadata.setExhibitReference("ex");
        metadata.setDefendantLastName("def");
        metadata.setWitnessFirstName("wit");
        metadata.setRecordingVersion("ORIG");
        metadata.setRecordingVersionNumber("1");
        metadata.setFileName("file.mp4");
        metadata.setFileSize("10MB");

        migrationRecordService.updateMetadataFields("id1", metadata);

        assertThat(record.getIsMostRecent()).isTrue();
        verify(migrationRecordRepository).save(record);
        verify(migrationRecordRepository).saveAll(List.of(record));
    }

    @Test
    @DisplayName("Should set isMostRecent true for only copy record in group")
    void updateMetadataFieldsShouldSetIsMostRecentForSingleCopy() {
        MigrationRecord copy = new MigrationRecord();
        copy.setArchiveId("id2");
        copy.setRecordingVersion("COPY");
        copy.setRecordingGroupKey("urn|ex|wit|def"); // Set the groupKey that will be generated
        copy.setRecordingVersionNumber("2");
        copy.setIsMostRecent(false); // Initialize to false
        copy.setParentTempId(UUID.randomUUID());

        when(migrationRecordRepository.findByArchiveId("id2")).thenReturn(Optional.of(copy));
        when(migrationRecordRepository.findByRecordingGroupKey("urn|ex|wit|def")).thenReturn(List.of(copy));

        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setCourtReference("Court");
        metadata.setUrn("urn");
        metadata.setExhibitReference("ex");
        metadata.setDefendantLastName("def");
        metadata.setWitnessFirstName("wit");
        metadata.setRecordingVersion("COPY");
        metadata.setRecordingVersionNumber("2");
        metadata.setFileName("file.mp4");
        metadata.setFileSize("10MB");

        migrationRecordService.updateMetadataFields("id2", metadata);

        assertThat(copy.getIsMostRecent()).isTrue();
        verify(migrationRecordRepository).save(copy);
        verify(migrationRecordRepository).saveAll(List.of(copy));
    }

    @Test
    @DisplayName("Should set isMostRecent true only for most recent copy in group")
    void updateMetadataFieldsShouldSetIsMostRecentForMostRecentCopy() {
        UUID parentId = UUID.randomUUID();

        MigrationRecord copy1 = new MigrationRecord();
        copy1.setArchiveId("id3");
        copy1.setRecordingVersion("COPY");
        copy1.setRecordingGroupKey("urn|ex|wit|def"); // Set the same groupKey that will be generated
        copy1.setRecordingVersionNumber("1");
        copy1.setIsMostRecent(false);
        copy1.setParentTempId(parentId);

        MigrationRecord copy2 = new MigrationRecord();
        copy2.setArchiveId("id4");
        copy2.setRecordingVersion("COPY");
        copy2.setRecordingGroupKey("urn|ex|wit|def"); // Set the same groupKey that will be generated
        copy2.setRecordingVersionNumber("2");
        copy2.setIsMostRecent(false);
        copy2.setParentTempId(parentId);

        when(migrationRecordRepository.findByArchiveId("id4")).thenReturn(Optional.of(copy2));
        when(migrationRecordRepository.findByRecordingGroupKey("urn|ex|wit|def")).thenReturn(List.of(copy1, copy2));

        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setCourtReference("Court");
        metadata.setUrn("urn");
        metadata.setExhibitReference("ex");
        metadata.setDefendantLastName("def");
        metadata.setWitnessFirstName("wit");
        metadata.setRecordingVersion("COPY");
        metadata.setRecordingVersionNumber("2");
        metadata.setFileName("file.mp4");
        metadata.setFileSize("10MB");

        migrationRecordService.updateMetadataFields("id4", metadata);

        assertThat(copy1.getIsMostRecent()).isFalse();
        assertThat(copy2.getIsMostRecent()).isTrue();
        verify(migrationRecordRepository).save(copy2);
        verify(migrationRecordRepository).saveAll(List.of(copy1, copy2));
    }

    @Test
    @DisplayName("Returns a page of VfMigrationRecordDTO")
    public void findAllBy() {
        final MigrationRecord migrationRecord = createMigrationRecord();

        when(migrationRecordRepository.findAllBy(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(migrationRecord)));

        Page<VfMigrationRecordDTO> result = migrationRecordService.findAllBy(
            new SearchMigrationRecords(),
            Pageable.unpaged());

        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(migrationRecord.getId());

        verify(migrationRecordRepository, times(1))
            .findAllBy(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("Update should throw ResourceInWrongStateException when record status is SUCCESS")
    public void updateThrowsResourceInWrongStateException() {
        final CreateVfMigrationRecordDTO dto = new CreateVfMigrationRecordDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(VfMigrationStatus.READY);

        final MigrationRecord migrationRecord = new MigrationRecord();
        migrationRecord.setId(dto.getId());
        migrationRecord.setStatus(VfMigrationStatus.SUCCESS);

        when(migrationRecordRepository.findById(dto.getId())).thenReturn(Optional.of(migrationRecord));

        final String message = assertThrows(
            ResourceInWrongStateException.class,
            () -> migrationRecordService.update(dto)
        ).getMessage();
        assertThat(message).contains("MigrationRecord")
            .contains(migrationRecord.getId().toString())
            .contains(dto.getStatus().toString());

        verify(migrationRecordRepository, times(1)).findById(dto.getId());
        verifyNoMoreInteractions(migrationRecordRepository);
    }

    @Test
    @DisplayName("Update should throw ResourceInWrongStateException when record status is SUBMITTED")
    public void updateThrowsResourceInWrongStateExceptionSubmitted() {
        final CreateVfMigrationRecordDTO dto = new CreateVfMigrationRecordDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(VfMigrationStatus.READY);

        final MigrationRecord migrationRecord = new MigrationRecord();
        migrationRecord.setId(dto.getId());
        migrationRecord.setStatus(VfMigrationStatus.SUBMITTED);

        when(migrationRecordRepository.findById(dto.getId())).thenReturn(Optional.of(migrationRecord));

        final String message = assertThrows(
            ResourceInWrongStateException.class,
            () -> migrationRecordService.update(dto)
        ).getMessage();
        assertThat(message).contains("MigrationRecord")
            .contains(migrationRecord.getId().toString())
            .contains(dto.getStatus().toString());

        verify(migrationRecordRepository, times(1)).findById(dto.getId());
        verifyNoMoreInteractions(migrationRecordRepository);
    }

    @Test
    @DisplayName("Update should throw NotFoundException when migration record not found")
    public void updateMigrationRecordNotFound() {
        final CreateVfMigrationRecordDTO dto = new CreateVfMigrationRecordDTO();
        dto.setId(UUID.randomUUID());

        when(migrationRecordRepository.findById(dto.getId())).thenReturn(Optional.empty());

        String message = assertThrows(
            NotFoundException.class,
            () -> migrationRecordService.update(dto)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Migration Record: " +  dto.getId());

        verify(migrationRecordRepository, times(1)).findById(dto.getId());
        verifyNoMoreInteractions(migrationRecordRepository);
    }

    @Test
    @DisplayName("Update should throw NotFoundException when specified court id not found")
    public void updateCourtIdNotFound() {
        final CreateVfMigrationRecordDTO dto = new CreateVfMigrationRecordDTO();
        dto.setId(UUID.randomUUID());
        dto.setCourtId(UUID.randomUUID());

        when(migrationRecordRepository.findById(dto.getId())).thenReturn(Optional.of(createMigrationRecord()));
        when(courtRepository.existsById(dto.getCourtId())).thenReturn(false);

        String message = assertThrows(
            NotFoundException.class,
            () -> migrationRecordService.update(dto)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Court: " +  dto.getCourtId());

        verify(migrationRecordRepository, times(1)).findById(dto.getId());
        verify(courtRepository, times(1)).findById(dto.getCourtId());
        verifyNoMoreInteractions(migrationRecordRepository);
    }

    @Test
    @DisplayName("Should successfully update migration record")
    public void updateSuccess() {
        final CreateVfMigrationRecordDTO dto = new CreateVfMigrationRecordDTO();
        dto.setId(UUID.randomUUID());
        dto.setCourtId(UUID.randomUUID());
        dto.setUrn("urn");
        dto.setExhibitReference("exhibitReference");
        dto.setWitnessName("witnessName");
        dto.setDefendantName("defendantName");
        dto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);
        dto.setStatus(VfMigrationStatus.READY);
        dto.setResolvedAt(Timestamp.from(Instant.now()));

        final Court court = new Court();
        court.setName("Example Court");

        final MigrationRecord migrationRecord = createMigrationRecord();

        when(migrationRecordRepository.findById(dto.getId())).thenReturn(Optional.of(migrationRecord));
        when(courtRepository.findById(dto.getCourtId())).thenReturn(Optional.of(court));

        UpsertResult result = migrationRecordService.update(dto);

        assertThat(result).isEqualTo(UpsertResult.UPDATED);
        assertThat(migrationRecord.getCourtReference()).isEqualTo(court.getName());
        assertThat(migrationRecord.getUrn()).isEqualTo(dto.getUrn());
        assertThat(migrationRecord.getExhibitReference()).isEqualTo(dto.getExhibitReference());
        assertThat(migrationRecord.getWitnessName()).isEqualTo(dto.getWitnessName());
        assertThat(migrationRecord.getDefendantName()).isEqualTo(dto.getDefendantName());
        assertThat(migrationRecord.getRecordingVersion()).isEqualTo(dto.getRecordingVersion().toString());
        assertThat(migrationRecord.getStatus()).isEqualTo(dto.getStatus());
        assertThat(migrationRecord.getResolvedAt()).isEqualTo(dto.getResolvedAt());

        verify(migrationRecordRepository, times(1)).findById(dto.getId());
        verify(courtRepository, times(1)).findById(dto.getCourtId());
        verify(migrationRecordRepository, times(1)).saveAndFlush(any(MigrationRecord.class));
    }

    @Test
    @DisplayName("Should update READY status to SUBMITTED and return true")
    void markReadyAsSubmittedShouldUpdateReadyToSubmitted() {
        MigrationRecord readyRecord1 = new MigrationRecord();
        readyRecord1.setArchiveId("readyId1");
        readyRecord1.setStatus(VfMigrationStatus.READY);

        MigrationRecord readyRecord2 = new MigrationRecord();
        readyRecord2.setArchiveId("readyId2");
        readyRecord2.setStatus(VfMigrationStatus.READY);

        when(migrationRecordRepository.findAllByStatus(VfMigrationStatus.READY))
            .thenReturn(List.of(readyRecord1, readyRecord2));

        boolean result = migrationRecordService.markReadyAsSubmitted();

        assertThat(result).isTrue();
        assertThat(readyRecord1.getStatus()).isEqualTo(VfMigrationStatus.SUBMITTED);
        assertThat(readyRecord2.getStatus()).isEqualTo(VfMigrationStatus.SUBMITTED);

        verify(migrationRecordRepository, times(1)).saveAllAndFlush(List.of(readyRecord1, readyRecord2));
    }

    @Test
    @DisplayName("Should return false when no READY records exist")
    void markReadyAsSubmittedShouldReturnFalseIfNoReadyRecordsExist() {
        when(migrationRecordRepository.findAllByStatus(VfMigrationStatus.READY))
            .thenReturn(List.of());

        boolean result = migrationRecordService.markReadyAsSubmitted();

        assertThat(result).isFalse();

        verify(migrationRecordRepository, times(1)).findAllByStatus(VfMigrationStatus.READY);
        verify(migrationRecordRepository, never()).saveAllAndFlush(any());
    }


    private MigrationRecord createMigrationRecord() {
        final MigrationRecord migrationRecord = new MigrationRecord();
        migrationRecord.setId(UUID.randomUUID());

        return migrationRecord;
    }
}
