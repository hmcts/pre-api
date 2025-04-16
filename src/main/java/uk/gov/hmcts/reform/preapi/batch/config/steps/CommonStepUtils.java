package uk.gov.hmcts.reform.preapi.batch.config.steps;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;

import java.io.IOException;

import static uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration.CHUNK_SIZE;
import static uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration.SKIP_LIMIT;

@Component
public class CommonStepUtils {

    private final LoggingService loggingService;
    private final Processor processor;

    public CommonStepUtils(LoggingService loggingService, Processor processor) {
        this.loggingService = loggingService;
        this.processor = processor;
    }

    public <T> FlatFileItemReader<T> createCsvReader(
        Resource inputFile,
        String[] fieldNames,
        Class<T> targetClass
    ) {
        try {
            return CSVReader.createReader(inputFile, fieldNames, targetClass);
        } catch (IOException e) {
            loggingService.logError("Failed to create reader for file: {}" + inputFile.getFilename() + e);
            throw new IllegalStateException("Failed to create reader for file: ", e);
        }
    }

    public <T> Step buildChunkStep(
        String stepName,
        Resource inputFile,
        String[] fieldNames,
        Class<T> inputType,
        ItemWriter<MigratedItemGroup> writer,
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager
    ) {
        FlatFileItemReader<T> reader = createCsvReader(inputFile, fieldNames, inputType);

        return new StepBuilder(stepName, jobRepository)
            .<T, MigratedItemGroup>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(SKIP_LIMIT)
            .skip(Exception.class)
            .build();
    }


}
