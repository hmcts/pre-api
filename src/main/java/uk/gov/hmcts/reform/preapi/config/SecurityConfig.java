package uk.gov.hmcts.reform.preapi.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import uk.gov.hmcts.reform.preapi.security.filter.XUserIdFilter;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserAuthenticationService userAuthenticationService;

    public static final AntPathRequestMatcher[] NOT_AUTHORIZED_URIS = new AntPathRequestMatcher[] {
        new AntPathRequestMatcher("/testing-support/**"),
        new AntPathRequestMatcher("/swagger-ui/**"),
        new AntPathRequestMatcher("/v3/api-docs/**"),
        new AntPathRequestMatcher("/v3/api-docs"),
        new AntPathRequestMatcher("/health/**"),
        new AntPathRequestMatcher("/health"),
        new AntPathRequestMatcher("/info"),
        new AntPathRequestMatcher("/prometheus"),
        new AntPathRequestMatcher("/users/by-email/**"),
        new AntPathRequestMatcher("/reports/**"),
        new AntPathRequestMatcher("/audit/**"),
        new AntPathRequestMatcher("/error"),
        new AntPathRequestMatcher("/invites", "GET"),
        new AntPathRequestMatcher("/invites/redeem", "POST"),
        new AntPathRequestMatcher("/app-terms-and-conditions/latest"),
        new AntPathRequestMatcher("/portal-terms-and-conditions/latest"),
        new AntPathRequestMatcher("/batch", "POST"),
        new AntPathRequestMatcher("/batch/start", "POST"),
        new AntPathRequestMatcher("/batch/startXml", "POST"),
        new AntPathRequestMatcher("/batch/startTransform", "POST"),
    };

    @Autowired
    public SecurityConfig(UserAuthenticationService userAuthenticationService) {
        this.userAuthenticationService = userAuthenticationService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
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
