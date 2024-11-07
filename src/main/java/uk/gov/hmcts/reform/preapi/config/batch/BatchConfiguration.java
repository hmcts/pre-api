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
import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.processor.CSVProcessor;
import uk.gov.hmcts.reform.preapi.batch.processor.PreProcessor;
import uk.gov.hmcts.reform.preapi.batch.processor.XMLProcessor;
import uk.gov.hmcts.reform.preapi.batch.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.reader.XMLReader;
import uk.gov.hmcts.reform.preapi.batch.writer.CSVWriter;
import uk.gov.hmcts.reform.preapi.entities.batch.ArchiveFiles;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVChannelData;
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
    private final XMLReader xmlReader;
    private final PreProcessor preProcessor;
    private final CSVProcessor csvProcessor;
    private final XMLProcessor xmlProcessor;
    private final CSVWriter csvWriter;
    private final MigrationTrackerService migrationTrackerService;

    @Autowired
    public BatchConfiguration(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              CSVReader csvReader,
                              XMLReader xmlReader,
                              PreProcessor preProcessor,
                              CSVProcessor csvProcessor,
                              XMLProcessor xmlProcessor,
                              CSVWriter csvWriter,
                              MigrationTrackerService migrationTrackerService) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.csvReader = csvReader;
        this.xmlReader = xmlReader;
        this.preProcessor = preProcessor;
        this.csvProcessor = csvProcessor;
        this.xmlProcessor = xmlProcessor;
        this.csvWriter = csvWriter;
        this.migrationTrackerService = migrationTrackerService;
    }

    @Bean
    @Qualifier("importCsvJob")
    public Job processCSVJob() {
        return new JobBuilder("importCsvJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(preProcessStep())
            .next(createReadStep(
                "sitesDataStep", 
                new ClassPathResource("batch/Sites.csv"), 
                new String[]{"site_reference", "site_name", "location"}, 
                CSVSitesData.class, 
                false
            ))
            .next(createReadStep(
                "channelUserStep",
                new ClassPathResource("batch/Channel_User_Report.csv"),
                new String[]{"channel_name","channel_user", "channel_user_email"},
                CSVChannelData.class,
                false 
            ))
            .next(createReadStep(
                "archiveListDataStep", 
                new ClassPathResource("batch/Archive_List.csv"), 
                new String[]{"archive_name", "description", "create_time", "duration", "owner", 
                    "video_type", "audio_type", "content_type", "far_end_address"}, 
                CSVArchiveListData.class, 
                true 
            ))  
            // .next(createXmlReadStep())  
            .next(writeToCSV())
            .build();
    }

    public <T> Step createReadStep(
        String stepName, 
        Resource filePath, 
        String[] fieldNames, 
        Class<T> targetClass, 
        boolean writeToCsv
    ) {
        FlatFileItemReader<T> reader = createCsvReader(filePath, fieldNames, targetClass, ",");
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
        Resource inputFile, 
        String[] fieldNames, 
        Class<T> targetClass, 
        String delimiter
    ) {
        try {
            return csvReader.createReader(inputFile, fieldNames, targetClass, delimiter);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create reader for file: " + inputFile.getFilename(), e);
        }
    }

    @Bean
    public Step preProcessStep() {
        return new StepBuilder("preProcessStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    preProcessor.initialize();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step processDataStep() {
        return new StepBuilder("processDataStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step writeToCSV() {
        return new StepBuilder("writeToCSV", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    migrationTrackerService.writeAllToCsv();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step createXmlReadStep() {
        Resource xmlFile = new ClassPathResource("batch/Ed033cd9b7a08449f9cd157f7eaff7577.xml");
        StaxEventItemReader<ArchiveFiles> reader = xmlReader.createReader(xmlFile, ArchiveFiles.class);
        
        return new StepBuilder("xmlReadStep", jobRepository)
            .<ArchiveFiles, ArchiveFiles>chunk(1, transactionManager)
            .reader(reader)
            .processor(xmlProcessor)
            .writer(noOpWriter())  
            .faultTolerant()
            .skipLimit(5)
            .skip(Exception.class)
            .build();
    }

    @Bean
    public <T> ItemWriter<T> noOpWriter() {
        return items -> {
            // no-op writer  does nothing
        };
    }
}
