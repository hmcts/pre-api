package uk.gov.hmcts.reform.preapi.batch.application.services.persistence;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

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
}
