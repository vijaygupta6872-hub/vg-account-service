package com.vijay.account.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that establishes request-scoped trace identifier propagation
 * for the Account Service.
 *
 * <p>The filter reads the inbound {@code X-Trace-Id} header when present or
 * generates a UUID when the caller does not provide one. The resolved value is
 * stored in the logging MDC under {@code traceId}, returned to the caller in the
 * response header, and cleared after request processing completes. This enables
 * consistent correlation across HTTP responses, structured logs, controllers,
 * and service-layer operations.</p>
 *
 * @see OncePerRequestFilter
 * @see MDC
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    /**
     * Resolves, stores, propagates, and clears the trace identifier for one HTTP
     * request.
     *
     * <p>The method prefers the caller-supplied {@code X-Trace-Id} header. When
     * the header is absent or blank, a UUID is generated. The trace identifier is
     * added to MDC before the request continues through the filter chain and is
     * removed in a {@code finally} block to avoid leaking request context across
     * servlet container threads.</p>
     *
     * @param request the current HTTP servlet request
     * @param response the current HTTP servlet response
     * @param filterChain the servlet filter chain used to continue request
     *        processing
     * @throws ServletException if downstream request processing fails with a
     *         servlet error
     * @throws IOException if downstream request processing fails with an I/O
     *         error
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
