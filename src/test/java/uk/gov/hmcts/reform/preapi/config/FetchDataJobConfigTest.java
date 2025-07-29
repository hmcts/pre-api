package uk.gov.hmcts.reform.preapi.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ArchiveMetadataXmlExtractor;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.jobs.FetchDataJobConfig;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FetchDataJobConfigTest {

    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private ArchiveMetadataXmlExtractor xmlProcessingService;
    private MigrationRecordService migrationRecordService;
    private CoreStepsConfig coreSteps;
    private LoggingService loggingService;
    private FetchDataJobConfig config;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        xmlProcessingService = mock(ArchiveMetadataXmlExtractor.class);
        migrationRecordService = mock(MigrationRecordService.class);
        coreSteps = mock(CoreStepsConfig.class);
        loggingService = mock(LoggingService.class);

        // Mock the startLogging step
        Step mockStep = mock(Step.class);
        when(coreSteps.startLogging()).thenReturn(mockStep);

        config = new FetchDataJobConfig(
            jobRepository,
            transactionManager,
            coreSteps,
            xmlProcessingService,
            migrationRecordService,
            loggingService
        );
    }

    @Test
    void fetchDataJob_shouldReturnJobBean() {
        Job job = config.fetchDataJob();
        assertNotNull(job);
        assertEquals("fetchDataJob", job.getName());
    }

    @Test
    void createXmlFetchStep_shouldReturnStepBean() {
        Step step = config.createXmlFetchStep();
        assertNotNull(step);
        assertEquals("fetchAndConvertXmlFileStep", step.getName());
    }
}
