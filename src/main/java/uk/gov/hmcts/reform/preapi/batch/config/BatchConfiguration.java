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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.PreProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.RecordingMetadataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ReferenceDataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ArchiveMetadataXmlExtractor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.EntityCreationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.RedisService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.Writer;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.tasks.BatchRobotUserTask;

import java.io.IOException;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration implements StepExecutionListener {
    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 10;
    private static final String BASE_PATH = "/batch/";
    private static final String SITES_CSV = BASE_PATH + "Sites.csv";
    private static final String CHANNEL_USER_CSV = BASE_PATH + "Channel_User_Report.csv";
    private static final String ARCHIVE_LIST_CSV = BASE_PATH + "Archive_List.csv";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CSVReader csvReader;
    private final PreProcessor preProcessor;
    private final RecordingMetadataProcessor recordingPreProcessor;
    private final ReferenceDataProcessor referenceDataProcessor;
    private final RedisService redisService;
    private final Processor itemProcessor;
    private final Writer itemWriter;
    private final MigrationTrackerService migrationTrackerService;
    private final CaseService caseService;
    private final CaptureSessionService captureSessionService;
    private final CaseRepository caseRepository;
    private final BatchRobotUserTask robotUserTask;
    private final ArchiveMetadataXmlExtractor xmlProcessingService;
    private LoggingService loggingService;

    @Autowired
    public BatchConfiguration(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        CSVReader csvReader,
        PreProcessor preProcessor,
        RecordingMetadataProcessor recordingPreProcessor,
        ReferenceDataProcessor referenceDataProcessor,
        RedisService redisService,
        Processor itemProcessor,
        EntityCreationService entityCreationService,
        CaseService caseService,
        BookingService bookingService,
        CaptureSessionService captureSessionService,
        CaseRepository caseRepository,
        BookingRepository bookingRepository,
        CaptureSessionRepository captureSessionRepository,
        RecordingRepository recordingRepository,
        RecordingService recordingService,
        Writer itemWriter,
        MigrationTrackerService migrationTrackerService,
        BatchRobotUserTask robotUserTask,
        ArchiveMetadataXmlExtractor xmlProcessingService,
        LoggingService loggingService
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.csvReader = csvReader;
        this.preProcessor = preProcessor;
        this.recordingPreProcessor =  recordingPreProcessor;
        this.referenceDataProcessor = referenceDataProcessor;
        this.itemProcessor = itemProcessor;
        this.redisService = redisService;
        this.caseService = caseService;
        this.captureSessionService = captureSessionService;
        this.caseRepository = caseRepository;
        this.itemWriter = itemWriter;
        this.migrationTrackerService = migrationTrackerService;
        this.robotUserTask = robotUserTask;
        this.xmlProcessingService = xmlProcessingService;
        this.loggingService = loggingService;
    }

    // =========================
    // Job Definitions
    // =========================

    @Bean
    @Qualifier("fetchXmlJob")
    public Job fetchXmlJob() {
        return new JobBuilder("fetchXmlJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(startLogging())
            .next(createXmlFetchStep())
            .build();
    }

    @Bean
    @Qualifier("importCsvJob")
    public Job processCSVJob() {
        Step startLogStep = startLogging();
        Step sitesStep = createSitesDataStep();
        Step channelStep = createChannelUserStep();
        Step robotStep = createRobotUserSignInStep();
        Step preProcessStep = createPreProcessStep();
        Step metadataStep = createPreProcessMetadataStep();
        Step archiveStep = createArchiveListStep();
        // Step markCasesStep = createMarkCasesClosedStep();
        Step writeCsvStep = createWriteToCSVStep();

        return new JobBuilder("importCsvJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fileAvailabilityDecider())
            .on("FAILED").end()
            .on("COMPLETED")
            .to(startLogStep)
            .next(sitesStep)
            .next(channelStep)
            .next(robotStep)
            .next(preProcessStep)
            .next(metadataStep)
            .next(archiveStep)
            // .next(createNonTransactionalMarkCasesClosedStep())
            // .next(markCasesStep)
            .next(writeCsvStep)
            .end()
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
            new ClassPathResource(ARCHIVE_LIST_CSV),
            new String[]{"archive_name", "create_time", "duration", "file_name", "file_size"},
            CSVArchiveListData.class,
            true
        );
    }

    protected Step startLogging() {
        return new StepBuilder("loggingStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String debugParam = (String) chunkContext.getStepContext()
                    .getJobParameters()
                    .get("debug");

                    boolean debug = Boolean.parseBoolean(debugParam);

                    loggingService.setDebugEnabled(debug);
                    loggingService.initializeLogFile();
                    loggingService.logInfo("Job started with debug mode: " + debug);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    @JobScope
    protected Step createXmlFetchStep() {
        return new StepBuilder("fetchAndConvertXmlFileStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String containerName = "pre-vodafone-spike";
                    String outputDir = "src/main/resources/batch";
                    xmlProcessingService.extractAndReportArchiveMetadata(containerName, outputDir);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    protected Step createPreProcessStep() {
        return new StepBuilder("preProcessStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    preProcessor.initialize();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    protected Step createPreProcessMetadataStep() {
        return new StepBuilder("preProcessMetadataStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                Resource resource = new ClassPathResource(ARCHIVE_LIST_CSV);
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
            }, transactionManager)
            .build();
    }

    protected Step createWriteToCSVStep() {
        return new StepBuilder("writeToCSVStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    migrationTrackerService.writeAllToCsv();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    protected Step createRobotUserSignInStep() {
        return new StepBuilder("signInRobotUserStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                robotUserTask.signIn();
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }



    // =========================
    // Utility and Helper Functions
    // =========================
    protected JobExecutionDecider fileAvailabilityDecider() {
        return (jobExecution, stepExecution) -> {
            Resource sites = new ClassPathResource(SITES_CSV);
            Resource channelReport = new ClassPathResource(CHANNEL_USER_CSV);
            Resource archiveList = new ClassPathResource(ARCHIVE_LIST_CSV);

            if (sites.exists() && channelReport.exists() && archiveList.exists()) {
                return new FlowExecutionStatus("COMPLETED");
            } else {
                return new FlowExecutionStatus("FAILED");
            }
        };
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

    // protected Step createMarkCasesClosedStep() {
    //     return new StepBuilder("markCasesClosedStep", jobRepository)
    //         .tasklet((contribution, chunkContext) -> {
    //             robotUserTask.signIn();

    //             Set<String> channelNames = referenceDataProcessor.fetchChannelUserDataKeys();

    //             int processedCases = 0;
    //             int closedCases = 0;
    //             int skippedCases = 0;

    //             Page<CaptureSessionDTO> vodafoneOriginSessions = captureSessionService.searchBy(
    //                 null,  null, RecordingOrigin.VODAFONE, null,
    //                 Optional.empty(), null, Pageable.unpaged()
    //             );

    //             Page<CaseDTO> allCases = caseService.searchBy(null, null, false, Pageable.unpaged());

    //             for (CaseDTO caseDto : allCases.getContent()) {
    //                 loggingService.logInfo("Evaluating case: {}"+ caseDto.getReference());
    //                 // logger.info("Evaluating case: {}", caseDto.getReference());
    //                 try {
    //                     boolean hasVodafoneOrigin = checkForVodafoneOrigin(vodafoneOriginSessions, caseDto);

    //                     if (hasVodafoneOrigin) {
    //                         boolean hasChannelUser = channelNames
    //                             .stream()
    //                             .anyMatch(channelName -> channelName.contains(caseDto.getReference()));

    //                         if (!hasChannelUser) {
    //                             loggingService.logInfo("Attempting to close case:"+ caseDto.getReference());
    //                             // logger.info("Attempting to close case:", caseDto.getReference());
    //                             try {
    //                                 if (tryCloseCase(caseDto)) {
    //                                     closedCases++;
    //                                 }
    //                             } catch (Exception e) {
    //                                 loggingService.logError("Error closing case {}: {}" + caseDto.getReference() + e.getMessage());
    //                                 // logger.error("Error closing case {}: {}", caseDto.getReference(), e.getMessage());
    //                                 skippedCases++;
    //                             }

    //                         }

    //                         processedCases++;
    //                     }
    //                 } catch (Exception e) {
    //                     loggingService.logError("Error processing case {}: {}" + caseDto.getReference() + e.getMessage() + e);
    //                     // logger.error("Error processing case {}: {}", caseDto.getReference(), e.getMessage(), e);
    //                 }
    //             }
    //             loggingService.logInfo("Vodafone Case Closure Summary: {} cases processed, {} cases closed"+
    //                 processedCases + closedCases);
    //             // logger.info("Vodafone Case Closure Summary: {} cases processed, {} cases closed",
    //             //     processedCases, closedCases);

    //             return RepeatStatus.FINISHED;
    //         }, transactionManager)

    //         .build();
    // }

    // private boolean tryCloseCase(CaseDTO caseDto) {
    //     try {
    //         Case existingCase = caseRepository.findById(caseDto.getId())
    //             .orElseThrow(() -> new uk.gov.hmcts.reform.preapi.exception.NotFoundException(
    //                 "Case not found: " + caseDto.getId()));

    //         loggingService.logInfo("Attempting to close case without channel user: {}" + caseDto.getReference());
    //         // logger.info("Attempting to close case without channel user: {}", caseDto.getReference());


    //         CreateCaseDTO updateCaseDto = createCaseUpdateDTO(existingCase, caseDto);

    //         try {
    //             caseService.upsert(updateCaseDto);
    //             loggingService.logInfo("Successfully closed case {} with reference {}" +
    //                 caseDto.getId() + caseDto.getReference());
    //             // logger.info("Successfully closed case {} with reference {}",
    //             //     caseDto.getId(), caseDto.getReference());

    //             return true;

    //         } catch (Exception e) {
    //             if (e.getMessage() != null
    //                 && (e.getMessage().contains("getCaptureSessions()")
    //                 || e.getCause() != null && e.getCause().getMessage() != null
    //                 && e.getCause().getMessage().contains("getCaptureSessions()"))) {

    //                 loggingService.logWarning("Case {} has bookings with null captureSessions - cannot process" +
    //                     caseDto.getReference());
    //                 // logger.warn("Case {} has bookings with null captureSessions - cannot process",
    //                 //     caseDto.getReference());
    //                 return false;
    //             } else {
    //                 throw e;
    //             }
    //         }

    //     } catch (Exception e) {
    //         loggingService.logError("Failed to close case {} (Reference: {}): {}" +
    //             caseDto.getId() + caseDto.getReference() + e.getMessage() + e);
    //         // logger.error("Failed to close case {} (Reference: {}): {}",
    //         //     caseDto.getId(), caseDto.getReference(), e.getMessage(), e);
    //         return false;
    //     }
    // }

    // private CreateCaseDTO createCaseUpdateDTO(Case existingCase, CaseDTO caseDto) {
    //     CreateCaseDTO updateCaseDto = new CreateCaseDTO(existingCase);
    //     updateCaseDto.setState(CaseState.CLOSED);
    //     updateCaseDto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));

    //     if (caseDto.getParticipants() != null && !caseDto.getParticipants().isEmpty()) {
    //         updateCaseDto.setParticipants(
    //             caseDto.getParticipants()
    //                 .stream()
    //                 .map(this::convertDtoToCreateDto)
    //                 .collect(Collectors.toSet())
    //         );
    //     }

    //     return updateCaseDto;
    // }

    // private boolean checkForVodafoneOrigin(Page<CaptureSessionDTO> vodafoneOriginSessions, CaseDTO caseDto) {
    //     return vodafoneOriginSessions.getContent().stream()
    //         .anyMatch(session -> session.getOrigin() == RecordingOrigin.VODAFONE);
    // }

    // private CreateParticipantDTO convertDtoToCreateDto(ParticipantDTO participantDTO) {
    //     CreateParticipantDTO createParticipantDTO = new CreateParticipantDTO();
    //     createParticipantDTO.setId(participantDTO.getId());
    //     createParticipantDTO.setFirstName(participantDTO.getFirstName());
    //     createParticipantDTO.setLastName(participantDTO.getLastName());
    //     createParticipantDTO.setParticipantType(participantDTO.getParticipantType());
    //     return createParticipantDTO;
    // }


    // protected Step createNonTransactionalMarkCasesClosedStep() {
    //     return new StepBuilder("nonTransactionalMarkCasesClosedStep", jobRepository)
    //         .tasklet((contribution, chunkContext) -> {
    //             logger.info("Starting nonTransactionalMarkCasesClosedStep");
    //             try {
    //                 robotUserTask.signIn();
    //                 logger.info("Robot user signed in successfully");

    //                 Set<String> channelNames = referenceDataProcessor.fetchChannelUserDataKeys();
    //                 logger.info("Fetched {} channel user keys", channelNames.size());

    //                 Page<CaptureSessionDTO> vodafoneOriginSessions = captureSessionService.searchBy(
    //                     null, null, RecordingOrigin.VODAFONE, null,
    //                     Optional.empty(), null, Pageable.unpaged()
    //                 );
    //                 logger.info("Fetched {} Vodafone origin sessions", vodafoneOriginSessions.getTotalElements());

    //                 Page<CaseDTO> allCases = caseService.searchBy(null, null, false, Pageable.unpaged());
    //                 logger.info("Fetched {} cases in total", allCases.getTotalElements());

    //                 int processedCases = 0;
    //                 int closedCases = 0;
    //                 int skippedCases = 0;

    //                 for (CaseDTO caseDto : allCases.getContent()) {
    //                     try {
    //                         boolean hasVodafoneOrigin = checkForVodafoneOrigin(vodafoneOriginSessions, caseDto);
    //                         logger.debug("Case {} has Vodafone origin: {}", caseDto.getReference(), hasVodafoneOrigin);

    //                         if (hasVodafoneOrigin) {
    //                             boolean hasChannelUser = channelNames
    //                                 .stream()
    //                                 .anyMatch(channelName -> channelName.contains(caseDto.getReference()));
    //                             logger.debug("Case {} has channel user: {}", caseDto.getReference(), hasChannelUser);

    //                             if (!hasChannelUser) {
    //                                 logger.info("Non-transactional: Attempting to close case {}", caseDto.getReference());

    //                                 try {
    //                                     logger.debug("Before processCaseNonTransactional call for case {}", caseDto.getReference());
    //                                     boolean success = processCaseNonTransactional(caseDto);
    //                                     logger.debug("After processCaseNonTransactional call for case {}, success={}",
    //                                         caseDto.getReference(), success);

    //                                     if (success) {
    //                                         closedCases++;
    //                                         logger.info("Successfully closed case {}", caseDto.getReference());
    //                                     } else {
    //                                         skippedCases++;
    //                                         logger.info("Skipped closing case {}", caseDto.getReference());
    //                                     }
    //                                 } catch (Exception e) {
    //                                     logger.error("Error closing case {}: {}", caseDto.getReference(), e.getMessage(), e);
    //                                     skippedCases++;
    //                                 }
    //                             }

    //                             processedCases++;
    //                         }
    //                     } catch (Exception e) {
    //                         logger.error("Error evaluating case {}: {}", caseDto.getReference(), e.getMessage(), e);
    //                     }
    //                 }

    //                 logger.info("Non-transactional Summary: {} processed, {} closed, {} skipped",
    //                     processedCases, closedCases, skippedCases);


    //                 logger.info("Completed nonTransactionalMarkCasesClosedStep successfully");
    //                 return RepeatStatus.FINISHED;
    //             } catch (Exception e) {
    //                 logger.error("Fatal error in non-transactional case closure: {}", e.getMessage(), e);
    //                 throw new NonFatalStepException("Step completed with handled errors", e);
    //             }
    //         }, transactionManager)
    //         .allowStartIfComplete(true)
    //         .transactionAttribute(getNoTransactionAttribute())
    //         .build();
    // }

    // private DefaultTransactionAttribute getNoTransactionAttribute() {
    //     DefaultTransactionAttribute attribute = new DefaultTransactionAttribute();
    //     attribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    //     return attribute;
    // }

    // public static class NonFatalStepException extends RuntimeException {
    //     public NonFatalStepException(String message, Throwable cause) {
    //         super(message, cause);
    //     }
    // }

    // private boolean processCaseNonTransactional(CaseDTO caseDto) {
    //     logger.debug("Starting processCaseNonTransactional for case {}", caseDto.getReference());
    //     try {
    //         logger.debug("Fetching case {} from repository", caseDto.getId());
    //         Case existingCase = caseRepository.findById(caseDto.getId())
    //             .orElseThrow(() -> new RuntimeException("Case not found"));
    //         logger.debug("Successfully fetched case {}", caseDto.getId());

    //         logger.debug("Creating update DTO for case {}", caseDto.getId());
    //         CreateCaseDTO updateDto = new CreateCaseDTO(existingCase);
    //         updateDto.setState(CaseState.CLOSED);
    //         updateDto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));

    //         if (caseDto.getParticipants() != null && !caseDto.getParticipants().isEmpty()) {
    //             logger.debug("Setting {} participants for case {}",
    //                 caseDto.getParticipants().size(), caseDto.getId());
    //             updateDto.setParticipants(
    //                 caseDto.getParticipants()
    //                     .stream()
    //                     .map(this::convertDtoToCreateDto)
    //                     .collect(Collectors.toSet())
    //             );
    //         }

    //         try {
    //             logger.debug("About to call caseService.upsert for case {}", caseDto.getId());
    //             caseService.upsert(updateDto);
    //             logger.info("Non-transactional: Successfully closed case {}", caseDto.getReference());
    //             return true;
    //         } catch (Exception e) {
    //             if (e.getMessage() != null
    //                 && (e.getMessage().contains("getCaptureSessions()")
    //                 || (e.getCause() != null && e.getCause().getMessage() != null
    //                 && e.getCause().getMessage().contains("getCaptureSessions()")))) {

    //                 logger.warn("Non-transactional: Case {} has null captureSessions - skipping",
    //                     caseDto.getReference());
    //                 return false;
    //             }

    //             logger.error("Error from caseService.upsert for case {}: {}",
    //                 caseDto.getReference(), e.getMessage(), e);
    //             throw e;
    //         }
    //     } catch (Exception e) {
    //         logger.error("Non-transactional: Error closing case {}: {}",
    //             caseDto.getReference(), e.getMessage(), e);
    //         return false;
    //     }
    // }

}
