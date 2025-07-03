package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private CaseRepository caseRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MigrationGroupBuilderService migrationGroupBuilderService;

    private static final String CASE_REFERENCE = "CASE_REFERENCE";
    private static final String BASE_KEY = "baseKey";

    @AfterEach
    void tearDown() {
        migrationGroupBuilderService.caseCache.clear();
    }

    // todo createMigratedItemGroup

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
        Case existingCase = createCase();
        when(caseRepository.findAll()).thenReturn(List.of(existingCase));
        applicationContext.publishEvent(new ContextRefreshedEvent(applicationContext));
        when(entityCreationService.createParticipants(any())).thenReturn(Set.of());
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .build();

        CreateCaseDTO result = migrationGroupBuilderService.createCaseIfOrig(cleansedData);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should return new case if doesn't already exist but is created successfully")
    void createCaseIfOrigCreateNewCaseSuccess() {
        when(caseRepository.findAll()).thenReturn(List.of());
        applicationContext.publishEvent(new ContextRefreshedEvent(applicationContext));
        CreateCaseDTO newCase = new CreateCaseDTO();
        when(entityCreationService.createCase(any())).thenReturn(newCase);
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .build();

        CreateCaseDTO result = migrationGroupBuilderService.createCaseIfOrig(cleansedData);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(newCase);
        assertThat(migrationGroupBuilderService.caseCache.containsKey(CASE_REFERENCE)).isTrue();
        assertThat(migrationGroupBuilderService.caseCache.get(CASE_REFERENCE)).isEqualTo(newCase);
    }

    @Test
    @DisplayName("Should return null if doesn't already exist but new case cannot be created")
    void createCaseIfOrigCreateNewCaseFailure() {
        when(caseRepository.findAll()).thenReturn(List.of());
        applicationContext.publishEvent(new ContextRefreshedEvent(applicationContext));
        when(entityCreationService.createCase(any())).thenReturn(null);
        ProcessedRecording cleansedData = ProcessedRecording.builder()
            .caseReference(CASE_REFERENCE)
            .build();

        CreateCaseDTO result = migrationGroupBuilderService.createCaseIfOrig(cleansedData);
        assertThat(result).isNull();
        assertThat(migrationGroupBuilderService.caseCache.containsKey(CASE_REFERENCE)).isFalse();
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
        verify(entityCreationService, times(0)).createCaptureSession(any(), any(), any());
    }

    @Test
    void processCaptureSessionCacheMiss() {
        ProcessedRecording cleansedData = ProcessedRecording.builder().build();
        CreateBookingDTO booking = new CreateBookingDTO();
        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, CAPTURE_SESSION_FIELD)).thenReturn(false);
        when(entityCreationService.createCaptureSession(cleansedData, booking, BASE_KEY)).thenReturn(captureSession);

        CreateCaptureSessionDTO result = migrationGroupBuilderService.processCaptureSession(
            BASE_KEY,
            cleansedData,
            booking
        );

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(captureSession);

        verify(inMemoryCacheService, times(1)).checkHashKeyExists(BASE_KEY, CAPTURE_SESSION_FIELD);
        verify(entityCreationService, times(1)).createCaptureSession(cleansedData, booking, BASE_KEY);
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
        verify(entityCreationService, times(0)).createRecording(any(), any(), any());
    }

    @Test
    void processRecordingCacheMiss() {
        ProcessedRecording cleansedData = ProcessedRecording.builder().build();
        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, RECORDING_FIELD)).thenReturn(false);
        when(entityCreationService.createRecording(BASE_KEY, cleansedData, captureSession))
            .thenReturn(new CreateRecordingDTO());

        CreateRecordingDTO result = migrationGroupBuilderService.processRecording(
            BASE_KEY,
            cleansedData,
            captureSession
        );

        assertThat(result).isNotNull();

        verify(inMemoryCacheService, times(1)).checkHashKeyExists(BASE_KEY, RECORDING_FIELD);
        verify(entityCreationService, times(1)).createRecording(BASE_KEY, cleansedData, captureSession);
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
        when(entityCreationService.createCaptureSession(cleansedData, bookingDTO, BASE_KEY))
            .thenReturn(captureSessionDTO);

        when(inMemoryCacheService.checkHashKeyExists(BASE_KEY, RECORDING_FIELD)).thenReturn(false);
        when(entityCreationService.createRecording(BASE_KEY, cleansedData, captureSessionDTO)).thenReturn(recordingDTO);

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
}
