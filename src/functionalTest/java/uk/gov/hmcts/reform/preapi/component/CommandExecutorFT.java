package uk.gov.hmcts.reform.preapi.component;

import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class CommandExecutorFT {

    @Autowired
    private CommandExecutor commandExecutor;

    @Test
    @DisplayName("Should have access to use ffmpeg command")
    void ffmpeg() {
        assertTrue(commandExecutor.execute(new CommandLine("ffmpeg").addArgument("-version")));
        assertTrue(commandExecutor.execute(new CommandLine("ffprobe").addArgument("-version")));
    }
}
