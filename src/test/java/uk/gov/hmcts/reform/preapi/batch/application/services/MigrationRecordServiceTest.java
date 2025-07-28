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

    @Test
    @DisplayName("Returns a page of VfMigrationRecordDTO")
    public void findAllBy() {
        final MigrationRecord migrationRecord = createMigrationRecord();

        when(migrationRecordRepository.findAllBy(any(), any()))
            .thenReturn(new PageImpl<>(List.of(migrationRecord)));

        Page<VfMigrationRecordDTO> result = migrationRecordService.findAllBy(
            new SearchMigrationRecords(),
            Pageable.unpaged());

        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(migrationRecord.getId());

        verify(migrationRecordRepository, times(1)).findAllBy(any(SearchMigrationRecords.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Update should throw ResourceInWrongStateException when record status is SUCCESS")
    public void updateThrowsResourceInWrongStateException() {
        final CreateVfMigrationRecordDTO dto = new CreateVfMigrationRecordDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(VfMigrationStatus.RESOLVED);

        final MigrationRecord migrationRecord = new MigrationRecord();
        migrationRecord.setStatus(VfMigrationStatus.SUCCESS);

        when(migrationRecordRepository.findById(dto.getId())).thenReturn(Optional.of(migrationRecord));

        final String message = assertThrows(
            ResourceInWrongStateException.class,
            () -> migrationRecordService.update(dto)
        ).getMessage();
        assertThat(message).contains("MigrationRecord")
            .contains(dto.getId().toString())
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
        dto.setStatus(VfMigrationStatus.RESOLVED);
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

    private MigrationRecord createMigrationRecord() {
        final MigrationRecord migrationRecord = new MigrationRecord();
        migrationRecord.setId(UUID.randomUUID());

        return migrationRecord;
    }
}
