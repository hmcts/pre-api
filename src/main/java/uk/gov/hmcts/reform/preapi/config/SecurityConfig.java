package uk.gov.hmcts.reform.preapi.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uk.gov.hmcts.reform.preapi.security.UserDetailService;
import uk.gov.hmcts.reform.preapi.security.XUserIdFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailService userDetailService;

    public static final String[] NOT_AUTHORIZED_URIS = new String[] {
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/v3/api-docs",
        "testing-support/**"
    };

    @Autowired
    public SecurityConfig(UserDetailService userDetailService) {
        this.userDetailService = userDetailService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
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
            .addFilterBefore(new XUserIdFilter(userDetailService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
