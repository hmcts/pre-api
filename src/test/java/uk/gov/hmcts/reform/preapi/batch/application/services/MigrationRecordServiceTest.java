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
