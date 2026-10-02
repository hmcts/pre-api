package uk.gov.hmcts.reform.preapi.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uk.gov.hmcts.reform.preapi.security.filter.XUserIdFilter;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.Boolean.parseBoolean;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Value("${security.csrf-disabled:false}") String csrfDisabled;

    private final UserAuthenticationService userAuthenticationService;

    public static final String[] PERMITTED_URIS_ALL_REQUESTS = {
        "/testing-support/**",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/v3/api-docs",
        "/health/**",
        "/health",
        "/info",
        "/prometheus",
        "/users/by-email/**",
        "/reports/**",
        "/b2c/**",
        "/error",
        "/app-terms-and-conditions/latest",
        "/portal-terms-and-conditions/latest"
    };

    public static final String[] PERMITTED_URIS_GET_ONLY = {
        "/invites",
    };

    public static final String[] PERMITTED_URIS_PUT_ONLY = {
        "/audit/**",
    };

    public static final String[] PERMITTED_URIS_POST = {
        "/invites/redeem",
        "/batch",
        "/batch/fetch-xml",
        "/batch/process-migration",
        "/batch/post-migration-tasks",
        "/batch/migrate-exclusions",
    };

    @Autowired
    public SecurityConfig(UserAuthenticationService userAuthenticationService) {
        this.userAuthenticationService = userAuthenticationService;
    }

    @Bean
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        List<String> csrfDisabledEndpoints = new ArrayList<>(PERMITTED_URIS_POST.length
                                                                 + PERMITTED_URIS_GET_ONLY.length
                                                                 + PERMITTED_URIS_PUT_ONLY.length
                                                                 + PERMITTED_URIS_ALL_REQUESTS.length);
        Collections.addAll(csrfDisabledEndpoints, PERMITTED_URIS_ALL_REQUESTS);
        Collections.addAll(csrfDisabledEndpoints, PERMITTED_URIS_GET_ONLY);
        Collections.addAll(csrfDisabledEndpoints, PERMITTED_URIS_PUT_ONLY);
        Collections.addAll(csrfDisabledEndpoints, PERMITTED_URIS_POST);

        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(csrfDisabledEndpoints.toArray(String[]::new))
            )
            .authorizeHttpRequests(authorize ->
                                       authorize
                                           .requestMatchers(HttpMethod.GET, PERMITTED_URIS_GET_ONLY).permitAll()
                                           .requestMatchers(HttpMethod.POST, PERMITTED_URIS_POST).permitAll()
                                           .requestMatchers(HttpMethod.PUT, PERMITTED_URIS_PUT_ONLY).permitAll()
                                           .requestMatchers(PERMITTED_URIS_ALL_REQUESTS).permitAll()
                                           .anyRequest().authenticated()
            )
            .authenticationManager(authentication -> authentication)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(httpSecuritySessionManagementConfigurer ->
                                   httpSecuritySessionManagementConfigurer
                                       .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new XUserIdFilter(userAuthenticationService), UsernamePasswordAuthenticationFilter.class);

        if (parseBoolean(csrfDisabled)) {
            http.csrf(AbstractHttpConfigurer::disable);
        }

        return http.build();
    }
}
