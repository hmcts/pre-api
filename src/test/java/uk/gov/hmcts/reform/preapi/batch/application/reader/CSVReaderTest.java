package uk.gov.hmcts.reform.preapi.batch.application.reader;

import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.core.io.Resource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CSVReaderTest {
    private Resource resource;

    @BeforeEach
    void setUp() {
        resource = mock(Resource.class);
    }

    @Data
    private static class DummyRecord {
        private String field1;
        private String field2;
    }

    @Test
    @DisplayName("Should create FlatFileItemReader successfully")
    void shouldCreateReaderSuccessfully() throws IOException {
        when(resource.exists()).thenReturn(true);

        FlatFileItemReader<DummyRecord> reader = CSVReader.createReader(
            resource,
            new String[]{"field1", "field2"},
            DummyRecord.class
        );
        assertThat(reader).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when resource is null")
    void shouldThrowWhenResourceIsNull() {
        String message = assertThrows(
            IllegalArgumentException.class,
            () -> CSVReader.createReader(null, new String[]{"field1"}, DummyRecord.class)
        ).getMessage();
        assertThat(message).isEqualTo("Resource must not be null and must exist.");
    }

    @Test
    @DisplayName("Should throw exception when resource doesn't exist")
    void shouldThrowWhenResourceDoesNotExist() {
        when(resource.exists()).thenReturn(false);

        String message = assertThrows(
            IllegalArgumentException.class,
            () -> CSVReader.createReader(resource, new String[]{"field1"}, DummyRecord.class)
        ).getMessage();
        assertThat(message).isEqualTo("Resource must not be null and must exist.");
    }

    @Test
    @DisplayName("Should throw exception when fieldNames is null")
    void shouldThrowWhenFieldNamesIsNull() {
        when(resource.exists()).thenReturn(true);

        String message = assertThrows(
            IllegalArgumentException.class,
            () -> CSVReader.createReader(resource, null, DummyRecord.class)
        ).getMessage();
        assertThat(message).isEqualTo("Field names must not be null or empty.");
    }

    @Test
    @DisplayName("Should throw exception when fieldNames is empty")
    void shouldThrowWhenFieldNamesIsEmpty() {
        when(resource.exists()).thenReturn(true);

        String message = assertThrows(
            IllegalArgumentException.class,
            () -> CSVReader.createReader(resource, new String[]{}, DummyRecord.class)
        ).getMessage();
        assertThat(message).isEqualTo("Field names must not be null or empty.");
    }

    @Test
    @DisplayName("Should throw exception when targetClass is null")
    void shouldThrowWhenTargetClassIsNull() {
        when(resource.exists()).thenReturn(true);

        String message = assertThrows(
            IllegalArgumentException.class,
            () -> CSVReader.createReader(resource, new String[]{"field1"}, null)
        ).getMessage();
        assertThat(message).isEqualTo("Target class must not be null.");
    }
}
