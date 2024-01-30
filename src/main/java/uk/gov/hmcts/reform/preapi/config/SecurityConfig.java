package uk.gov.hmcts.reform.preapi.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uk.gov.hmcts.reform.preapi.security.filter.XUserIdFilter;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserAuthenticationService userAuthenticationService;

    public static String[] NOT_AUTHORIZED_URIS = new String[] {
        "/testing-support/**",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/v3/api-docs",
        "/health/**",
        "/health",
        "/info",
        "/prometheus",
        "/users/by-email/**"
    };

    @Value("${testing-support-endpoints.enabled:false}")
    private String testingEndpointActive;

    @Autowired
    public SecurityConfig(UserAuthenticationService userAuthenticationService) {
        this.userAuthenticationService = userAuthenticationService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if ("true".equals(testingEndpointActive)) {
            http.csrf(AbstractHttpConfigurer::disable);
        }

        http
            .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                                       authorizationManagerRequestMatcherRegistry
                                           .requestMatchers(NOT_AUTHORIZED_URIS).permitAll()
                                           .requestMatchers("/**").fullyAuthenticated()
            )
            .authenticationManager(authentication -> authentication)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(httpSecuritySessionManagementConfigurer ->
                                   httpSecuritySessionManagementConfigurer
                                       .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new XUserIdFilter(userAuthenticationService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
