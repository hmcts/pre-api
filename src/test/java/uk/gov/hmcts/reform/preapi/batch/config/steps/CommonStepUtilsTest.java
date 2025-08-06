package uk.gov.hmcts.reform.preapi.batch.config.steps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonStepUtilsTest {

    @Mock
    private LoggingService loggingService;

    @Mock
    private Processor processor;

    @Mock
    private Resource inputFile;

    @Mock
    private FlatFileItemReader<String> reader;

    @Mock
    private ItemWriter<MigratedItemGroup> writer;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private CommonStepUtils commonStepUtils;

    @BeforeEach
    void setUp() {
        commonStepUtils = new CommonStepUtils(loggingService, processor);
    }

    @Test
    @DisplayName("Should create CSV reader successfully")
    void shouldCreateCsvReaderSuccessfully() throws IOException {
        // Given
        String[] fieldNames = {"field1", "field2"};
        Class<String> targetClass = String.class;

        try (MockedStatic<CSVReader> csvReaderMock = mockStatic(CSVReader.class)) {
            csvReaderMock.when(() -> CSVReader.createReader(inputFile, fieldNames, targetClass))
                .thenReturn(reader);

            // When
            FlatFileItemReader<String> result = commonStepUtils.createCsvReader(inputFile, fieldNames, targetClass);

            // Then
            assertNotNull(result);
            assertEquals(reader, result);
            csvReaderMock.verify(() -> CSVReader.createReader(inputFile, fieldNames, targetClass));
        }
    }

    @Test
    @DisplayName("Should handle IOException when creating CSV reader")
    void shouldHandleIOExceptionWhenCreatingCsvReader() throws IOException {
        // Given
        String[] fieldNames = {"field1", "field2"};
        Class<String> targetClass = String.class;
        String filename = "test.csv";
        IOException ioException = new IOException("Test exception");

        when(inputFile.getFilename()).thenReturn(filename);

        try (MockedStatic<CSVReader> csvReaderMock = mockStatic(CSVReader.class)) {
            csvReaderMock.when(() -> CSVReader.createReader(inputFile, fieldNames, targetClass))
                .thenThrow(ioException);

            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class, () ->
                    commonStepUtils.createCsvReader(inputFile, fieldNames, targetClass)
            );

            assertEquals("Failed to create reader for file: ", exception.getMessage());
            assertEquals(ioException, exception.getCause());
            verify(loggingService).logError(contains("Failed to create reader for file: {}"), eq(filename), any());
        }
    }

    @Test
    @DisplayName("Should build chunk step successfully")
    void shouldBuildChunkStepSuccessfully() throws IOException {
        // Given
        String stepName = "testStep";
        String[] fieldNames = {"field1", "field2"};
        Class<String> inputType = String.class;

        try (MockedStatic<CSVReader> csvReaderMock = mockStatic(CSVReader.class)) {
            csvReaderMock.when(() -> CSVReader.createReader(inputFile, fieldNames, inputType))
                .thenReturn(reader);

            // When
            Step result = commonStepUtils.buildChunkStep(
                stepName,
                inputFile,
                fieldNames,
                inputType,
                writer,
                jobRepository,
                transactionManager
            );

            // Then
            assertNotNull(result);
            csvReaderMock.verify(() -> CSVReader.createReader(inputFile, fieldNames, inputType));
        }
    }

    @Test
    @DisplayName("Should propagate exception when CSV reader creation fails in buildChunkStep")
    void shouldPropagateExceptionWhenCsvReaderCreationFailsInBuildChunkStep() throws IOException {
        // Given
        String stepName = "testStep";
        String[] fieldNames = {"field1", "field2"};
        Class<String> inputType = String.class;
        String filename = "test.csv";
        IOException ioException = new IOException("Test exception");

        when(inputFile.getFilename()).thenReturn(filename);

        try (MockedStatic<CSVReader> csvReaderMock = mockStatic(CSVReader.class)) {
            csvReaderMock.when(() -> CSVReader.createReader(inputFile, fieldNames, inputType))
                .thenThrow(ioException);

            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class, () ->
                    commonStepUtils.buildChunkStep(
                        stepName,
                        inputFile,
                        fieldNames,
                        inputType,
                        writer,
                        jobRepository,
                        transactionManager
                    )
            );

            assertEquals("Failed to create reader for file: ", exception.getMessage());
            assertEquals(ioException, exception.getCause());
            verify(loggingService).logError(contains("Failed to create reader for file: {}"), eq(filename), any());
        }
    }

    @Test
    @DisplayName("Should handle null filename gracefully")
    void shouldHandleNullFilenameGracefully() throws IOException {
        // Given
        String[] fieldNames = {"field1", "field2"};
        Class<String> targetClass = String.class;
        IOException ioException = new IOException("Test exception");

        when(inputFile.getFilename()).thenReturn(null);

        try (MockedStatic<CSVReader> csvReaderMock = mockStatic(CSVReader.class)) {
            csvReaderMock.when(() -> CSVReader.createReader(inputFile, fieldNames, targetClass))
                .thenThrow(ioException);

            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class, () ->
                    commonStepUtils.createCsvReader(inputFile, fieldNames, targetClass)
            );

            assertEquals("Failed to create reader for file: ", exception.getMessage());
            assertEquals(ioException, exception.getCause());
            verify(loggingService).logError(contains("Failed to create reader for file: {}"), eq(null), any());
        }
    }

    @Test
    @DisplayName("Should create reader with different target types")
    void shouldCreateReaderWithDifferentTargetTypes() throws IOException {
        // Given
        String[] fieldNames = {"id", "name"};
        Class<Object> targetClass = Object.class;

        try (MockedStatic<CSVReader> csvReaderMock = mockStatic(CSVReader.class)) {
            FlatFileItemReader<Object> expectedReader = new FlatFileItemReader<>();
            csvReaderMock.when(() -> CSVReader.createReader(inputFile, fieldNames, targetClass))
                .thenReturn(expectedReader);

            // When
            FlatFileItemReader<Object> result = commonStepUtils.createCsvReader(inputFile, fieldNames, targetClass);

            // Then
            assertNotNull(result);
            assertEquals(expectedReader, result);
        }
    }

    @Test
    @DisplayName("Should handle empty field names array")
    void shouldHandleEmptyFieldNamesArray() throws IOException {
        // Given
        String[] fieldNames = {};
        Class<String> targetClass = String.class;

        try (MockedStatic<CSVReader> csvReaderMock = mockStatic(CSVReader.class)) {
            csvReaderMock.when(() -> CSVReader.createReader(inputFile, fieldNames, targetClass))
                .thenReturn(reader);

            // When
            FlatFileItemReader<String> result = commonStepUtils.createCsvReader(inputFile, fieldNames, targetClass);

            // Then
            assertNotNull(result);
            assertEquals(reader, result);
        }
    }
}
