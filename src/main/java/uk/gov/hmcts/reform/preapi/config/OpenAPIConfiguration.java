package uk.gov.hmcts.reform.preapi.config;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenAPIConfiguration {

    public static final String X_USER_ID_HEADER = "X-User-Id";
    public static final String APIM_SUBSCRIPTION_KEY_HEADER = "Ocp-Apim-Subscription-Key";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("PRE API")
                      .description("PRE API - Used for managing courts, bookings, recordings and permissions.")
                      .version("v0.0.1")
                      .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
            .addSecurityItem(new SecurityRequirement().addList(APIM_SUBSCRIPTION_KEY_HEADER))
            .components(new Components()
                            .addSecuritySchemes(APIM_SUBSCRIPTION_KEY_HEADER,
                                                new SecurityScheme()
                                                    .type(SecurityScheme.Type.APIKEY)
                                                    .in(SecurityScheme.In.HEADER)
                                                    .name(APIM_SUBSCRIPTION_KEY_HEADER)
                            ))
            .externalDocs(new ExternalDocumentation()
                              .description("README")
                              .url("https://github.com/hmcts/pre-api"));
    }

    @Bean
    public OperationCustomizer customGlobalHeaders() {
        return (Operation customOperation, HandlerMethod handlerMethod) -> {
            Parameter serviceAuthorizationHeader = new Parameter()
                .in(ParameterIn.HEADER.toString())
                .schema(new UUIDSchema())
                .name(X_USER_ID_HEADER)
                .description("The User Id of the User making the request")
                .example("123e4567-e89b-12d3-a456-426614174000")
                .required(false); // set to true once Power Platform is updated.
            customOperation.addParametersItem(serviceAuthorizationHeader);
            return customOperation;
        };
    }

    @Bean
    public GroupedOpenApi publicApi(OperationCustomizer customGlobalHeaders) {
        return GroupedOpenApi.builder()
            .group("pre-api")
            .pathsToMatch("/**")
            .pathsToExclude("/b2c/**")
            .addOperationCustomizer(customGlobalHeaders)
            .build();
    }

    @Bean
    public GroupedOpenApi b2cApi() {
        return GroupedOpenApi.builder()
            .group("b2c-api")
            .pathsToMatch("/b2c/**")
            .build();
    }
}
