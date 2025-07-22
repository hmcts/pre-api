package uk.gov.hmcts.reform.preapi.component.util;

import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.exception.CommandExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CommandRunnerTest {
    @Test
    @DisplayName("Should return command output when runs successfully")
    void callSuccess() throws Exception {
        var commandLine = CommandLine.parse("echo Mock output");
        var runner = new CommandRunner(commandLine);
        var result = runner.call();

        assertThat(result).isEqualTo("Mock output\n");
    }

    @Test
    @DisplayName("Should throw command execution exception when error occurs when running command")
    void callError() {
        var commandLine = CommandLine.parse("invalidCommand");
        var runner = new CommandRunner(commandLine);

        var message = assertThrows(
            CommandExecutionException.class,
            runner::call
        ).getMessage();

        assertThat(message).isEqualTo("Command failed with this output: ");
    }
}
