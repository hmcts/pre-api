package uk.gov.hmcts.reform.preapi.batch.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ArchiveMetadataXmlExtractor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.DeltaProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.PreProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.RecordingMetadataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ReferenceDataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.EntityCreationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.Writer;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVExemptionListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.tasks.BatchRobotUserTask;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration implements StepExecutionListener {
    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 10;
    private static final String CONTAINER_NAME = "pre-vodafone-spike";
    private static final String FULL_PATH = "src/main/resources/batch";
    private static final String BASE_PATH = "/batch/";
    private static final String SITES_CSV = BASE_PATH + "reference_data/Sites.csv";
    private static final String CHANNEL_USER_CSV = BASE_PATH + "reference_data/Channel_User_Report.csv";
    private static final String ARCHIVE_LIST_INITAL = BASE_PATH + "Archive_List_initial.csv";
    private static final String DELTA_RECORDS_CSV = BASE_PATH + "Archive_List_delta.csv";
    private static final String EXCEMPTIONS_LIST_CSV = BASE_PATH + "Excemption_List.csv";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final InMemoryCacheService cacheService;
    private final CSVReader csvReader;
    private final PreProcessor preProcessor;
    private final RecordingMetadataProcessor recordingPreProcessor;
    private final Processor itemProcessor;
    private final Writer itemWriter;
    private final MigrationTrackerService migrationTrackerService;
    private final BatchRobotUserTask robotUserTask;
    private final ArchiveMetadataXmlExtractor xmlProcessingService;
    private final DeltaProcessor deltaProcessor;
    private final CaseService caseService;
    private LoggingService loggingService;

    @Autowired
    public BatchConfiguration(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        CSVReader csvReader,
        PreProcessor preProcessor,
        RecordingMetadataProcessor recordingPreProcessor,
        ReferenceDataProcessor referenceDataProcessor,
        Processor itemProcessor,
        EntityCreationService entityCreationService,
        CaseService caseService,
        BookingService bookingService,
        CaptureSessionService captureSessionService,
        CaseRepository caseRepository,
        InMemoryCacheService cacheService,
        BookingRepository bookingRepository,
        CaptureSessionRepository captureSessionRepository,
        RecordingRepository recordingRepository,
        RecordingService recordingService,
        Writer itemWriter,
        MigrationTrackerService migrationTrackerService,
        BatchRobotUserTask robotUserTask,
        ArchiveMetadataXmlExtractor xmlProcessingService,
        LoggingService loggingService,
        DeltaProcessor deltaProcessor
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.csvReader = csvReader;
        this.preProcessor = preProcessor;
        this.recordingPreProcessor = recordingPreProcessor;
        this.caseService = caseService;
        this.cacheService = cacheService;
        this.itemProcessor = itemProcessor;
        this.itemWriter = itemWriter;
        this.migrationTrackerService = migrationTrackerService;
        this.robotUserTask = robotUserTask;
        this.xmlProcessingService = xmlProcessingService;
        this.loggingService = loggingService;
        this.deltaProcessor = deltaProcessor;
    }

    // =========================
    // Job Definitions
    // =========================

    @Bean
    public Job fetchXmlJob() {
        return new JobBuilder("fetchXmlJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(startLogging())
            .next(createXmlFetchStep())
            .build();
    }

    @Bean
    public Job processCSVJob() {
        return new JobBuilder("processCSVJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fileAvailabilityDecider())
            .on("FAILED").end()
            .on("COMPLETED")
            .to(startLogging())
            .next(createSitesDataStep())
            .next(createChannelUserStep())
            .next(createRobotUserSignInStep())
            .next(createPreProcessStep())

            .next(deltaProcessingDecider())
            .on("FULL").to(createPreProcessMetadataStep())
                                .next(createArchiveListStep())
                                .next(createWriteToCSVStep())

            .from(deltaProcessingDecider())
            .on("DELTA").to(createDeltaProcessingStep())
                                .next(createDeltaListStep())
                                .next(createWriteToCSVStep())
            .end()
            .build();
    }

    @Bean
    public Job postMigrationJob() {
        return new JobBuilder("postMigrationJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(startLogging())
            .next(createRobotUserSignInStep())
            .next(createChannelUserStep())
            .next(createMarkCasesClosedStep())
            .build();
    }

    @Bean
    public Job processExclusionsJob() {
        return new JobBuilder("processExclusionsJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(startLogging())
            .next(createSitesDataStep())
            .next(createChannelUserStep())
            .next(createRobotUserSignInStep())
            .next(createPreProcessStep())
            .next(createExcemptionListStep())
            .next(createWriteToCSVStep())
            .build();
    }

    // =========================
    // Step Definitions
    // =========================
    private Step createSitesDataStep() {
        return createReadStep(
            "sitesDataStep",
            new ClassPathResource(SITES_CSV),
            new String[]{"site_reference", "site_name", "location", "court_name"},
            CSVSitesData.class,
            false
        );
    }

    private Step createChannelUserStep() {
        return createReadStep(
            "channelUserStep",
            new ClassPathResource(CHANNEL_USER_CSV),
            new String[]{"channel_name", "channel_user", "channel_user_email"},
            CSVChannelData.class,
            false
        );
    }

    private Step createArchiveListStep() {
        return createReadStep(
            "archiveListDataStep",
            new ClassPathResource(ARCHIVE_LIST_INITAL),
            new String[]{"archive_name", "create_time", "duration", "file_name", "file_size"},
            CSVArchiveListData.class,
            true
        );
    }

    private Step createDeltaListStep() {
        return createReadStep(
            "deltaDataStep",
            new ClassPathResource(DELTA_RECORDS_CSV),
            new String[]{"archive_name", "create_time", "duration", "file_name", "file_size"},
            CSVArchiveListData.class,
            true
        );
    }

    protected Step createDeltaProcessingStep() {
        return new StepBuilder("deltaProcessingStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                String deltaFilePath = "src/main/resources/batch/Archive_List_delta.csv";
                deltaProcessor.processDelta(
                    "src/main/resources/batch/Archive_List_initial.csv",
                    "src/main/resources/batch/Archive_List_updated.csv",
                    deltaFilePath
                );

                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    private Step createExcemptionListStep() {
        return createExcemptionReadStep(
            "excemptionListDataStep",
            new ClassPathResource(EXCEMPTIONS_LIST_CSV),
            new String[] {
                "archive_name","create_time","duration","court_reference","urn",
                "exhibit_reference","defendant_name","witness_name","recording_version",
                "recording_version_number","file_extension","file_name","file_size","reason","added_by"
            },
            CSVExemptionListData.class,
            true
        );
    }

    protected Step startLogging() {
        return new StepBuilder("loggingStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    String debugParam = (String) chunkContext.getStepContext()
                                                             .getJobParameters()
                                                             .get("debug");

                    var migrationType = MigrationType.fromString((String) chunkContext.getStepContext()
                                                                                      .getJobParameters()
                                                                                      .get("migrationType"));

                    boolean debug = Boolean.parseBoolean(debugParam);

                    loggingService.setDebugEnabled(debug);
                    loggingService.initializeLogFile(migrationType);
                    loggingService.logInfo("Job started with debug mode: " + debug);

                    return RepeatStatus.FINISHED;
                }, transactionManager
            )
            .build();
    }

    @Bean
    @JobScope
    protected Step createXmlFetchStep() {
        return new StepBuilder("fetchAndConvertXmlFileStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    var migrationType = MigrationType.fromString((String) chunkContext.getStepContext()
                                                                                      .getJobParameters()
                                                                                      .get("migrationType"));

                    String outputFileName = "Archive_List_initial";
                    if (migrationType.equals(MigrationType.DELTA)) {
                        outputFileName = "Archive_List_updated";
                    }

                    xmlProcessingService.extractAndReportArchiveMetadata(CONTAINER_NAME, FULL_PATH, outputFileName);
                    return RepeatStatus.FINISHED;
                }, transactionManager
            )
            .build();
    }

    protected Step createPreProcessStep() {
        return new StepBuilder("preProcessStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    preProcessor.initialize();
                    return RepeatStatus.FINISHED;
                }, transactionManager
            )
            .build();
    }

    protected Step createPreProcessMetadataStep() {
        return new StepBuilder("preProcessMetadataStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    String migrationType = Optional.ofNullable((String) chunkContext.getStepContext()
                                    .getJobParameters().get("migrationType")).orElse("FULL");

                    String filePath = "FULL".equalsIgnoreCase(migrationType)
                        ? ARCHIVE_LIST_INITAL
                        : DELTA_RECORDS_CSV;

                    Resource resource = new ClassPathResource(filePath);
                    String[] fieldNames = {"archive_name", "create_time", "duration", "file_name", "file_size"};

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
                }, transactionManager
            )
            .build();
    }

    protected Step createWriteToCSVStep() {
        return new StepBuilder("writeToCSVStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    migrationTrackerService.writeAllToCsv();
                    return RepeatStatus.FINISHED;
                }, transactionManager
            )
            .build();
    }

    protected Step createRobotUserSignInStep() {
        return new StepBuilder("signInRobotUserStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    robotUserTask.signIn();
                    return RepeatStatus.FINISHED;
                }, transactionManager
            )
            .build();
    }


    // =========================
    // Utility and Helper Functions
    // =========================
    @Bean
    public JobExecutionDecider deltaProcessingDecider() {
        return (jobExecution, stepExecution) -> {
            var migrationType = MigrationType.fromString(
                (String) Objects.requireNonNull(jobExecution.getJobParameters().getString("migrationType"))
            );

            if (migrationType.equals(MigrationType.DELTA)) {
                return new FlowExecutionStatus("DELTA");
            } else {
                return new FlowExecutionStatus("FULL");
            }
        };
    }

    protected JobExecutionDecider fileAvailabilityDecider() {
        return (jobExecution, stepExecution) -> {
            Resource sites = new ClassPathResource(SITES_CSV);
            Resource channelReport = new ClassPathResource(CHANNEL_USER_CSV);
            Resource archiveList = new ClassPathResource(ARCHIVE_LIST_INITAL);

            if (sites.exists() && channelReport.exists() && archiveList.exists()) {
                return new FlowExecutionStatus("COMPLETED");
            } else {
                return new FlowExecutionStatus("FAILED");
            }
        };
    }

    protected <T> Step createExcemptionReadStep(
        String stepName,
        Resource filePath,
        String[] fieldNames,
        Class<T> targetClass,
        boolean writeToCsv
    ) {
        FlatFileItemReader<T> reader = createCsvReader(filePath, fieldNames, targetClass);
        return new StepBuilder(stepName, jobRepository)
            .<T, MigratedItemGroup>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(itemProcessor)
            .writer(writeToCsv ? itemWriter : noOpWriter())
            .faultTolerant()
            .skipLimit(SKIP_LIMIT)
            .skip(Exception.class)
            .build();
    }



    protected <T> Step createReadStep(
        String stepName,
        Resource filePath,
        String[] fieldNames,
        Class<T> targetClass,
        boolean writeToCsv
    ) {
        FlatFileItemReader<T> reader = createCsvReader(filePath, fieldNames, targetClass);
        return new StepBuilder(stepName, jobRepository)
            .<T, MigratedItemGroup>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(itemProcessor)
            .writer(writeToCsv ? itemWriter : noOpWriter())
            .faultTolerant()
            .skipLimit(SKIP_LIMIT)
            .skip(Exception.class)
            .build();
    }

    private <T> FlatFileItemReader<T> createCsvReader(
        Resource inputFile,
        String[] fieldNames,
        Class<T> targetClass
    ) {
        try {
            return csvReader.createReader(inputFile, fieldNames, targetClass);
        } catch (IOException e) {
            loggingService.logError("Failed to create reader for file: {}" + inputFile.getFilename() + e);
            // logger.error("Failed to create reader for file: {}", inputFile.getFilename(), e);
            throw new IllegalStateException("Failed to create reader for file: ", e);
        }
    }

    @Bean
    public <T> ItemWriter<T> noOpWriter() {
        return items -> { /*  no-op writer  does nothing */ };
    }

    protected Step createMarkCasesClosedStep() {
        return new StepBuilder("markCasesClosedStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                List<CaseDTO> vodafoneCases = fetchVodafoneCases();

                if (vodafoneCases.isEmpty()) {
                    loggingService.logInfo("No Vodafone-origin cases found.");
                    return RepeatStatus.FINISHED;
                }

                Map<String, List<String[]>> channelUsersMap = cacheService.getAllChannelReferences();
                loggingService.logInfo("Loaded %d channel reference keys from cache.", channelUsersMap.size());

                AtomicInteger closed = new AtomicInteger();
                AtomicInteger skipped = new AtomicInteger();

                vodafoneCases.forEach(caseDTO -> 
                    processCase(caseDTO, channelUsersMap, closed, skipped)
                );

                loggingService.logInfo("Case closure summary — Total: %d, Closed: %d, Skipped: %d",
                    vodafoneCases.size(), closed.get(), skipped.get());

                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    private List<CaseDTO> fetchVodafoneCases() {
        List<CaseDTO> cases = caseService.getCasesByOrigin(RecordingOrigin.VODAFONE);
        loggingService.logInfo("Found %d Vodafone-origin cases.", cases.size());
        return cases;
    }

    private void processCase(CaseDTO caseDTO, Map<String, List<String[]>> channelUsersMap, 
        AtomicInteger closed, AtomicInteger skipped) {
        String reference = caseDTO.getReference();
        loggingService.logInfo("===== Evaluating case: %s", reference);

        if (hasMatchingChannelUser(reference, channelUsersMap)) {
            loggingService.logDebug("Case %s has matching channel user entry — attempting to close.", reference);
            try {
                caseService.upsert(buildClosedCaseDTO(caseDTO));
                loggingService.logInfo("Successfully closed Vodafone case: %s", reference);
                closed.incrementAndGet();
            } catch (Exception e) {
                loggingService.logError("Failed to close case %s: %s", reference, e.getMessage());
                skipped.incrementAndGet();
            }
        } else {
            loggingService.logInfo("Skipping case %s — no matching channel user data found.", reference);
            skipped.incrementAndGet();
        }
    }

    private boolean hasMatchingChannelUser(String reference, Map<String, List<String[]>> channelUsersMap) {
        return channelUsersMap.keySet().stream()
            .anyMatch(k -> k.toLowerCase().contains(reference.toLowerCase()));
    }

    private CreateCaseDTO buildClosedCaseDTO(CaseDTO caseDTO) {
        CreateCaseDTO dto = new CreateCaseDTO();
        dto.setId(caseDTO.getId());
        dto.setCourtId(caseDTO.getCourt().getId());
        dto.setReference(caseDTO.getReference());
        dto.setOrigin(caseDTO.getOrigin());
        dto.setTest(caseDTO.isTest());
        dto.setState(CaseState.CLOSED);
        dto.setClosedAt(Timestamp.from(Instant.now()));

        if (caseDTO.getParticipants() != null) {
            loggingService.logInfo("Mapping %d participant(s) for case: %s", caseDTO.getParticipants().size(), 
                caseDTO.getReference());
            Set<CreateParticipantDTO> createParticipants = caseDTO.getParticipants().stream()
                .map(this::mapParticipant)
                .collect(Collectors.toSet());
            dto.setParticipants(createParticipants);
        }

        return dto;
    }

    private CreateParticipantDTO mapParticipant(ParticipantDTO p) {
        CreateParticipantDTO dto = new CreateParticipantDTO();
        dto.setId(p.getId());
        dto.setFirstName(p.getFirstName());
        dto.setLastName(p.getLastName());
        dto.setParticipantType(p.getParticipantType());
        return dto;
    }

}