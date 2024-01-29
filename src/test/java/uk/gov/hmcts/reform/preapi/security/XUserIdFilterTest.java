package uk.gov.hmcts.reform.preapi.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.PrintWriter;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = XUserIdFilter.class)
public class XUserIdFilterTest {

    @MockBean
    private UserDetailService userDetailService;

    @Autowired
    private XUserIdFilter filter;

    @DisplayName("Should do filter with valid user id and set authentication")
    @Test
    void doFilterValidUserIdSuccess() throws Exception {
        var request = mock(MockHttpServletRequest.class);
        var auth = mock(UserDetails.class);
        var id = UUID.randomUUID();

        when(request.getRequestURI()).thenReturn("/example-uri");
        when(request.getHeader("X-User-Id")).thenReturn(id.toString());
        when(userDetailService.loadAppUserById(id.toString())).thenReturn(auth);

        var response = mock(MockHttpServletResponse.class);
        var filterChain = mock(MockFilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(request, times(1)).getHeader("X-User-Id");
        verify(userDetailService, times(1)).loadAppUserById(id.toString());
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @DisplayName("Should skip filter with when path does not require user id")
    @Test
    void doFilterSkipSuccess() throws Exception {
        var request = mock(MockHttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");

        var response = mock(MockHttpServletResponse.class);
        var filterChain = mock(MockFilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(request, times(1)).getRequestURI();
        verify(request, never()).getHeader("X-User-Id");
        verify(userDetailService, never()).loadAppUserById(any());
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @DisplayName("Should do filter and return 401 error when user id is not valid")
    @Test
    void doFilterNotFoundUserIdSuccess() throws Exception {
        var request = mock(MockHttpServletRequest.class);
        var response = mock(MockHttpServletResponse.class);
        var id = UUID.randomUUID();
        var writer = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);
        when(request.getRequestURI()).thenReturn("/example-uri");
        when(request.getHeader("X-User-Id")).thenReturn(id.toString());
        doThrow(
            new BadCredentialsException("Unauthorised user: " + id)
        ).when(userDetailService).loadAppUserById(id.toString());

        var filterChain = mock(MockFilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(request, times(1)).getHeader("X-User-Id");
        verify(userDetailService, times(1)).loadAppUserById(id.toString());
        verify(response, times(1)).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(writer).print("{\"message\": \"Unauthorised user: " + id + "\"}");
        verify(writer).flush();
        verify(writer).close();
        verify(filterChain, never()).doFilter(request, response);
    }
}
