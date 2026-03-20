package uk.gov.hmcts.reform.preapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.hmcts.reform.preapi.utils.InputSanitizer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * ALTERNATIVE APPROACH: Filter that sanitizes all request parameters and headers.
 * This is commented out by default - use @SanitizedStringConstraint on DTOs instead.
 *
 * To enable this filter, uncomment the @Component annotation.
 * Note: This approach sanitizes ALL string inputs, which may be too aggressive.
 */
// @Component
// @Order(Ordered.HIGHEST_PRECEDENCE)
@SuppressWarnings("unused") // This is an optional alternative approach
public class InputSanitizationFilter extends OncePerRequestFilter {

    @Override
    @SuppressWarnings("NullableProblems") // Spring framework guarantees non-null parameters
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Wrap the request to sanitize parameters
        SanitizedHttpServletRequest sanitizedRequest = new SanitizedHttpServletRequest(request);

        filterChain.doFilter(sanitizedRequest, response);
    }

    /**
     * Request wrapper that sanitizes parameter values
     */
    private static class SanitizedHttpServletRequest extends HttpServletRequestWrapper {
        private final Map<String, String[]> sanitizedParameterMap;

        public SanitizedHttpServletRequest(HttpServletRequest request) {
            super(request);
            this.sanitizedParameterMap = sanitizeParameters(request.getParameterMap());
        }

        @Override
        public String getParameter(String name) {
            String[] values = sanitizedParameterMap.get(name);
            return (values != null && values.length > 0) ? values[0] : null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.unmodifiableMap(sanitizedParameterMap);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(sanitizedParameterMap.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return sanitizedParameterMap.get(name);
        }

        private Map<String, String[]> sanitizeParameters(Map<String, String[]> originalMap) {
            Map<String, String[]> sanitizedMap = new HashMap<>();

            for (Map.Entry<String, String[]> entry : originalMap.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();

                if (values != null) {
                    String[] sanitizedValues = Arrays.stream(values)
                        .map(InputSanitizer::sanitize)
                        .toArray(String[]::new);
                    sanitizedMap.put(key, sanitizedValues);
                } else {
                    sanitizedMap.put(key, null);
                }
            }

            return sanitizedMap;
        }
    }
}

