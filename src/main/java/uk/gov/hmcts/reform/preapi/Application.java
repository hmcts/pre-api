<<<<<<<< HEAD:src/main/java/uk/gov/hmcts/reform/preapi/Application.java
package uk.gov.hmcts.reform.preapi;
========
package uk.gov.hmcts.reform.pre-api;
>>>>>>>> 943a58c4500e409cd9c5c7378b603568d2968ef5:src/main/java/uk/gov/hmcts/reform/pre-api/Application.java

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class Application {

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
