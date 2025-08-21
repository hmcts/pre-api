package uk.gov.hmcts.reform.preapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@EnableFeignClients
@EnableScheduling
@EnableCaching
@EnableAsync(proxyTargetClass = true)
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class Application implements CommandLineRunner {

    @Autowired
    ScheduledTaskRunner taskRunner;

    public static void main(final String[] args) {
        final SpringApplication application = new SpringApplication(Application.class);
        final var instance = application.run(args); //NOPMD - suppressed CloseResource

        if (System.getenv("TASK_NAME") != null) {
            instance.close();
        }
    }

    @Override
    public void run(String... args) {
        if (System.getenv("TASK_NAME") != null) {
            taskRunner.run(System.getenv("TASK_NAME"));
        }
    }
}
