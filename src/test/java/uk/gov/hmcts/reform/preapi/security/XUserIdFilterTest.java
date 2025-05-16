package uk.gov.hmcts.reform.preapi.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Cleanup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.filter.XUserIdFilter;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;

import java.io.PrintWriter;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

@SpringBootTest(classes = XUserIdFilter.class)
public class XUserIdFilterTest {

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @Autowired
    private XUserIdFilter filter;

    @DisplayName("Should do filter with valid user id and set authentication")
    @Test
    void doFilterValidUserIdSuccess() throws Exception {
        var request = mock(MockHttpServletRequest.class);
        var auth = mock(UserAuthentication.class);
        var id = UUID.randomUUID();

        when(request.getRequestURI()).thenReturn("/example-uri");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(X_USER_ID_HEADER)).thenReturn(id.toString());
        when(userAuthenticationService.loadAppUserById(id.toString())).thenReturn(auth);
        when(request.getServletPath()).thenReturn("/example-uri");
        when(request.getPathInfo()).thenReturn("/example-uri");

        var response = mock(MockHttpServletResponse.class);
        var filterChain = mock(MockFilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(request, times(1)).getHeader(X_USER_ID_HEADER);
        verify(userAuthenticationService, times(1)).loadAppUserById(id.toString());
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @DisplayName("Should skip filter with when path does not require user id")
    @Test
    void doFilterSkipSuccess() throws Exception {
        var request = mock(MockHttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");
        when(request.getServletPath()).thenReturn("/swagger-ui/index.html");
        when(request.getPathInfo()).thenReturn("/swagger-ui/index.html");

        var response = mock(MockHttpServletResponse.class);
        var filterChain = mock(MockFilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(request, never()).getHeader(X_USER_ID_HEADER);
        verify(userAuthenticationService, never()).loadAppUserById(any());
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @DisplayName("Should do filter and return 401 error when user id is not valid")
    @Test
    void doFilterNotFoundUserIdSuccess() throws Exception {
        var request = mock(MockHttpServletRequest.class);
        var response = mock(MockHttpServletResponse.class);
        var id = UUID.randomUUID();
        @Cleanup var writer = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);
        when(request.getRequestURI()).thenReturn("/example-uri");
        when(request.getHeader(X_USER_ID_HEADER)).thenReturn(id.toString());
        when(request.getServletPath()).thenReturn("/example-uri");
        when(request.getPathInfo()).thenReturn("/example-uri");

        doThrow(
            new BadCredentialsException("Unauthorised user: " + id)
        ).when(userAuthenticationService).loadAppUserById(id.toString());

        var filterChain = mock(MockFilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(request, times(1)).getHeader(X_USER_ID_HEADER);
        verify(userAuthenticationService, times(1)).loadAppUserById(id.toString());
        verify(response, times(1)).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(writer).print("{\"message\": \"Unauthorised user: " + id + "\"}");
        verify(writer).flush();
        verify(writer).close();
        verify(filterChain, never()).doFilter(request, response);
    }

    @DisplayName("Should continue filter chain when already has authentication object")
    @Test
    void doFilterAlreadyHasAuthenticationObjectSuccess() throws Exception {
        var request = mock(MockHttpServletRequest.class);
        var auth = mock(UserAuthentication.class);
        SecurityContextHolder.getContext().setAuthentication(auth);
        var id = UUID.randomUUID();

        when(request.getRequestURI()).thenReturn("/example-uri");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(X_USER_ID_HEADER)).thenReturn(id.toString());
        when(request.getServletPath()).thenReturn("/example-uri");
        when(request.getPathInfo()).thenReturn("/example-uri");

        var response = mock(MockHttpServletResponse.class);
        var filterChain = mock(MockFilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(userAuthenticationService, never()).loadAppUserById(id.toString());
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
