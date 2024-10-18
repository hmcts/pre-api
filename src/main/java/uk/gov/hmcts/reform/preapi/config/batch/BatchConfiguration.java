package uk.gov.hmcts.reform.preapi.config.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.processor.CSVProcessor;
import uk.gov.hmcts.reform.preapi.batch.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.writer.CSVWriter;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVSitesData;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;

import java.io.IOException;
import java.util.logging.Logger;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration implements StepExecutionListener {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CSVReader csvReader;
    private final CSVProcessor csvProcessor;
    private final CSVWriter csvWriter;
    private final MigrationTrackerService migrationTrackerService;

    @Autowired
    public BatchConfiguration(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              CSVReader csvReader,
                              CSVProcessor csvProcessor,
                              CSVWriter csvWriter,
                              MigrationTrackerService migrationTrackerService) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.csvReader = csvReader;
        this.csvProcessor = csvProcessor;
        this.csvWriter = csvWriter;
        this.migrationTrackerService = migrationTrackerService;
    }

    @Bean
    @Qualifier("importCsvJob")
    public Job processCSVJob() {
        return new JobBuilder("importCsvJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(createStep(
                "sitesDataStep", 
                new ClassPathResource("batch/Sites.csv"), 
                new String[]{"site_reference", "site_name", "location"}, 
                CSVSitesData.class, 
                ",", 
                false
            ))  
            .next(createStep(
                "archiveListDataStep", 
                new ClassPathResource("batch/Archive_List.csv"), 
                new String[]{"archive_name", "description", "create_time", "duration", "owner", 
                    "video_type", "audio_type", "content_type", "far_end_address"}, 
                CSVArchiveListData.class, 
                ",", 
                true 
            ))  
            .next(finalCsvWriteStep())
            .build();
    }

    public <T> Step createStep(String stepName, Resource inputFile, String[] fieldNames, Class<T> targetClass, 
                            String delimiter, boolean writeToCsv) {
        FlatFileItemReader<T> reader = createCsvReader(inputFile, fieldNames, targetClass, delimiter);
        StepBuilder stepBuilder = new StepBuilder(stepName, jobRepository);
        Logger.getAnonymousLogger().info("Starting step: " + stepName);
        
        var simpleStepBuilder = stepBuilder.<T, MigratedItemGroup>chunk(10, transactionManager)
                .reader(reader)
                .processor(csvProcessor);

        if (writeToCsv) {
            Logger.getAnonymousLogger().info("Using CSV Writer for step: " + stepName);
            simpleStepBuilder = simpleStepBuilder.writer(csvWriter);
        } else {
            Logger.getAnonymousLogger().info("Using No-Op Writer for step: " + stepName);
            simpleStepBuilder = simpleStepBuilder.writer(noOpWriter()); 
        }

        return simpleStepBuilder
                .faultTolerant()
                .skipLimit(10)
                .skip(Exception.class)
                .build();  
    }

    private <T> FlatFileItemReader<T> createCsvReader(
        Resource inputFile, String[] fieldNames, Class<T> targetClass, String delimiter) {
        try {
            return csvReader.createReader(inputFile, fieldNames, targetClass, delimiter);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create reader for file: " + inputFile.getFilename(), e);
        }
    }

    @Bean
    public Step finalCsvWriteStep() {
        return new StepBuilder("finalCsvWriteStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Logger.getAnonymousLogger().info("Writing all migrated items to CSV...");
                    migrationTrackerService.writeAllToCsv();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public <T> ItemWriter<T> noOpWriter() {
        return items -> {
            // No-op writer that does nothing
        };
    }
}
