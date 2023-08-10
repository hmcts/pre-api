<<<<<<<< HEAD:src/main/java/uk/gov/hmcts/reform/preapi/config/OpenAPIConfiguration.java
package uk.gov.hmcts.reform.preapi.config;
========
package uk.gov.hmcts.reform.pre-api.config;
>>>>>>>> 943a58c4500e409cd9c5c7378b603568d2968ef5:src/main/java/uk/gov/hmcts/reform/pre-api/config/OpenAPIConfiguration.java

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfiguration {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("rpe demo")
                      .description("rpe demo")
                      .version("v0.0.1")
                      .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
            .externalDocs(new ExternalDocumentation()
                              .description("README")
                              .url("https://github.com/hmcts/spring-boot-template"));
    }

}
