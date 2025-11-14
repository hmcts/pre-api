package uk.gov.hmcts.reform.preapi.component;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.component.util.CommandRunner;
import uk.gov.hmcts.reform.preapi.exception.CommandExecutionException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
public class CommandExecutor {
    public boolean execute(final CommandLine commandLine) {
        return executeAndGetOutput(commandLine) != null;
    }

    public String executeAndGetOutput(final CommandLine commandLine) {
        log.info("Executing command: {}", commandLine);
        try {
            @Cleanup("shutdown") final ExecutorService executor = Executors.newSingleThreadExecutor();
            final Future<String> future = executor.submit(new CommandRunner(commandLine));
            return future.get();
        } catch (InterruptedException e) {
            log.error("Failed to execute system command {} due to {}", commandLine, e.getMessage());
            Thread.currentThread().interrupt();
        } catch (ExecutionException | CommandExecutionException e) {
            log.error("Failed to execute system command {} due to {}", commandLine, e.getMessage());
        }
        return null;
    }
}
