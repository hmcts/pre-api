package uk.gov.hmcts.reform.preapi.batch.application.services.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    static void setUp() {
        reportCsvWriter = Mockito.mockStatic(ReportCsvWriter.class);
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
    void getHashAll() {
        Map<String, Object> data =  Map.of(
            hashKey, value,
            "otherKey", "otherValue"
        );
        inMemoryCacheService.saveHashAll(testKey, data);
        assertThat(data).isEqualTo(inMemoryCacheService.getHashAll(testKey));
    }

    @Test
    void clearNamespaceKeys() {
        inMemoryCacheService.saveHashValue("namespace:test1", hashKey, value);
        inMemoryCacheService.saveHashValue("namespace:test2", hashKey, value);
        inMemoryCacheService.saveHashValue("other:test3", hashKey, value);

        inMemoryCacheService.clearNamespaceKeys("namespace:");

        assertThat(inMemoryCacheService.getHashAll("namespace:test1")).isNull();
        assertThat(inMemoryCacheService.getHashAll("namespace:test2")).isNull();
        assertThat(inMemoryCacheService.getHashAll("other:test3")).isNotNull();
    }

    @Test
    void generateBaseKeySuccess() {
        String baseKey = inMemoryCacheService.generateBaseKey("12345", "A-B");
        assertThat(baseKey).isEqualTo("vf:case:12345:participants:A-B");
    }

    @Test
    void generateBaseKeyCaseRefNullFailure() {
        String message = assertThrows(
            IllegalArgumentException.class,
            () -> inMemoryCacheService.generateBaseKey(null, "A-B")
        ).getMessage();
        assertThat(message).isEqualTo("Case reference cannot be null or blank");
    }

    @Test
    void generateBaseKeyCaseRefEmptyFailure() {
        String message = assertThrows(
            IllegalArgumentException.class,
            () -> inMemoryCacheService.generateBaseKey("", "A-B")
        ).getMessage();
        assertThat(message).isEqualTo("Case reference cannot be null or blank");
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
    void getAsStringArrayList() {
        List<String[]> list = new ArrayList<>();
        list.add(new String[]{"a", "b"});

        inMemoryCacheService.saveHashValue(testKey, hashKey, list);
        assertThat(inMemoryCacheService.getAsStringArrayList(testKey, hashKey)).isEqualTo(list);
    }

    @Test
    void testGetAsStringArrayListWithInvalidData() {
        inMemoryCacheService.saveHashValue(testKey, hashKey, "not a list");
        assertThat(inMemoryCacheService.getAsStringArrayList(testKey, hashKey)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllAsType() {
        Map<String, Object> data = Map.of("field1", "value1");

        inMemoryCacheService.saveHashAll(testKey, data);
        assertThat((Map<String, Object>) inMemoryCacheService.getAllAsType(testKey, Map.class)).isEqualTo(data);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllAsTypeWithIncorrectType() {
        Map<String, Object> data = Map.of("field1", "value1");

        inMemoryCacheService.saveHashAll(testKey, data);
        assertThat(inMemoryCacheService.getAllAsType(testKey, List.class)).isNull();
    }
}
