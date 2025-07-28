
package uk.gov.hmcts.reform.preapi.batch.application.config.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.EntityCreationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.PostMigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.config.jobs.PostMigrationJobConfig;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class PostMigrationJobConfigTest {

    @Mock private JobRepository jobRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private CoreStepsConfig coreSteps;
    @Mock private BatchConfiguration batchConfig;
    @Mock private LoggingService loggingService;
    @Mock private InMemoryCacheService cacheService;
    @Mock private EntityCreationService entityCreationService;
    @Mock private MigrationTrackerService migrationTrackerService;
    @Mock private CaseService caseService;
    @Mock private BookingService bookingService;
    @Mock private Step dummyStep;
    @Mock private PostMigrationWriter postMigrationWriter;

    private PostMigrationJobConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new PostMigrationJobConfig(
            jobRepository,
            transactionManager,
            coreSteps,
            batchConfig,
            loggingService,
            cacheService,
            entityCreationService,
            migrationTrackerService,
            caseService,
            bookingService
        );
    }

    @Test
    void postMigrationJobShouldNotBeNull() {
        when(coreSteps.startLogging()).thenReturn(dummyStep);
        Job job = config.postMigrationJob(dummyStep, dummyStep, dummyStep, dummyStep, dummyStep, dummyStep);
        assertThat(job).isNotNull();
    }

    @Test
    void createMarkCasesClosedStepShouldNotBeNull() {
        Step step = config.createMarkCasesClosedStep();
        assertThat(step).isNotNull();
    }

    @Test
    void createShareBookingsStepShouldNotBeNull() {
        Step step = config.createShareBookingsStep(postMigrationWriter);
        assertThat(step).isNotNull();
    }
}
