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
import uk.gov.hmcts.reform.preapi.tasks.migration.FetchData;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FetchDataTest {

    private static UserService userService;
    private static UserAuthenticationService userAuthenticationService;
    private static JobLauncher jobLauncher;
    private static LoggingService loggingService;
    private static Job fetchDataJob;

    private static final String CRON_USER_EMAIL = "test@test.com";

    @BeforeEach
    void beforeEach() {
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);

        var baseAppAccessDTO = new BaseAppAccessDTO();
        baseAppAccessDTO.setId(UUID.randomUUID());
        
        var accessDto = new AccessDTO();
        accessDto.setAppAccess(Set.of(baseAppAccessDTO));

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(accessDto);

        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.ofNullable(userAuth));

        jobLauncher = mock(JobLauncher.class);
        loggingService = mock(LoggingService.class);
        fetchDataJob = mock(Job.class);
    }


    @DisplayName("Test Fetch Data")
    @Test
    public void testRun() throws JobInstanceAlreadyCompleteException,
        JobExecutionAlreadyRunningException,
        JobParametersInvalidException,
        JobRestartException {

        var fetchData = new FetchData(userService,
                                    userAuthenticationService,
                                    CRON_USER_EMAIL,
                                    jobLauncher,
                                    loggingService,
                                    false,
                                    false,
                                    MigrationType.FULL.name(),
                                    "xml",
                                    fetchDataJob);
        fetchData.run();

        ArgumentCaptor<JobParameters> jobParameters = ArgumentCaptor.forClass(JobParameters.class);

        verify(jobLauncher, times(1)).run(eq(fetchDataJob), jobParameters.capture());

        Assertions.assertEquals(String.valueOf(false),
                                jobParameters.getValue().getString("debug"));

        Assertions.assertEquals(MigrationType.FULL.name(),
                                jobParameters.getValue().getString("migrationType"));

        verify(loggingService, times(1)).logInfo("Successfully completed Fetch Data batch job");
    }

    @DisplayName("Test Fetch XML Second Type")
    @Test
    public void testRunSecondType() throws JobInstanceAlreadyCompleteException,
        JobExecutionAlreadyRunningException,
        JobParametersInvalidException,
        JobRestartException {

        var fetchData = new FetchData(userService,
                                    userAuthenticationService,
                                    CRON_USER_EMAIL,
                                    jobLauncher,
                                    loggingService,
                                    false,
                                    false,
                                    MigrationType.DELTA.name(),
                                    "xml",
                                    fetchDataJob);
        fetchData.run();

        ArgumentCaptor<JobParameters> jobParameters = ArgumentCaptor.forClass(JobParameters.class);

        verify(jobLauncher, times(1)).run(eq(fetchDataJob), jobParameters.capture());

        Assertions.assertEquals(String.valueOf(false),
                                jobParameters.getValue().getString("debug"));

        Assertions.assertEquals(MigrationType.DELTA.name(),
                                jobParameters.getValue().getString("migrationType"));

        verify(loggingService, times(1)).logInfo("Successfully completed Fetch Data batch job");
    }

    @DisplayName("Test FetchData Exception")
    @Test
    public void testRunException() throws JobInstanceAlreadyCompleteException,
        JobExecutionAlreadyRunningException,
        JobParametersInvalidException,
        JobRestartException {

        when(jobLauncher.run(eq(fetchDataJob), any()))
            .thenThrow(new JobExecutionAlreadyRunningException("Test"));

        var fetchData = new FetchData(userService,
                                    userAuthenticationService,
                                    CRON_USER_EMAIL,
                                    jobLauncher,
                                    loggingService,
                                    false,
                                    false,
                                    MigrationType.FULL.name(),
                                    "xml",
                                    fetchDataJob);
        fetchData.run();

        verify(loggingService, times(1))
            .logError(eq("Error starting Fetch Data batch job"),
                      any(JobExecutionAlreadyRunningException.class));
    }
}
