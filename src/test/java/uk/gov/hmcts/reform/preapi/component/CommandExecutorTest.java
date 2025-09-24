package uk.gov.hmcts.reform.preapi.component;

import lombok.Cleanup;
import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.component.util.CommandRunner;
import uk.gov.hmcts.reform.preapi.exception.CommandExecutionException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CommandExecutor.class)
public class CommandExecutorTest {

    @Mock
    private Future<String> mockFuture;

    @Autowired
    private CommandExecutor commandExecutor;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should return true when command successfully runs")
    void executeSuccess() throws Exception {
        @Cleanup var executors = mockStatic(Executors.class);
        var executorService = mock(ExecutorService.class);
        executors.when(Executors::newSingleThreadExecutor).thenReturn(executorService);
        var command = CommandLine.parse("echo hello world");
        when(mockFuture.get()).thenReturn("hello world\n");
        when(executorService.submit(any(CommandRunner.class))).thenReturn(mockFuture);

        var result = commandExecutor.execute(command);

        assertThat(result).isTrue();

        verify(executorService, times(1)).submit(any(CommandRunner.class));
        verify(mockFuture, times(1)).get();
    }

    @Test
    @DisplayName("Should return false when encounters ExecutionException")
    void executeExecutionException() throws Exception {
        @Cleanup var executors = mockStatic(Executors.class);
        var executorService = mock(ExecutorService.class);
        executors.when(Executors::newSingleThreadExecutor).thenReturn(executorService);
        var command = CommandLine.parse("echo hello world");
        when(executorService.submit(any(CommandRunner.class))).thenReturn(mockFuture);
        when(mockFuture.get()).thenThrow(ExecutionException.class);

        var result = commandExecutor.execute(command);

        assertThat(result).isFalse();

        verify(executorService, times(1)).submit(any(CommandRunner.class));
        verify(mockFuture, times(1)).get();
    }

    @Test
    @DisplayName("Should return false when encounters InterruptedException")
    void executeInterruptedException() throws Exception {
        @Cleanup var executors = mockStatic(Executors.class);
        var executorService = mock(ExecutorService.class);
        executors.when(Executors::newSingleThreadExecutor).thenReturn(executorService);
        var command = CommandLine.parse("echo hello world");
        when(executorService.submit(any(CommandRunner.class))).thenReturn(mockFuture);
        when(mockFuture.get()).thenThrow(InterruptedException.class);

        var result = commandExecutor.execute(command);

        assertThat(result).isFalse();

        verify(executorService, times(1)).submit(any(CommandRunner.class));
        verify(mockFuture, times(1)).get();
    }

    @Test
    @DisplayName("Should return false when encounters InterruptedException")
    void executeCommandExecutionException() throws Exception {
        @Cleanup var executors = mockStatic(Executors.class);
        var executorService = mock(ExecutorService.class);
        executors.when(Executors::newSingleThreadExecutor).thenReturn(executorService);
        var command = CommandLine.parse("echo hello world");
        when(executorService.submit(any(CommandRunner.class))).thenThrow(CommandExecutionException.class);

        var result = commandExecutor.execute(command);

        assertThat(result).isFalse();

        verify(executorService, times(1)).submit(any(CommandRunner.class));
        verify(mockFuture, never()).get();
    }
}
