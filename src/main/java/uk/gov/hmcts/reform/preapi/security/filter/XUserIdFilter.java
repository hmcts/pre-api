package uk.gov.hmcts.reform.preapi.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Cleanup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import uk.gov.hmcts.reform.preapi.config.SecurityConfig;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

@Component
public class XUserIdFilter extends GenericFilterBean {

    private final UserAuthenticationService userAuthenticationService;

    @Autowired
    public XUserIdFilter(UserAuthenticationService userAuthenticationService) {
        super();
        this.userAuthenticationService = userAuthenticationService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;

        if (!applyAuth(request)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        try {
            String id = request.getHeader(X_USER_ID_HEADER);
            Authentication authentication = userAuthenticationService.loadAppUserById(id);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            writeErrorResponse(e, (HttpServletResponse) servletResponse);
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean applyAuth(HttpServletRequest request) {
        return Arrays.stream(SecurityConfig.NOT_AUTHORIZED_URIS)
            .noneMatch(antPathRequestMatcher -> antPathRequestMatcher.matches(request));
    }

    private void writeErrorResponse(Exception exception, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        @Cleanup PrintWriter writer = response.getWriter();
        writer.print("{\"message\": \"" + exception.getMessage() + "\"}");
        writer.flush();
    }
}
