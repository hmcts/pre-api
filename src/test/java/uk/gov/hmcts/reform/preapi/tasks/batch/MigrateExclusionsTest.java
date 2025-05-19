package uk.gov.hmcts.reform.preapi.tasks.batch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.MigrationType;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.tasks.migration.MigrateExclusions;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MigrateExclusionsTest {

    private static UserService userService;
    private static UserAuthenticationService userAuthenticationService;
    private static JobLauncher jobLauncher;
    private static LoggingService loggingService;
    private static Job processExclusionsJob;

    private static final String CRON_USER_EMAIL = "test@test.com";

    @BeforeEach
    void beforeEach() {
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);

        var accessDto = mock(AccessDTO.class);
        var baseAppAccessDTO = mock(BaseAppAccessDTO.class);
        when(baseAppAccessDTO.getId()).thenReturn(UUID.randomUUID());

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(accessDto);
        when(accessDto.getAppAccess()).thenReturn(Set.of(baseAppAccessDTO));

        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.ofNullable(userAuth));

        jobLauncher = mock(JobLauncher.class);
        loggingService = mock(LoggingService.class);
        processExclusionsJob = mock(Job.class);
    }

    @DisplayName("Test Migrating Exclusions")
    @Test
    public void testRun() throws JobInstanceAlreadyCompleteException,
        JobExecutionAlreadyRunningException,
        JobParametersInvalidException,
        JobRestartException {

        var migrateExclusions = new MigrateExclusions(userService,
                                                      userAuthenticationService,
                                                      CRON_USER_EMAIL,
                                                      jobLauncher,
                                                      loggingService,
                                                      false,
                                                      false,
                                                      processExclusionsJob);
        migrateExclusions.run();

        ArgumentCaptor<JobParameters> jobParameters = ArgumentCaptor.forClass(JobParameters.class);

        verify(jobLauncher, times(1)).run(eq(processExclusionsJob), jobParameters.capture());

        Assertions.assertEquals(String.valueOf(false),
                                jobParameters.getValue().getString("debug"));

        Assertions.assertEquals(MigrationType.FULL.name(),
                                jobParameters.getValue().getString("migrationType"));

        verify(loggingService, times(1)).logInfo("Successfully completed Process Exclusions batch job");
    }

    @DisplayName("Test Migrating Exclusions Exception")
    @Test
    public void testRunException() throws JobInstanceAlreadyCompleteException,
        JobExecutionAlreadyRunningException,
        JobParametersInvalidException,
        JobRestartException {

        when(jobLauncher.run(eq(processExclusionsJob), any()))
            .thenThrow(new JobExecutionAlreadyRunningException("Test"));

        var migrateExclusions = new MigrateExclusions(userService,
                                                      userAuthenticationService,
                                                      CRON_USER_EMAIL,
                                                      jobLauncher,
                                                      loggingService,
                                                      false,
                                                      false,
                                                      processExclusionsJob);
        migrateExclusions.run();

        verify(loggingService, times(1))
            .logError(eq("Error starting Process Exclusions batch job"),
                      any(JobExecutionAlreadyRunningException.class));
    }
}
