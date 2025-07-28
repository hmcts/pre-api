package uk.gov.hmcts.reform.preapi.batch.application.config.steps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.JobSynchronizationManager;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.MigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreStepsConfigTest {

    private CoreStepsConfig stepsConfig;

    @Mock private JobRepository jobRepository;
    @Mock private Processor itemProcessor;
    @Mock private MigrationWriter itemWriter;
    @Mock private LoggingService loggingService;

    @BeforeEach
    void setup() {
        this.jobRepository = mock(JobRepository.class);
        this.itemProcessor = mock(Processor.class);
        this.itemWriter = mock(MigrationWriter.class);
        this.loggingService = mock(LoggingService.class);

        PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();

        stepsConfig = new CoreStepsConfig(
            jobRepository,
            transactionManager,
            itemProcessor,
            itemWriter,
            loggingService
        );
    }

    @Test
    void getDryRunFlagShouldReturnFalseWhenNoContext() {
        JobSynchronizationManager.close();
        assertThat(stepsConfig.getDryRunFlag()).isFalse();
    }


    @Test
    void createArchiveListStepReturnsStep() {
        Step step = stepsConfig.createArchiveListStep();
        assertThat(step).isNotNull();
    }

    @Test
    void createSitesDataStepReturnsStep() {
        Step step = stepsConfig.createSitesDataStep("false");
        assertThat(step).isNotNull();
    }

    @Test
    void createChannelUserStepReturnsStep() {
        Step step = stepsConfig.createChannelUserStep();
        assertThat(step).isNotNull();
    }

}