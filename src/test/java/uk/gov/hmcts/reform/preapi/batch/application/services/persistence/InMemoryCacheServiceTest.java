package uk.gov.hmcts.reform.preapi.batch.application.services.persistence;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = InMemoryCacheService.class)
public class InMemoryCacheServiceTest {
    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private InMemoryCacheService inMemoryCacheService;

    private final String testKey = "testKey";
    private final String hashKey = "field1";
    private final String value = "value1";

    private static MockedStatic<ReportCsvWriter> reportCsvWriter;

    @BeforeAll
    public static void setUp() {
        reportCsvWriter = Mockito.mockStatic(ReportCsvWriter.class);
    }

    @AfterAll
    public static void tearDown() {
        reportCsvWriter.close();
    }

    @BeforeEach
    void resetMocks() {
        reportCsvWriter.reset();
    }

    @Test
    void saveHashAll() {
        inMemoryCacheService.saveHashAll(testKey, Map.of(hashKey, value));
        assertThat(inMemoryCacheService.getHashValue(testKey, hashKey, String.class)).isEqualTo(value);
    }

    @Test
    void saveHashValue() {
        inMemoryCacheService.saveHashValue(testKey, hashKey, value);
        assertThat(inMemoryCacheService.getHashValue(testKey, hashKey, String.class)).isEqualTo(value);
    }

    @Test
    void checkHashKeyExists() {
        inMemoryCacheService.saveHashValue(testKey, hashKey, value);
        assertThat(inMemoryCacheService.checkHashKeyExists(testKey, hashKey)).isTrue();
        assertThat(inMemoryCacheService.checkHashKeyExists(testKey, "otherKey")).isFalse();
    }

    @Test
    void generateCacheKeySuccess() {
        String baseKey = inMemoryCacheService.generateEntityCacheKey("case", "participants", "A", "B");
        assertThat(baseKey).isEqualTo("vf:case:participants-a-b");
    }

    @Test
    void dumpToFileIOException() {
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenThrow(IOException.class);
        inMemoryCacheService.dumpToFile();
        verify(loggingService, times(1))
            .logError(contains("Failed to write in-memory cache to file"));
    }

    @Test
    void getCourtReturnsCourtWhenPresent() {
        CourtDTO courtDTO = new CourtDTO();
        courtDTO.setName("Test Court");
        inMemoryCacheService.saveCourt(courtDTO.getName(), courtDTO);

        Optional<CourtDTO> result = inMemoryCacheService.getCourt("Test Court");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Court");
    }

    @Test
    void getCourtReturnsEmptyWhenNotPresent() {
        Optional<CourtDTO> result = inMemoryCacheService.getCourt("Nonexistent Court");

        assertThat(result).isEmpty();
    }

    @Test
    void saveCourtStoresCourtSuccessfully() {
        CourtDTO courtDTO = new CourtDTO();
        courtDTO.setName("Court A");

        inMemoryCacheService.saveCourt("Court A", courtDTO);

        assertThat(inMemoryCacheService.getCourt("Court A").get()).isEqualTo(courtDTO);
    }

    @Test
    void saveCourtAllowsRetrievalByName() {
        CourtDTO courtDTO = new CourtDTO();
        courtDTO.setName("Court B");

        inMemoryCacheService.saveCourt("Court B", courtDTO);

        Optional<CourtDTO> retrievedCourt = inMemoryCacheService.getCourt("Court B");

        assertThat(retrievedCourt).isPresent();
        assertThat(retrievedCourt.get().getName()).isEqualTo("Court B");
    }

    @Test
    void saveCaseStoresCaseSuccessfully() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setReference("CASE123");

        inMemoryCacheService.saveCase("CASE123", createCaseDTO);

        // Verify case is stored (no getter method, but can verify via dumpToFile behavior)
        Optional<CreateCaseDTO> result = inMemoryCacheService.getCase("CASE123");
        assertThat(result).isPresent();
        assertThat(createCaseDTO.getReference()).isEqualTo("CASE123");
    }

    @Test
    void saveAndGetShareBookingSuccess() {
        CreateShareBookingDTO shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        String cacheKey = "booking-key-123";

        inMemoryCacheService.saveShareBooking(cacheKey, shareBookingDTO);
        Optional<CreateShareBookingDTO> result = inMemoryCacheService.getShareBooking(cacheKey);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(shareBookingDTO.getId());
    }

    @Test
    void getShareBookingReturnsEmptyWhenNotPresent() {
        Optional<CreateShareBookingDTO> result = inMemoryCacheService.getShareBooking("nonexistent-key");

        assertThat(result).isEmpty();
    }

    @Test
    void saveUserStoresUserSuccessfully() {
        UUID userId = UUID.randomUUID();
        String email = "Test.User@Example.COM";

        inMemoryCacheService.saveUser(email, userId);

        // Verify user is stored in hash with lowercase email
        String storedUserId = inMemoryCacheService.getHashValue(
            Constants.CacheKeys.USERS_PREFIX,
            email.toLowerCase(),
            String.class
        );
        assertThat(storedUserId).isEqualTo(userId.toString());
    }

    @Test
    void saveAndGetSiteReferenceSuccess() {
        String siteRef = "SITE123";
        String courtName = "Test Court";

        inMemoryCacheService.saveSiteReference(siteRef, courtName);
        Map<String, String> allSiteReferences = inMemoryCacheService.getAllSiteReferences();

        assertThat(allSiteReferences).containsEntry(siteRef, courtName);
    }

    @Test
    void saveAndGetChannelReferenceSuccess() {
        String channelName = "Channel1";
        List<String[]> users = List.of(
            new String[]{"User1", "user1@example.com"},
            new String[]{"User2", "user2@example.com"}
        );

        inMemoryCacheService.saveChannelReference(channelName, users);
        Optional<List<String[]>> result = inMemoryCacheService.getChannelReference(channelName);

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(2);
        assertThat(result.get().get(0)[0]).isEqualTo("User1");
        assertThat(result.get().get(0)[1]).isEqualTo("user1@example.com");
    }

    @Test
    void getChannelReferenceReturnsEmptyWhenNotPresent() {
        Optional<List<String[]>> result = inMemoryCacheService.getChannelReference("nonexistent-channel");

        assertThat(result).isEmpty();
    }

    @Test
    void getAllChannelReferencesReturnsAllChannels() {
        String channel1 = "Channel1";
        String channel2 = "Channel2";
        List<String[]> users1 = new ArrayList<>() {
            {
                add(new String[]{"User1", "user1@example.com"});
            }
        };
        List<String[]> users2 = new ArrayList<>() {
            {
                add(new String[]{"User2", "user2@example.com"});
            }
        };

        inMemoryCacheService.saveChannelReference(channel1, users1);
        inMemoryCacheService.saveChannelReference(channel2, users2);

        Map<String, List<String[]>> allChannels = inMemoryCacheService.getAllChannelReferences();

        assertThat(allChannels).hasSize(2);
        assertThat(allChannels).containsKey(channel1);
        assertThat(allChannels).containsKey(channel2);
    }

    @Test
    void checkHashKeyExistsReturnsFalseForNonExistentKey() {
        assertThat(inMemoryCacheService.checkHashKeyExists("nonexistent", "field")).isFalse();
    }

    @Test
    void getHashValueReturnsNullForNonExistentKey() {
        String result = inMemoryCacheService.getHashValue("nonexistent", "field", String.class);
        assertThat(result).isNull();
    }

    @Test
    void getHashValueReturnsNullForWrongType() {
        inMemoryCacheService.saveHashValue(testKey, hashKey, "string-value");
        Integer result = inMemoryCacheService.getHashValue(testKey, hashKey, Integer.class);
        assertThat(result).isNull();
    }

    @Test
    void clearNamespaceKeysRemovesMatchingKeys() {
        String namespace = "test:namespace:";
        inMemoryCacheService.saveHashValue(namespace + "key1", "field1", "value1");
        inMemoryCacheService.saveHashValue(namespace + "key2", "field2", "value2");
        inMemoryCacheService.saveHashValue("other:key", "field3", "value3");

        inMemoryCacheService.clearNamespaceKeys(namespace);

        assertThat(inMemoryCacheService.getHashValue(namespace + "key1", "field1", String.class)).isNull();
        assertThat(inMemoryCacheService.getHashValue(namespace + "key2", "field2", String.class)).isNull();
        assertThat(inMemoryCacheService.getHashValue("other:key", "field3", String.class)).isEqualTo("value3");
    }

    @Test
    void generateEntityCacheKeyHandlesNullValues() {
        String result = inMemoryCacheService.generateEntityCacheKey("case", "part1", null, "part3");
        assertThat(result).isEqualTo("vf:case:part1-part3");
    }

    @Test
    void generateEntityCacheKeyHandlesMixedCase() {
        String result = inMemoryCacheService.generateEntityCacheKey("CASE", "Part1", "PART2");
        assertThat(result).isEqualTo("vf:CASE:part1-part2");
    }

    @Test
    void dumpToFileSuccessfullyWritesData() throws IOException {
        // Setup some test data
        CourtDTO court = new CourtDTO();
        court.setName("Test Court");
        inMemoryCacheService.saveCourt("Test Court", court);

        CreateCaseDTO caseDTO = new CreateCaseDTO();
        caseDTO.setReference("CASE123");
        inMemoryCacheService.saveCase("CASE123", caseDTO);

        CreateShareBookingDTO shareBooking = new CreateShareBookingDTO();
        shareBooking.setId(UUID.randomUUID());
        inMemoryCacheService.saveShareBooking("share-key", shareBooking);

        inMemoryCacheService.saveUser("user@example.com", UUID.randomUUID());
        inMemoryCacheService.saveSiteReference("SITE1", "Court1");
        inMemoryCacheService.saveChannelReference(
            "Channel1", new ArrayList<>() {
                {
                    add(new String[]{"User", "email"});
                }
            }
        );
        inMemoryCacheService.saveHashValue("test:key", "field", "value");

        inMemoryCacheService.dumpToFile();

        // Verify ReportCsvWriter was called
        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()), times(1));
    }
}
