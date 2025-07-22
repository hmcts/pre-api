package uk.gov.hmcts.reform.preapi.component.util;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.output.ByteArrayOutputStream;
import uk.gov.hmcts.reform.preapi.exception.CommandExecutionException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Slf4j
@AllArgsConstructor
public final class CommandRunner implements Callable<String> {
    @NonNull
    private final CommandLine commandLine;

    @Override
    public String call() {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        var exec = DefaultExecutor.builder().get();
        var streamHandler = new PumpStreamHandler(stdout, stderr);
        exec.setStreamHandler(streamHandler);

        try {
            exec.execute(commandLine);
        } catch (Exception e) {
            String stdErrOutput = stderr.toString(StandardCharsets.UTF_8);
            String stdOutOutput = stdout.toString(StandardCharsets.UTF_8);
            throw new CommandExecutionException("Command failed:"
                                                    + "\nSTDOUT: " + stdOutOutput
                                                    + "\nSTDERR: " + stdErrOutput, e);
        }

        var outputString = stdout.toString(StandardCharsets.UTF_8);
        log.debug("Command output: {} ", outputString);
        return outputString;
    }
}
