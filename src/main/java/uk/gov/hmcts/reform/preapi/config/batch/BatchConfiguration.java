package uk.gov.hmcts.reform.preapi.config.batch;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.processor.PreProcessor;
import uk.gov.hmcts.reform.preapi.batch.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.processor.RecordingMetadataProcessor;
import uk.gov.hmcts.reform.preapi.batch.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.reader.XMLReader;
import uk.gov.hmcts.reform.preapi.batch.writer.Writer;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVChannelData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVSitesData;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.entities.batch.XMLArchiveFileData;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration implements StepExecutionListener {


    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CSVReader csvReader;
    private final XMLReader xmlReader;
    private final PreProcessor preProcessor;
    private final RecordingMetadataProcessor recordingPreProcessor;
    private final Processor itemProcessor;
    private final Writer itemWriter;
    private final MigrationTrackerService migrationTrackerService;
    private static final int CHUNK_SIZE = 10;
    private static final int SKIP_LIMIT = 10;

    @Autowired
    public BatchConfiguration(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        CSVReader csvReader,
        XMLReader xmlReader,
        PreProcessor preProcessor,
        RecordingMetadataProcessor recordingPreProcessor,
        Processor itemProcessor,
        Writer itemWriter,
        MigrationTrackerService migrationTrackerService
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.csvReader = csvReader;
        this.xmlReader = xmlReader;
        this.preProcessor = preProcessor;
        this.recordingPreProcessor =  recordingPreProcessor;
        this.itemProcessor = itemProcessor;
        this.itemWriter = itemWriter;
        this.migrationTrackerService = migrationTrackerService;
    }

    @Bean
    @Qualifier("importCsvJob")
    public Job processCSVJob() {
        return new JobBuilder("importCsvJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(preProcessStep())
            .next(preProcessMetadataStep())
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
            // .next(processXmlFilesStep())
            .next(createReadStep(
                "archiveListDataStep", 
                new ClassPathResource("batch/Archive_List.csv"), 
                new String[]{"archive_name", "description", "create_time", "duration", "owner", 
                    "video_type", "audio_type", "content_type", "far_end_address"}, 
                CSVArchiveListData.class, 
                true 
            ))  
            // .next(writeToCsvXML())
            .next(writeToCSV())
            .build();
    }

    // Defines a generic step for reading, processing, and optionally writing CSV data
    public <T> Step createReadStep(
        String stepName, 
        Resource filePath, 
        String[] fieldNames, 
        Class<T> targetClass, 
        boolean writeToCsv
    ) {
        FlatFileItemReader<T> reader = createCsvReader(filePath, fieldNames, targetClass);
        StepBuilder stepBuilder = new StepBuilder(stepName, jobRepository);
        var simpleStepBuilder = createChunkStep(stepBuilder, reader, writeToCsv);

        return simpleStepBuilder
                .faultTolerant()
                .skipLimit(SKIP_LIMIT)
                .skip(Exception.class)
                .build();  
    }

    private <T> SimpleStepBuilder<T, MigratedItemGroup> createChunkStep(
        StepBuilder stepBuilder, 
        FlatFileItemReader<T> reader, 
        boolean writeToCsv
    ) {
        var simpleStepBuilder = stepBuilder.<T, MigratedItemGroup>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(itemProcessor);

        if (writeToCsv) {
            simpleStepBuilder = simpleStepBuilder.writer(itemWriter);
        } else {
            simpleStepBuilder = simpleStepBuilder.writer(noOpWriter()); 
        }

        return simpleStepBuilder;
    }
        
    // Utility method to create a CSV reader
    private <T> FlatFileItemReader<T> createCsvReader(
        Resource inputFile, 
        String[] fieldNames, 
        Class<T> targetClass
    ) {
        try {
            return csvReader.createReader(inputFile, fieldNames, targetClass);
        } catch (IOException e) {
            Logger.getAnonymousLogger().severe("Failed to create reader for file: " + inputFile.getFilename());
            throw new IllegalStateException("Failed to create reader for file: " + inputFile.getFilename(), e);
        }
    }

    // Initialisation step to set up necessary prerequisites for processing
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
    public Step preProcessMetadataStep() {
        return new StepBuilder("preProcessMetadataStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                Resource resource = new ClassPathResource("batch/Archive_List.csv");
                String[] fieldNames = {"archive_name", "description", "create_time", "duration", "owner", 
                    "video_type", "audio_type", "content_type", "far_end_address"};

                FlatFileItemReader<CSVArchiveListData> reader = csvReader.createReader(
                    resource, fieldNames, CSVArchiveListData.class
                );

                reader.open(new ExecutionContext());

                CSVArchiveListData item;
                while ((item = reader.read()) != null) {
                    recordingPreProcessor.processRecording(item); 
                }

                reader.close();

                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }
    

    // Step to write data to CSV
    @Bean
    public Step writeToCSV() {
        return new StepBuilder("writeToCSV", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    migrationTrackerService.writeAllToCsv();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    
    // A utility no-operation writer for steps that do not require actual writing
    @Bean
    public <T> ItemWriter<T> noOpWriter() {
        return items -> {
            // no-op writer  does nothing
        };
    }

    @Bean
    public Step processXmlFilesStep() {
        return new StepBuilder("processXmlFilesStep", jobRepository)
                .<XMLArchiveFileData, MigratedItemGroup>chunk(CHUNK_SIZE, transactionManager)
                .reader(xmlItemReader())
                .processor(itemProcessor)
                .writer(itemWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(SKIP_LIMIT) 
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(StepExecution stepExecution) {
                        Logger.getAnonymousLogger().info("Starting XML file processing step.");
                    }

                    @Override
                    public ExitStatus afterStep(StepExecution stepExecution) {
                        Logger.getAnonymousLogger().info("Completed XML file processing step.");
                        return stepExecution.getExitStatus();
                    }
                })
                .build();
    }


    @Bean
    public ItemReader<XMLArchiveFileData> xmlItemReader() {
        return new ItemReader<>() {
            private List<InputStreamResource> currentBatch = new ArrayList<>();
            private int currentIndex = 0;
            private StaxEventItemReader<XMLArchiveFileData> currentReader = null;

            @Override
            public XMLArchiveFileData read() throws Exception {
                if (currentReader == null || currentReader.read() == null) {
                    if (currentIndex >= currentBatch.size()) {
                        currentBatch = preProcessor.fetchAndProcessNextBatch("pre-vodafone-spike", 10);
                        currentIndex = 0;

                        if (currentBatch.isEmpty()) {
                            return null; 
                        }
                    }

                    Resource currentResource = currentBatch.get(currentIndex++);
                    currentReader = xmlReader.createReader(currentResource, XMLArchiveFileData.class);
                    currentReader.open(new ExecutionContext());
                }

                return currentReader.read();
            }
        };
    }

}
