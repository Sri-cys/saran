package com.psgitech.library.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Filter that attaches a unique server-generated correlation ID to every incoming request.
 * Client-supplied X-Request-ID headers are ignored to prevent log spoofing.
 */
public class RequestIdFilter implements Filter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_ATTR = "com.psgitech.library.requestId";
    public static final String INVOCATION_COUNT_ATTR = "com.psgitech.library.filter.RequestIdFilter.invocations";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            // Track invocations per request for filter-chain verification
            Integer count = (Integer) req.getAttribute(INVOCATION_COUNT_ATTR);
            req.setAttribute(INVOCATION_COUNT_ATTR, (count == null ? 1 : count + 1));

            // Generate clean server-side request correlation ID
            String requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            req.setAttribute(REQUEST_ID_ATTR, requestId);
            res.setHeader(REQUEST_ID_HEADER, requestId);
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}

