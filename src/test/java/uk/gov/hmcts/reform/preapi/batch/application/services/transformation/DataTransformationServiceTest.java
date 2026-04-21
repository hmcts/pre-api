package uk.gov.hmcts.reform.preapi.batch.application.services.transformation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = DataTransformationService.class)
public class DataTransformationServiceTest {
    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private CourtRepository courtRepository;

    @MockitoBean
    private LoggingService loggingService;

    @MockitoBean
    private MigrationRecordService migrationRecordService;

    @Autowired
    private DataTransformationService dataTransformationService;

    private static final Map<String, String> SITES_DATA_MAP = Map.of(
        "court_one", "Court One"
    );

    private static final String ARCHIVE_ID = "archiveID";
    private static final String ARCHIVE_NAME = "archiveName";

    private static MockedStatic<RecordingUtils> mockedRecordingUtils;

    @BeforeAll
    static void beforeAll() {
        mockedRecordingUtils = mockStatic(RecordingUtils.class);
    }

    @BeforeEach
    void setUp() {
        when(cacheService.getAllSiteReferences()).thenReturn(SITES_DATA_MAP);
        List<String[]> usersAndEmails = Arrays.asList(
            new String[]{"example.one", "example.one@example.com"},
            new String[]{"example.two", "example.two@example.com"}
        );

        when(cacheService.getAllChannelReferences())
            .thenReturn(Collections.singletonMap(ARCHIVE_NAME, usersAndEmails));
    }

    @AfterAll
    public static void tearDown() {
        mockedRecordingUtils.close();
    }

    @Test
    @DisplayName("Should return failure on transformation when data is null")
    void transformDataIsNullFailure() {
        ServiceResult<ProcessedRecording> result = dataTransformationService.transformData(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Extracted item cannot be null");

        verify(loggingService).logError("Extracted item is null");
    }

    @Test
    @DisplayName("Should successfully get site data")
    void getSitesDataSuccess() {
        assertThat(dataTransformationService.getSitesData()).isNotNull();

        verify(cacheService, times(1)).getAllSiteReferences();
        verifyNoInteractions(loggingService);
    }

    @Test
    @DisplayName("Should return log error when site data is empty")
    void getSitesDataEmpty() {
        when(cacheService.getAllSiteReferences()).thenReturn(Map.of());

        Map<String, String> result = dataTransformationService.getSitesData();

        assertThat(result).isEmpty();

        verify(cacheService, times(1)).getAllSiteReferences();
        verify(loggingService, times(1)).logError("Sites data not found in Cache");
    }

    @Test
    @DisplayName("Should determine case is CLOSED when no contacts found")
    void determineStateClosed() {
        List<Map<String, String>> contacts = List.of();

        assertThat(dataTransformationService.determineState(contacts)).isEqualTo(CaseState.CLOSED);
    }

    @Test
    @DisplayName("Should determine case is OPEN when contacts found")
    void determineStateOpen() {
        List<Map<String, String>> contacts = List.of(Map.of("a", "b"));

        assertThat(dataTransformationService.determineState(contacts)).isEqualTo(CaseState.OPEN);
    }

    @Test
    @DisplayName("Should return list of emails when key found")
    void getUsersAndEmailsUserDataFoundInCacheForKey() {
        String key = "some-key";
        List<String[]> userEmails = new ArrayList<>();
        userEmails.add(new String[] {"example@example.com"});
        when(cacheService.getChannelReference(key)).thenReturn(Optional.of(userEmails));

        List<String[]> result = dataTransformationService.getUsersAndEmails(key);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst()[0]).isEqualTo("example@example.com");
    }

    @Test
    @DisplayName("Should return empty list when key not found in map")
    void getUsersAndEmailsUserDataInCacheNull() {
        String key = "some-key";
        when(cacheService.getChannelReference(key)).thenReturn(Optional.empty());

        List<String[]> result = dataTransformationService.getUsersAndEmails(key);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should build share booking contacts successfully")
    void buildShareBookingContactsSuccess() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setArchiveName(ARCHIVE_NAME);

        List<String[]> usersAndEmails = Arrays.asList(
            new String[]{"example.one", "example.one@example.com"},
            new String[]{"example.two", "example.two@example.com"}
        );

        when(cacheService.getChannelReference(ARCHIVE_NAME)).thenReturn(Optional.of(usersAndEmails));

        List<Map<String, String>> contacts = dataTransformationService.buildShareBookingContacts(data);

        assertThat(contacts).isNotNull();
        assertThat(contacts.size()).isEqualTo(2);
        Map<String, String> firstContact = contacts.getFirst();
        assertThat(firstContact.get("firstName")).isEqualTo("example");
        assertThat(firstContact.get("lastName")).isEqualTo("one");
        assertThat(firstContact.get("email")).isEqualTo("example.one@example.com");
        Map<String, String> secondContact = contacts.getLast();
        assertThat(secondContact.get("firstName")).isEqualTo("example");
        assertThat(secondContact.get("lastName")).isEqualTo("two");
        assertThat(secondContact.get("email")).isEqualTo("example.two@example.com");
    }

    @Test
    @DisplayName("Should build share booking contacts successfully when list is empty")
    void shouldHandleEmptyUserList() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setArchiveName(ARCHIVE_NAME);
        when(cacheService.getChannelReference(ARCHIVE_NAME)).thenReturn(Optional.empty());

        List<Map<String, String>> contacts = dataTransformationService.buildShareBookingContacts(data);
        assertThat(contacts).isEmpty();
    }

    @Test
    @DisplayName("Should build share booking contacts successfully when data malformed")
    void buildShareBookingContactsMalformedSuccess() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setArchiveName(ARCHIVE_NAME);
        List<String[]> usersAndEmails = Arrays.asList(
            new String[]{"exampleOne", "example.one@example.com"},
            new String[]{"exampleTwo", "example.two@example.com"}
        );

        when(cacheService.getChannelReference(ARCHIVE_NAME)).thenReturn(Optional.of(usersAndEmails));

        List<Map<String, String>> contacts = dataTransformationService.buildShareBookingContacts(data);

        assertThat(contacts).isNotNull();
        assertThat(contacts.size()).isEqualTo(2);
        Map<String, String> firstContact = contacts.getFirst();
        assertThat(firstContact.get("firstName")).isEqualTo("exampleOne");
        assertThat(firstContact.get("lastName")).isEqualTo("");
        assertThat(firstContact.get("email")).isEqualTo("example.one@example.com");
        Map<String, String> secondContact = contacts.getLast();
        assertThat(secondContact.get("firstName")).isEqualTo("exampleTwo");
        assertThat(secondContact.get("lastName")).isEqualTo("");
        assertThat(secondContact.get("email")).isEqualTo("example.two@example.com");
    }

    @Test
    @DisplayName("Should throw error when court data is empty")
    void fetchCourtFromDBCourtDataEmptyError() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");
        when(cacheService.getCourt("court_one")).thenReturn(null);

        assertThat(dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP)).isNull();

        verify(loggingService).logWarning(eq("Court not found in cache or DB for name: %s"), eq("Court One"));
    }

    @Test
    @DisplayName("Should return null when court id is null")
    void fetchCourtFromDBCourtIdIsNullWarn() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");

        Map<String, String> courtData = new HashMap<>();
        courtData.put("court_one", null);
        when(cacheService.getAllSiteReferences())
            .thenReturn(courtData);

        Court result = dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP);
        assertThat(result).isNull();

        verify(cacheService).getCourt("Court One");
        verify(loggingService).logWarning(eq("Court not found in cache or DB for name: %s"), eq("Court One"));
    }

    @Test
    @DisplayName("Should return null when court is not found")
    void fetchCourtFromDBCourtNotFoundNull() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");
        UUID courtId = UUID.randomUUID();
        Map<String, String> courtData = new HashMap<>();
        courtData.put("Court One", courtId.toString());
        when(cacheService.getAllSiteReferences())
            .thenReturn(courtData);
        when(cacheService.getCourt("Court One")).thenReturn(Optional.empty());

        Court result = dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP);
        assertThat(result).isNull();

        verify(cacheService).getCourt("Court One");
    }

    @Test
    @DisplayName("Should return court when court is found")
    void fetchCourtFromDBCourtSuccess() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");
        UUID courtId = UUID.randomUUID();
        Map<String, String> courtData = new HashMap<>();
        courtData.put("Court One", courtId.toString());
        CourtDTO court = new CourtDTO();
        court.setId(courtId);
        when(cacheService.getCourt("Court One")).thenReturn(Optional.of(court));
        when(courtRepository.findById(courtId)).thenReturn(Optional.of(new Court()));

        Court result = dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP);
        assertThat(result).isNotNull();

        verify(courtRepository).findById(courtId);
    }

    @Test
    @DisplayName("Should successfully build processed recording with COPY version")
    void buildProcessedRecordingWithCopyVersionSuccess() {
        mockedRecordingUtils.when(() -> RecordingUtils.normalizeVersionType(any())).thenReturn("COPY");
        mockedRecordingUtils.when(() -> RecordingUtils.getValidVersionNumber(any())).thenReturn("2.1");

        ExtractedMetadata data = new ExtractedMetadata(
            "court_reference",
            UUID.randomUUID(),
            "200101",
            "URN123",
            "exhibitRef",
            "defendantName",
            "witnessName",
            "COPY",
            "2.1",
            ".mp4",
            LocalDateTime.now(),
            600,
            "recording.mp4",
            "12345",
            "ARCHIVE_ID",
            "ARCHIVE_NAME",
            null
        );

        Map<String, String> sitesDataMap = Map.of("court_reference", "Court Name");
        when(migrationRecordService.findOrigVersionsByBaseGroupKey(anyString()))
            .thenReturn(List.of("1", "2"));
        when(migrationRecordService.deduplicatePreferredByArchiveId(anyString())).thenReturn(true);

        ProcessedRecording recording = dataTransformationService.buildProcessedRecording(data, sitesDataMap);

        assertThat(recording).isNotNull();
        assertThat(recording.getExtractedRecordingVersion()).isEqualTo("COPY");
        assertThat(recording.getOrigVersionNumberStr()).isEqualTo("2");
        assertThat(recording.getCopyVersionNumberStr()).isEqualTo("1");
        verify(migrationRecordService, times(1)).updateIsPreferred(data.getArchiveId(), true);
    }

    @Test
    @DisplayName("Should successfully build processed recording with ORIG version")
    void buildProcessedRecordingWithOrigVersionSuccess() {
        mockedRecordingUtils.when(() -> RecordingUtils.normalizeVersionType(any())).thenReturn("ORIG");
        mockedRecordingUtils.when(() -> RecordingUtils.getValidVersionNumber(any())).thenReturn("1");

        ExtractedMetadata data = new ExtractedMetadata(
            "court_reference",
            UUID.randomUUID(),
            "200101",
            "URN123",
            "exhibitRef",
            "defendantName",
            "witnessName",
            "ORIG",
            "1",
            ".mp4",
            LocalDateTime.now(),
            600,
            "recording.mp4",
            "12345",
            "ARCHIVE_ID",
            "ARCHIVE_NAME",
            null
        );

        Map<String, String> sitesDataMap = Map.of("court_reference", "Court Name");

        ProcessedRecording recording = dataTransformationService.buildProcessedRecording(data, sitesDataMap);

        assertThat(recording).isNotNull();
        assertThat(recording.getExtractedRecordingVersion()).isEqualTo("ORIG");
        assertThat(recording.getOrigVersionNumberStr()).isEqualTo("1");
        assertThat(recording.getCopyVersionNumberStr()).isNull();
    }

    @Test
    @DisplayName("Should handle unknown court while building processed recording")
    void buildProcessedRecordingWithUnknownCourtSuccess() {
        ExtractedMetadata data = new ExtractedMetadata(
            "unknown_court",
            UUID.randomUUID(),
            "200101",
            "URN123",
            "exhibitRef",
            "defendantName",
            "witnessName",
            "ORIG",
            "1",
            ".mp4",
            LocalDateTime.now(),
            600,
            "recording.mp4",
            "12345",
            "ARCHIVE_ID",
            "ARCHIVE_NAME",
            null
        );

        Map<String, String> sitesDataMap = Map.of();

        ProcessedRecording recording = dataTransformationService.buildProcessedRecording(data, sitesDataMap);

        assertThat(recording).isNotNull();
        assertThat(recording.getFullCourtName()).isNull();
        verify(loggingService, times(1))
            .logWarning(eq("Court not found for reference: %s"), eq(data.getCourtReference()));
    }

    @Test
    @DisplayName("Should validate version normalization during buildProcessedRecording")
    void buildProcessedRecordingWithVersionNormalization() {
        mockedRecordingUtils.when(() -> RecordingUtils.normalizeVersionType(any())).thenReturn("COPY");
        mockedRecordingUtils.when(() -> RecordingUtils.getValidVersionNumber(any())).thenReturn("2");

        ExtractedMetadata data = new ExtractedMetadata(
            "court_reference",
            UUID.randomUUID(),
            "200101",
            "URN123",
            "exhibitRef",
            "defendantName",
            "witnessName",
            "COPY",
            "unknown-version",
            ".mp4",
            LocalDateTime.now(),
            600,
            "recording.mp4",
            "12345",
            "ARCHIVE_ID",
            "ARCHIVE_NAME",
            null
        );

        Map<String, String> sitesDataMap = Map.of("court_reference", "Court Name");
        when(migrationRecordService.findOrigVersionsByBaseGroupKey(anyString())).thenReturn(List.of());
        when(migrationRecordService.deduplicatePreferredByArchiveId(anyString())).thenReturn(true);
        when(RecordingUtils.getValidVersionNumber("unknown-version")).thenReturn("1");

        ProcessedRecording recording = dataTransformationService.buildProcessedRecording(data, sitesDataMap);

        assertThat(recording).isNotNull();
        assertThat(recording.getExtractedRecordingVersionNumberStr()).isEqualTo("1");
        verify(migrationRecordService, times(1)).updateIsPreferred(data.getArchiveId(), true);
    }


    @Test
    @DisplayName("Should successfully transform extracted data into processed recording")
    void transformDataSuccess() {
        ExtractedMetadata extractedMetadata = new ExtractedMetadata(
            "courtReference",
            UUID.randomUUID(),
            "200101",
            "URN123",
            "exhibitRef123",
            "defendantName",
            "witnessName",
            "ORIG",
            "1.0",
            ".mp4",
            LocalDateTime.now(),
            600,
            "recording.mp4",
            "123456",
            "ARCHIVE_ID",
            "ARCHIVE_NAME",
            null
        );

        when(cacheService.getAllSiteReferences()).thenReturn(SITES_DATA_MAP);
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .archiveId(extractedMetadata.getArchiveId())
            .archiveName(extractedMetadata.getArchiveName())
            .build();

        ServiceResult<ProcessedRecording> result = dataTransformationService.transformData(extractedMetadata);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("Should return failure when exception occurs during transformation")
    void transformDataWhenExceptionOccurs() {
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();

        when(cacheService.getAllSiteReferences()).thenThrow(new RuntimeException("Transformation error"));
        ServiceResult<ProcessedRecording> result = dataTransformationService.transformData(extractedMetadata);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Transformation error");
    }
}
