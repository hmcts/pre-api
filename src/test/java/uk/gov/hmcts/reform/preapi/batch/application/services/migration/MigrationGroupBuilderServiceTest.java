package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationGroupBuilderService.BOOKING_FIELD;
import static uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationGroupBuilderService.CAPTURE_SESSION_FIELD;
import static uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationGroupBuilderService.RECORDING_FIELD;

@SpringBootTest(classes = { MigrationGroupBuilderService.class })
public class MigrationGroupBuilderServiceTest {
    @MockitoBean
    private LoggingService loggingService;
    

    @MockitoBean
    private EntityCreationService entityCreationService;

    @MockitoBean
    private InMemoryCacheService inMemoryCacheService;

    @MockitoBean
    private MigrationTrackerService migrationTrackerService;

    @MockitoBean
    private MigrationRecordService migrationRecordService;

    @MockitoBean
    private CaseRepository caseRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MigrationGroupBuilderService migrationGroupBuilderService;

    private static final String CASE_REFERENCE = "CASE_REFERENCE";
    private static final String BASE_KEY = "baseKey";

    @Test
    @DisplayName("Should return null when case reference is invalid")
    void createCaseIfOrigCaseReferenceIsInvalid() {
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .build();

        assertThat(migrationGroupBuilderService.createCaseIfOrig(cleansedData)).isNull();
    }

    @Test
    @DisplayName("Should return updated case if already exists")
    void createCaseIfOrigUpdateCase() {
        CreateCaseDTO existingCase = new CreateCaseDTO(createCase());
        when(inMemoryCacheService.getCase(CASE_REFERENCE)).thenReturn(Optional.of(existingCase));
        when(entityCreationService.createParticipants(any())).thenReturn(Set.of());
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .build();

        CreateCaseDTO result = migrationGroupBuilderService.createCaseIfOrig(cleansedData);
        assertThat(result).isNotNull();
        verify(inMemoryCacheService, times(1)).getCase(CASE_REFERENCE);
    }

    @Test
    @DisplayName("Should return new case if doesn't already exist but is created successfully")
    void createCaseIfOrigCreateNewCaseSuccess() {
        when(inMemoryCacheService.getCase(CASE_REFERENCE)).thenReturn(Optional.empty());
        CreateCaseDTO newCase = new CreateCaseDTO();
        when(entityCreationService.createCase(any())).thenReturn(newCase);
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .build();

        CreateCaseDTO result = migrationGroupBuilderService.createCaseIfOrig(cleansedData);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(newCase);

        verify(inMemoryCacheService, times(1)).getCase(CASE_REFERENCE);
        verify(inMemoryCacheService, times(1)).saveCase(CASE_REFERENCE, newCase);
    }

    @Test
    @DisplayName("Should return null if doesn't already exist but new case cannot be created")
    void createCaseIfOrigCreateNewCaseFailure() {
        when(inMemoryCacheService.getCase(CASE_REFERENCE)).thenReturn(Optional.empty());
        when(entityCreationService.createCase(any())).thenReturn(null);
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .build();

        CreateCaseDTO result = migrationGroupBuilderService.createCaseIfOrig(cleansedData);
        assertThat(result).isNull();
        verify(inMemoryCacheService, times(1)).getCase(CASE_REFERENCE);
        verify(inMemoryCacheService, never()).saveCase(eq(CASE_REFERENCE), any());
    }

    @Test
    void processBookingCacheHit() {
        CreateBookingDTO booking = new CreateBookingDTO();
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, BOOKING_FIELD)).thenReturn(true);
        when(inMemoryCacheService.getHashValue(BASE_KEY, BOOKING_FIELD, CreateBookingDTO.class)).thenReturn(booking);
        ProcessedRecording cleansedData = ProcessedRecording.builder().build();
        CreateCaseDTO aCase = new CreateCaseDTO();

        CreateBookingDTO result = migrationGroupBuilderService.processBooking(BASE_KEY, cleansedData, aCase);

        assertThat(result).isNotNull();

        verify(inMemoryCacheService, times(1)).checkHashKeyExists(BASE_KEY, BOOKING_FIELD);
        verify(inMemoryCacheService, times(1)).getHashValue(BASE_KEY, BOOKING_FIELD, CreateBookingDTO.class);
        verify(entityCreationService, never()).createBooking(any(), any(), any());
    }

    @Test
    void processBookingCacheMiss() {
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, BOOKING_FIELD)).thenReturn(false);
        ProcessedRecording cleansedData = ProcessedRecording.builder().build();
        CreateCaseDTO aCase = new CreateCaseDTO();
        CreateBookingDTO booking = new CreateBookingDTO();

        when(entityCreationService.createBooking(cleansedData, aCase, BASE_KEY)).thenReturn(booking);

        CreateBookingDTO result = migrationGroupBuilderService.processBooking(BASE_KEY, cleansedData, aCase);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(booking);

        verify(inMemoryCacheService, times(1)).checkHashKeyExists(BASE_KEY, BOOKING_FIELD);
        verify(entityCreationService, times(1)).createBooking(cleansedData, aCase, BASE_KEY);
    }

    @Test
    void processCaptureSessionCacheHit() {
        ProcessedRecording cleansedData = ProcessedRecording.builder().build();
        CreateBookingDTO booking = new CreateBookingDTO();
        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, CAPTURE_SESSION_FIELD)).thenReturn(true);
        when(inMemoryCacheService.getHashValue(BASE_KEY, CAPTURE_SESSION_FIELD, CreateCaptureSessionDTO.class))
            .thenReturn(captureSession);

        CreateCaptureSessionDTO result = migrationGroupBuilderService.processCaptureSession(
            BASE_KEY,
            cleansedData,
            booking
        );

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(captureSession);

        verify(inMemoryCacheService, times(1)).checkHashKeyExists(BASE_KEY, CAPTURE_SESSION_FIELD);
        verify(inMemoryCacheService, times(1))
            .getHashValue(BASE_KEY, CAPTURE_SESSION_FIELD, CreateCaptureSessionDTO.class);
        verify(entityCreationService, times(0)).createCaptureSession(any(), any());
    }

    @Test
    void processCaptureSessionCacheMiss() {
        ProcessedRecording cleansedData = ProcessedRecording.builder().build();
        CreateBookingDTO booking = new CreateBookingDTO();
        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, CAPTURE_SESSION_FIELD)).thenReturn(false);
        when(entityCreationService.createCaptureSession(cleansedData, booking)).thenReturn(captureSession);

        CreateCaptureSessionDTO result = migrationGroupBuilderService.processCaptureSession(
            BASE_KEY,
            cleansedData,
            booking
        );

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(captureSession);

        verify(inMemoryCacheService, times(1)).checkHashKeyExists(BASE_KEY, CAPTURE_SESSION_FIELD);
        verify(entityCreationService, times(1)).createCaptureSession(cleansedData, booking);
    }

    @Test
    void processRecordingCacheHit() {
        ProcessedRecording cleansedData = ProcessedRecording.builder().build();
        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, RECORDING_FIELD)).thenReturn(true);
        when(inMemoryCacheService.getHashValue(BASE_KEY, RECORDING_FIELD, CreateRecordingDTO.class))
            .thenReturn(new CreateRecordingDTO());

        CreateRecordingDTO result = migrationGroupBuilderService.processRecording(
            BASE_KEY,
            cleansedData,
            captureSession
        );

        assertThat(result).isNotNull();

        verify(inMemoryCacheService, times(1)).checkHashKeyExists(BASE_KEY, RECORDING_FIELD);
        verify(inMemoryCacheService, times(1)).getHashValue(BASE_KEY, RECORDING_FIELD, CreateRecordingDTO.class);
        verify(entityCreationService, never()).createRecording(any(), any());
    }

    @Test
    void processRecordingCacheMiss() {
        ProcessedRecording cleansedData = ProcessedRecording.builder().build();
        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, RECORDING_FIELD)).thenReturn(false);
        when(entityCreationService.createRecording(cleansedData, captureSession))
            .thenReturn(new CreateRecordingDTO());

        CreateRecordingDTO result = migrationGroupBuilderService.processRecording(
            BASE_KEY,
            cleansedData,
            captureSession
        );

        assertThat(result).isNotNull();

        verify(inMemoryCacheService, times(1)).checkHashKeyExists(BASE_KEY, RECORDING_FIELD);
        verify(entityCreationService, times(1)).createRecording(cleansedData, captureSession);
    }

    @Test
    @DisplayName("Should return null if createCaseIfOrig returns null")
    void createMigratedItemGroupReturnsNullIfCaseIsNull() {
        ExtractedMetadata metadata = new ExtractedMetadata();
        ProcessedRecording recording = ProcessedRecording.builder().build();

        assertThat(migrationGroupBuilderService.createMigratedItemGroup(metadata, recording)).isNull();
    }

    @Test
    @DisplayName("Should create MigratedItemGroup successfully when all dependencies are satisfied")
    void createMigratedItemGroupHappyPath() {
        CreateCaseDTO caseDTO = new CreateCaseDTO();
        caseDTO.setReference(CASE_REFERENCE);

        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .witnessFirstName("Example")
            .defendantLastName("Example")
            .extractedRecordingVersion("ORIG")
            .origVersionNumberStr("1")
            .recordingVersionNumber(1)
            .extractedRecordingVersionNumberStr("1")
            .build();

        when(caseRepository.findAll()).thenReturn(List.of());
        applicationContext.publishEvent(new ContextRefreshedEvent(applicationContext));

        when(entityCreationService.createCase(cleansedData)).thenReturn(caseDTO);
        when(inMemoryCacheService.generateEntityCacheKey(
            eq("booking"),
            eq(CASE_REFERENCE),
            eq("Example"),
            eq("Example"),
            eq("1")
        )).thenReturn(BASE_KEY);

        CreateBookingDTO bookingDTO = new CreateBookingDTO();
        CreateCaptureSessionDTO captureSessionDTO = new CreateCaptureSessionDTO();
        CreateRecordingDTO recordingDTO = new CreateRecordingDTO();
        Set<CreateParticipantDTO> participants = Set.of(new CreateParticipantDTO());

        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, BOOKING_FIELD)).thenReturn(false);
        when(entityCreationService.createBooking(cleansedData, caseDTO, BASE_KEY)).thenReturn(bookingDTO);

        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, CAPTURE_SESSION_FIELD)).thenReturn(false);
        when(entityCreationService.createCaptureSession(cleansedData, bookingDTO))
            .thenReturn(captureSessionDTO);

        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, RECORDING_FIELD)).thenReturn(false);
        when(entityCreationService.createRecording(cleansedData, captureSessionDTO)).thenReturn(recordingDTO);

        when(entityCreationService.createParticipants(cleansedData)).thenReturn(participants);
        ExtractedMetadata metadata = new ExtractedMetadata();

        MigratedItemGroup result = migrationGroupBuilderService.createMigratedItemGroup(metadata, cleansedData);

        assertThat(result).isNotNull();
        assertThat(result.getCase()).isEqualTo(caseDTO);
        assertThat(result.getBooking()).isEqualTo(bookingDTO);
        assertThat(result.getCaptureSession()).isEqualTo(captureSessionDTO);
        assertThat(result.getRecording()).isEqualTo(recordingDTO);
        assertThat(result.getParticipants()).isEqualTo(participants);

    }


    @Test
    @DisplayName("Should return null and log error for COPY file with missing case")
    void createMigratedItemGroupCopyFileWithMissingCase() {
        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setFileName("test_COPY.mp4");
        
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference("INVALID_CASE")
            .fileName("test_COPY.mp4")
            .extractedRecordingVersion("COPY")  
            .build();

        when(inMemoryCacheService.getCase("INVALID_CASE")).thenReturn(Optional.empty());
        when(entityCreationService.createCase(any())).thenReturn(null);

        MigratedItemGroup result = migrationGroupBuilderService.createMigratedItemGroup(metadata, cleansedData);

        assertThat(result).isNull();
        
        verify(loggingService, times(1)).logError("COPY file with missing case in cache: %s", "test_COPY.mp4");
    }

    @Test
    @DisplayName("Should return null and log error for original file when case creation fails")
    void createMigratedItemGroupOriginalFileWithCaseCreationFailure() {
        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setFileName("test_original.mp4");
        
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference("INVALID_CASE")
            .fileName("test_original.mp4")
            .extractedRecordingVersion("ORIG") 
            .build();

        when(inMemoryCacheService.getCase("INVALID_CASE")).thenReturn(Optional.empty());
        when(entityCreationService.createCase(any())).thenReturn(null);

        MigratedItemGroup result = migrationGroupBuilderService.createMigratedItemGroup(metadata, cleansedData);

        assertThat(result).isNull();
        
        verify(loggingService, times(1)).logError("Failed to find or create case for file: %s", "test_original.mp4");
    }

    @Test
    @DisplayName("Should return null for COPY file when extractedRecordingVersion is null")
    void createMigratedItemGroupCopyFileWithNullVersion() {
        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setFileName("test.mp4");
        
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference("INVALID_CASE")
            .fileName("test.mp4")
            .extractedRecordingVersion(null)  
            .build();

        when(inMemoryCacheService.getCase("INVALID_CASE")).thenReturn(Optional.empty());
        when(entityCreationService.createCase(any())).thenReturn(null);

        MigratedItemGroup result = migrationGroupBuilderService.createMigratedItemGroup(metadata, cleansedData);

        assertThat(result).isNull();
        
        verify(loggingService, times(1)).logError("Failed to find or create case for file: %s", "test.mp4");
        verify(loggingService, never()).logError(eq("COPY file with missing case in cache: %s"), any());
    }

    @Test
    @DisplayName("Should add new participants when updating existing case")
    void updateExistingCaseWithNewParticipants() {
        
        CreateParticipantDTO existingParticipant = new CreateParticipantDTO();
        existingParticipant.setFirstName("John");
        existingParticipant.setLastName("Doe");
        existingParticipant.setParticipantType(uk.gov.hmcts.reform.preapi.enums.ParticipantType.WITNESS);
        
        CreateCaseDTO existingCase = new CreateCaseDTO(createCase());
        existingCase.setParticipants(Set.of(existingParticipant));
        
        when(inMemoryCacheService.getCase(CASE_REFERENCE)).thenReturn(Optional.of(existingCase));
        
        CreateParticipantDTO newParticipant = new CreateParticipantDTO();
        newParticipant.setFirstName("Jane");
        newParticipant.setLastName("Smith");
        newParticipant.setParticipantType(uk.gov.hmcts.reform.preapi.enums.ParticipantType.DEFENDANT);
        
        when(entityCreationService.createParticipants(any())).thenReturn(Set.of(newParticipant));
        
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .build();

        CreateCaseDTO result = migrationGroupBuilderService.createCaseIfOrig(cleansedData);
        
        assertThat(result).isNotNull();
        assertThat(result.getParticipants()).hasSize(2); 
        
        verify(inMemoryCacheService, times(1)).saveCase(eq(CASE_REFERENCE), any(CreateCaseDTO.class));
    }

    @Test
    @DisplayName("Should not update case when no new participants are added")
    void updateExistingCaseWithNoNewParticipants() {
        
        CreateParticipantDTO existingParticipant = new CreateParticipantDTO();
        existingParticipant.setFirstName("John");
        existingParticipant.setLastName("Doe");
        existingParticipant.setParticipantType(uk.gov.hmcts.reform.preapi.enums.ParticipantType.WITNESS);
        
        CreateCaseDTO existingCase = new CreateCaseDTO(createCase());
        existingCase.setParticipants(Set.of(existingParticipant));
        
        when(inMemoryCacheService.getCase(CASE_REFERENCE)).thenReturn(Optional.of(existingCase));
        
        when(entityCreationService.createParticipants(any())).thenReturn(Set.of(existingParticipant));
        
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .build();

        CreateCaseDTO result = migrationGroupBuilderService.createCaseIfOrig(cleansedData);
        
        assertThat(result).isNotNull();
        assertThat(result.getParticipants()).hasSize(1);
        
        verify(inMemoryCacheService, never()).saveCase(eq(CASE_REFERENCE), any(CreateCaseDTO.class));
    }

    @Test
    @DisplayName("Should handle case with null participants when updating")
    void updateExistingCaseWithNullParticipants() {
        CreateCaseDTO existingCase = new CreateCaseDTO(createCase());
        existingCase.setParticipants(null); 
        
        when(inMemoryCacheService.getCase(CASE_REFERENCE)).thenReturn(Optional.of(existingCase));
        
        CreateParticipantDTO newParticipant = new CreateParticipantDTO();
        newParticipant.setFirstName("Jane");
        newParticipant.setLastName("Smith");
        newParticipant.setParticipantType(uk.gov.hmcts.reform.preapi.enums.ParticipantType.DEFENDANT);
        
        when(entityCreationService.createParticipants(any())).thenReturn(Set.of(newParticipant));
        
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .build();

        CreateCaseDTO result = migrationGroupBuilderService.createCaseIfOrig(cleansedData);
        
        assertThat(result).isNotNull();
        assertThat(result.getParticipants()).hasSize(1); 
        
        verify(inMemoryCacheService, times(1)).saveCase(eq(CASE_REFERENCE), any(CreateCaseDTO.class));
    }

    private Case createCase() {
        Court court = new Court();
        court.setId(UUID.randomUUID());
        court.setCourtType(CourtType.CROWN);
        court.setName("Example Court");
        Case aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);
        aCase.setOrigin(RecordingOrigin.PRE);
        aCase.setReference(CASE_REFERENCE);
        aCase.setTest(false);
        return aCase;
    }

    @Test
    @DisplayName("Should skip migration and add failed item when case has deleted participants")
    void createMigratedItemGroup_skips_whenCaseHasDeletedParticipants() {
        CreateCaseDTO cachedCase = new CreateCaseDTO(); //pre-existing case
        cachedCase.setReference(CASE_REFERENCE);
        when(inMemoryCacheService.getCase(CASE_REFERENCE)).thenReturn(Optional.of(cachedCase));
        
        Participant deletedP = new Participant();
        deletedP.setParticipantType(ParticipantType.WITNESS);
        deletedP.setFirstName("Deleted");
        deletedP.setLastName("Person");
        deletedP.setDeletedAt(Timestamp.from(Instant.now()));

        Case dbCase = createCase();
        dbCase.setParticipants(Set.of(deletedP));
        when(caseRepository.findAllByReference(CASE_REFERENCE)).thenReturn(List.of(dbCase));

        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .extractedRecordingVersion("ORIG")
            .build();
        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setArchiveId("ARCH-1");
        metadata.setFileName("file.mp4");

        MigratedItemGroup result = migrationGroupBuilderService.createMigratedItemGroup(metadata, cleansedData);

        assertThat(result).isNull();
        verify(loggingService, times(1)).logWarning(
            eq("Skipping migration for archiveId=%s: %s"),
            eq("ARCH-1"),
            anyString()
        );
        verify(migrationTrackerService, times(1)).addFailedItem(any());
        verify(entityCreationService, never()).createBooking(any(), any(), any());
        verify(entityCreationService, never()).createCaptureSession(any(), any());
        verify(entityCreationService, never()).createRecording(any(), any());
        verify(migrationRecordService, never()).updateToSuccess(any());
    }

    @Test
    @DisplayName("Should continue migration when case has no deleted participants (guard false)")
    void createMigratedItemGroup_continues_whenCaseHasNoDeletedParticipants() {
        CreateCaseDTO cachedCase = new CreateCaseDTO();
        cachedCase.setReference(CASE_REFERENCE);
        when(inMemoryCacheService.getCase(CASE_REFERENCE)).thenReturn(Optional.of(cachedCase));
        
        Participant activeP = new Participant();
        activeP.setParticipantType(ParticipantType.WITNESS);
        activeP.setFirstName("Active");
        activeP.setLastName("Person");
        activeP.setDeletedAt(null);

        Case dbCase = createCase();
        dbCase.setParticipants(Set.of(activeP));
        when(caseRepository.findAllByReference(CASE_REFERENCE)).thenReturn(List.of(dbCase));

        when(inMemoryCacheService.generateEntityCacheKey(
            eq("booking"), eq(CASE_REFERENCE), eq("Doe"), eq("John"), eq("1")))
            .thenReturn(BASE_KEY);

        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, BOOKING_FIELD)).thenReturn(false);
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, CAPTURE_SESSION_FIELD)).thenReturn(false);
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, RECORDING_FIELD)).thenReturn(false);

        CreateBookingDTO bookingDTO = new CreateBookingDTO();
        CreateCaptureSessionDTO csDTO = new CreateCaptureSessionDTO();
        CreateRecordingDTO recDTO = new CreateRecordingDTO();
        Set<CreateParticipantDTO> parts = Set.of(new CreateParticipantDTO());

        when(entityCreationService.createBooking(any(), any(), any())).thenReturn(bookingDTO);
        when(entityCreationService.createCaptureSession(any(), any())).thenReturn(csDTO);
        when(entityCreationService.createRecording(any(), any())).thenReturn(recDTO);
        when(entityCreationService.createParticipants(any())).thenReturn(parts);

        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .defendantLastName("Doe")
            .witnessFirstName("John")
            .extractedRecordingVersion("ORIG")
            .origVersionNumberStr("1")
            .recordingVersionNumber(1)
            .extractedRecordingVersionNumberStr("1")
            .build();

        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setArchiveId("ARCH-2");
        metadata.setFileName("ok.mp4");

        MigratedItemGroup result = migrationGroupBuilderService.createMigratedItemGroup(metadata, cleansedData);

        assertThat(result).isNotNull();
        assertThat(result.getCase()).isEqualTo(cachedCase);
        assertThat(result.getBooking()).isEqualTo(bookingDTO);
        assertThat(result.getCaptureSession()).isEqualTo(csDTO);
        assertThat(result.getRecording()).isEqualTo(recDTO);
        assertThat(result.getParticipants()).isEqualTo(parts);
        verify(migrationTrackerService, never()).addFailedItem(any());
        verify(migrationRecordService, times(1)).updateToSuccess("ARCH-2");
    }
}
