package uk.gov.hmcts.reform.preapi.batch.application.reader;

import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.logging.Logger;

@Component
public class CSVReader {

    /**
     * Creates a FlatFileItemReader for reading CSV files.
     * @param resource    The CSV file resource.
     * @param fieldNames  The names of the fields in the CSV file.
     * @param targetClass The target class to map the CSV rows to.
     * @return A configured FlatFileItemReader.
     * @throws IOException If an error occurs while creating the reader.
     */
    public <T> FlatFileItemReader<T> createReader(
        Resource resource,
        String[] fieldNames,
        Class<T> targetClass
    ) throws IOException {
        if (resource == null || !resource.exists()) {
            throw new IllegalArgumentException("Resource must not be null and must exist.");
        }
        if (fieldNames == null || fieldNames.length == 0) {
            throw new IllegalArgumentException("Field names must not be null or empty.");
        }
        if (targetClass == null) {
            throw new IllegalArgumentException("Target class must not be null.");
        }
        try {
            BeanWrapperFieldSetMapper<T> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
            fieldSetMapper.setTargetType(targetClass);

            return new FlatFileItemReaderBuilder<T>()
                    .name(targetClass.getSimpleName() + "Reader")
                    .resource(resource)
                    .linesToSkip(1)
                    .delimited().delimiter(",")
                    .names(fieldNames)
                    .fieldSetMapper(fieldSetMapper)
                    .strict(false)
                    .build();
        } catch (Exception e) {
            String errorMsg = String.format("Failed to create CSV reader for resource '%s'. Reason: %s",
                resource.getFilename(),
                e.getMessage()
            );
            Logger.getAnonymousLogger().severe(errorMsg);
            throw new IOException(errorMsg, e);
        }
    }
}
