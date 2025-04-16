package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ArchiveMetadataXmlExtractor;
import uk.gov.hmcts.reform.preapi.batch.config.MigrationType;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;

@Configuration
public class FetchXmlJobConfig {

    public static final String CONTAINER_NAME = "pre-vodafone-spike";
    public static final String FULL_PATH = "src/main/resources/batch";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ArchiveMetadataXmlExtractor xmlProcessingService;
    private final CoreStepsConfig coreSteps;


    public FetchXmlJobConfig(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        CoreStepsConfig coreSteps,
        ArchiveMetadataXmlExtractor xmlProcessingService
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.coreSteps = coreSteps;
        this.xmlProcessingService = xmlProcessingService;
    }

    @Bean
    public Job fetchXmlJob() {
        return new JobBuilder("fetchXmlJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(coreSteps.startLogging())
            .next(createXmlFetchStep())
            .build();
    }

    @Bean
    public Step createXmlFetchStep() {
        return new StepBuilder("fetchAndConvertXmlFileStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                var migrationType = MigrationType.fromString(
                    (String) chunkContext.getStepContext()
                        .getJobParameters()
                        .get("migrationType")
                );

                String outputFileName = migrationType.equals(MigrationType.DELTA)
                    ? "Archive_List_updated"
                    : "Archive_List_initial";

                xmlProcessingService.extractAndReportArchiveMetadata(CONTAINER_NAME, FULL_PATH, outputFileName);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }
}
