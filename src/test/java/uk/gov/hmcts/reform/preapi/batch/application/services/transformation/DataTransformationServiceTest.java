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
import uk.gov.hmcts.reform.preapi.batch.application.processor.ReferenceDataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.time.Duration;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = DataTransformationService.class)
public class DataTransformationServiceTest {
    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private CourtRepository courtRepository;

    @MockitoBean
    private ReferenceDataProcessor referenceDataProcessor;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private DataTransformationService dataTransformationService;

    private static final Map<String, Object> SITES_DATA_MAP = Map.of(
        "court_one", "Court One"
    );

    private static final String ARCHIVE_NAME = "archiveName";

    private static MockedStatic<RecordingUtils> mockedRecordingUtils;

    @BeforeAll
    static void beforeAll() {
        mockedRecordingUtils = mockStatic(RecordingUtils.class);
    }

    @BeforeEach
    void setUp() {
        when(cacheService.getHashAll(Constants.CacheKeys.SITES_DATA)).thenReturn(SITES_DATA_MAP);
        List<String[]> usersAndEmails = Arrays.asList(
            new String[]{"example.one", "example.one@example.com"},
            new String[]{"example.two", "example.two@example.com"}
        );

        when(referenceDataProcessor.fetchChannelUserDataMap())
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
    @DisplayName("Should return failure when cannot find sites data")
    void transformDataSitesDataNotFound() {
        when(cacheService.getHashAll(Constants.CacheKeys.SITES_DATA))
            .thenReturn(null);
        var data = new ExtractedMetadata();
        data.setArchiveName(ARCHIVE_NAME);

        ServiceResult<ProcessedRecording> result = dataTransformationService.transformData(data);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Sites data not found in Cache");

        verify(cacheService).getHashAll(Constants.CacheKeys.SITES_DATA);
        verify(loggingService).logError(eq("Data transformation failed for archive: %s - %s"),
                                        eq(ARCHIVE_NAME),
                                        any(IllegalStateException.class));
    }

    @Test
    @DisplayName("Should successfully get site data")
    void getSitesDataSuccess() {
        assertThat(dataTransformationService.getSitesData()).isNotNull();
    }

    @Test
    @DisplayName("Should throw error when attempting to get site data but not found")
    void getSitesDataFailure() {
        when(cacheService.getHashAll(Constants.CacheKeys.SITES_DATA))
            .thenReturn(null);
        String message1 = assertThrows(
            IllegalStateException.class,
            () -> dataTransformationService.getSitesData()
        ).getMessage();
        assertThat(message1).isEqualTo("Sites data not found in Cache");

        when(cacheService.getHashAll(Constants.CacheKeys.SITES_DATA))
            .thenReturn(Map.of());
        String message2 = assertThrows(
            IllegalStateException.class,
            () -> dataTransformationService.getSitesData()
        ).getMessage();
        assertThat(message2).isEqualTo("Sites data not found in Cache");

        verify(loggingService, times(2)).logError("Sites data not found in Cache");
    }

    @Test
    @DisplayName("Should return list of emails when key found")
    void getUsersAndEmailsUserDataFoundInCacheForKey() {
        String key = "some-key";
        Map<String, List<String[]>> userDataMap = new HashMap<>();
        List<String[]> userEmails = new ArrayList<>();
        userEmails.add(new String[]{"example@example.com"});
        userDataMap.put(key, userEmails);
        when(referenceDataProcessor.fetchChannelUserDataMap())
            .thenReturn(userDataMap);

        List<String[]> result = dataTransformationService.getUsersAndEmails(key);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst()[0]).isEqualTo("example@example.com");
    }

    @Test
    @DisplayName("Should return empty list when key not found in map")
    void getUsersAndEmailsUserDataFoundInCacheKeyNotFound() {
        String key = "some-key";
        Map<String, List<String[]>> userDataMap = new HashMap<>();
        when(referenceDataProcessor.fetchChannelUserDataMap())
            .thenReturn(userDataMap);

        List<String[]> result = dataTransformationService.getUsersAndEmails(key);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return empty list when key not found in map")
    void getUsersAndEmailsUserDataInCacheNull() {
        String key = "some-key";
        when(referenceDataProcessor.fetchChannelUserDataMap())
            .thenReturn(null);

        List<String[]> result = dataTransformationService.getUsersAndEmails(key);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);

        verify(loggingService).logWarning(eq("Channel user data map is null"));
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

        when(referenceDataProcessor.fetchChannelUserDataMap())
            .thenReturn(Collections.singletonMap(ARCHIVE_NAME, usersAndEmails));

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
        when(referenceDataProcessor.fetchChannelUserDataMap()).thenReturn(Collections.emptyMap());

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

        when(referenceDataProcessor.fetchChannelUserDataMap())
            .thenReturn(Collections.singletonMap(ARCHIVE_NAME, usersAndEmails));

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
    @DisplayName("Should throw error when court reference is null")
    void fetchCourtFromDBCourtReferenceNullError() {
        ExtractedMetadata data = new ExtractedMetadata();

        String message = assertThrows(
            IllegalArgumentException.class,
            () -> dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP)
        ).getMessage();
        assertThat(message).isEqualTo("Court reference cannot be null or empty");

        verify(loggingService).logError(eq("Court reference is null or empty"));
    }

    @Test
    @DisplayName("Should throw error when court reference is empty")
    void fetchCourtFromDBCourtReferenceEmptyError() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("");

        String message = assertThrows(
            IllegalArgumentException.class,
            () -> dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP)
        ).getMessage();
        assertThat(message).isEqualTo("Court reference cannot be null or empty");

        verify(loggingService).logError(eq("Court reference is null or empty"));
    }

    @Test
    @DisplayName("Should throw error when court data is null")
    void fetchCourtFromDBCourtDataNullError() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");
        when(cacheService.getHashAll(Constants.CacheKeys.COURTS_PREFIX))
            .thenReturn(null);

        String message = assertThrows(
            IllegalStateException.class,
            () -> dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP)
        ).getMessage();
        assertThat(message).isEqualTo("Courts data not found in Cache");

        verify(cacheService).getHashAll(Constants.CacheKeys.COURTS_PREFIX);
        verify(loggingService).logError(eq("Courts data not found in Cache"));
    }

    @Test
    @DisplayName("Should throw error when court data is empty")
    void fetchCourtFromDBCourtDataEmptyError() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");
        when(cacheService.getHashAll(Constants.CacheKeys.COURTS_PREFIX))
            .thenReturn(Map.of());

        String message = assertThrows(
            IllegalStateException.class,
            () -> dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP)
        ).getMessage();
        assertThat(message).isEqualTo("Courts data not found in Cache");

        verify(cacheService).getHashAll(Constants.CacheKeys.COURTS_PREFIX);
        verify(loggingService).logError(eq("Courts data not found in Cache"));
    }

    @Test
    @DisplayName("Should return null when court id is null")
    void fetchCourtFromDBCourtIdIsNullWarn() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");

        Map<String, Object> courtData = new HashMap<>();
        courtData.put("court_one", null);
        when(cacheService.getHashAll(Constants.CacheKeys.COURTS_PREFIX))
            .thenReturn(courtData);

        Court result = dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP);
        assertThat(result).isNull();

        verify(cacheService).getHashAll(Constants.CacheKeys.COURTS_PREFIX);
        verify(loggingService).logWarning(eq("Court ID not found for court name: %s"), eq("Court One"));
    }

    @Test
    @DisplayName("Should throw error when court id is not a uuid")
    void fetchCourtFromDBCourtIdNotUuidError() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");

        Map<String, Object> courtData = new HashMap<>();
        courtData.put("Court One", "invalid-uuid");
        when(cacheService.getHashAll(Constants.CacheKeys.COURTS_PREFIX))
            .thenReturn(courtData);

        String message = assertThrows(
            IllegalArgumentException.class,
            () -> dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP)
        ).getMessage();
        assertThat(message).isEqualTo("Court ID parsing failed for: invalid-uuid");

        verify(cacheService).getHashAll(Constants.CacheKeys.COURTS_PREFIX);
        verify(loggingService)
            .logError(eq("Invalid court ID format: $s - $s"), eq("invalid-uuid"), any(IllegalArgumentException.class));
    }

    @Test
    @DisplayName("Should return null when court is not found")
    void fetchCourtFromDBCourtNotFoundNull() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");
        UUID courtId = UUID.randomUUID();
        Map<String, Object> courtData = new HashMap<>();
        courtData.put("Court One", courtId.toString());
        when(cacheService.getHashAll(Constants.CacheKeys.COURTS_PREFIX))
            .thenReturn(courtData);
        when(courtRepository.findById(courtId)).thenReturn(Optional.empty());

        Court result = dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP);
        assertThat(result).isNull();

        verify(cacheService).getHashAll(Constants.CacheKeys.COURTS_PREFIX);
        verify(courtRepository).findById(courtId);
    }

    @Test
    @DisplayName("Should return court when court is found")
    void fetchCourtFromDBCourtSuccess() {
        ExtractedMetadata data = new ExtractedMetadata();
        data.setCourtReference("court_one");
        UUID courtId = UUID.randomUUID();
        Map<String, Object> courtData = new HashMap<>();
        courtData.put("Court One", courtId.toString());
        when(cacheService.getHashAll(Constants.CacheKeys.COURTS_PREFIX))
            .thenReturn(courtData);
        when(courtRepository.findById(courtId)).thenReturn(Optional.of(new Court()));

        Court result = dataTransformationService.fetchCourtFromDB(data, SITES_DATA_MAP);
        assertThat(result).isNotNull();

        verify(cacheService).getHashAll(Constants.CacheKeys.COURTS_PREFIX);
        verify(courtRepository).findById(courtId);
    }

    @Test
    @DisplayName("Should successfully build processed recording when court is not found (with warning)")
    void buildProcessedRecordingNoCourtSuccess() {
        ExtractedMetadata data = new ExtractedMetadata(
            "court_one",
            "urn123",
            "exhibitReference",
            "defendantLastName",
            "witnessFirstName",
            "1",
            "1",
            ".mp4",
            LocalDateTime.now(),
            3000,
            "filename.mp4",
            "12345",
            ARCHIVE_NAME
        );

        String key = "vf:pre-process:urn123-defendantLastName-witnessFirstName";
        mockedRecordingUtils.when(() -> RecordingUtils.buildMetadataPreprocessKey(any(), any(), any()))
            .thenReturn(key);
        when(cacheService.getHashAll(key))
            .thenReturn(Map.of());
        mockedRecordingUtils.when(() -> RecordingUtils.processVersioning(any(), any(), any(), any(), any(), any()))
            .thenReturn(new RecordingUtils.VersionDetails("versionType", "1", 1, true));
        when(cacheService.getHashAll(key))
            .thenReturn(Collections.singletonMap(ARCHIVE_NAME, data));

        Map<String, Object> courtData = new HashMap<>();
        courtData.put("Court One", null);
        when(cacheService.getHashAll(Constants.CacheKeys.COURTS_PREFIX))
            .thenReturn(courtData);

        ProcessedRecording result = dataTransformationService.buildProcessedRecording(data, SITES_DATA_MAP);
        assertThat(result.getUrn()).isEqualTo(data.getUrn());
        assertThat(result.getExhibitReference()).isEqualTo(data.getExhibitReference());
        assertThat(result.getDefendantLastName()).isEqualTo(data.getDefendantLastName());
        assertThat(result.getWitnessFirstName()).isEqualTo(data.getWitnessFirstName());
        assertThat(result.getCourtReference()).isEqualTo(data.getCourtReference());
        assertThat(result.getCourt()).isNull();
        assertThat(result.getRecordingTimestamp()).isNotNull();
        assertThat(result.getDuration()).isEqualTo(Duration.ofSeconds(data.getDuration()));
        assertThat(result.getState()).isEqualTo(CaseState.OPEN);
        assertThat(result.getShareBookingContacts()).isNotEmpty();
        assertThat(result.getFileExtension()).isEqualTo(data.getFileExtension());
        assertThat(result.getFileName()).isEqualTo(data.getFileName());
        assertThat(result.getRecordingVersion()).isEqualTo("versionType");
        assertThat(result.getRecordingVersionNumberStr()).isEqualTo("1");
        assertThat(result.getRecordingVersionNumber()).isEqualTo(1);
        assertThat(result.isMostRecentVersion()).isTrue();

        verify(referenceDataProcessor, times(1)).fetchChannelUserDataMap();
        verify(cacheService, times(1)).getHashAll(key);
        verify(cacheService, times(1)).getHashAll(Constants.CacheKeys.COURTS_PREFIX);
        verify(loggingService, times(1))
            .logWarning(eq("Court not found for reference: %s"), eq(data.getCourtReference()));
    }

    @Test
    @DisplayName("Should successfully transform data")
    void transformDataSuccess() {
        ExtractedMetadata data = new ExtractedMetadata(
            "court_one",
            "urn123",
            "exhibitReference",
            "defendantLastName",
            "witnessFirstName",
            "1",
            "1",
            ".mp4",
            LocalDateTime.now(),
            3000,
            "filename.mp4",
            "12345",
            ARCHIVE_NAME
        );

        when(cacheService.getHashAll(Constants.CacheKeys.SITES_DATA))
            .thenReturn(SITES_DATA_MAP);
        String key = "vf:pre-process:urn123-defendantLastName-witnessFirstName";
        mockedRecordingUtils.when(() -> RecordingUtils.buildMetadataPreprocessKey(any(), any(), any()))
            .thenReturn(key);
        when(cacheService.getHashAll(key))
            .thenReturn(Map.of());
        mockedRecordingUtils.when(() -> RecordingUtils.processVersioning(any(), any(), any(), any(), any(), any()))
            .thenReturn(new RecordingUtils.VersionDetails("versionType", "1", 1, true));
        when(cacheService.getHashAll(key))
            .thenReturn(Collections.singletonMap(ARCHIVE_NAME, data));

        UUID courtId = UUID.randomUUID();
        Map<String, Object> courtData = new HashMap<>();
        courtData.put("Court One", courtId.toString());
        when(cacheService.getHashAll(Constants.CacheKeys.COURTS_PREFIX))
            .thenReturn(courtData);
        when(courtRepository.findById(courtId)).thenReturn(Optional.of(new Court()));

        ServiceResult<ProcessedRecording> result = dataTransformationService.transformData(data);
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getCourt()).isNotNull();

        verify(referenceDataProcessor, times(1)).fetchChannelUserDataMap();
        verify(cacheService, times(1)).getHashAll(key);
        verify(cacheService, times(1)).getHashAll(Constants.CacheKeys.COURTS_PREFIX);
        verify(loggingService, never())
            .logWarning(eq("Court not found for reference: %s"), eq(data.getCourtReference()));
    }
}
