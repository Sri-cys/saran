package com.psgitech.library;

import com.psgitech.library.enums.Role;
import com.psgitech.library.filter.AuthenticationFilter;
import com.psgitech.library.filter.AuthorizationFilter;
import com.psgitech.library.filter.ConcurrencyFilter;
import com.psgitech.library.filter.RequestIdFilter;
import com.psgitech.library.model.User;
import org.junit.Test;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class FilterChainInvocationTest {

    @Test
    public void testFiltersExecuteInStrictOrderAndExactlyOncePerRequest() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        StringWriter responseBody = new StringWriter();
        PrintWriter writer = new PrintWriter(responseBody);

        User mockAdmin = new com.psgitech.library.model.Admin(1, "Admin Office", "admin@psgitech.ac.in", "hash", "9876543210", true);

        HttpSession mockSession = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) {
                        if ("user".equals(args[0])) return mockAdmin;
                    }
                    return null;
                }
        );

        HttpServletRequest mockReq = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) return "POST";
                    if ("getRequestURI".equals(name)) return "/psgitech_library/borrow/issue";
                    if ("getContextPath".equals(name)) return "/psgitech_library";
                    if ("getServletPath".equals(name)) return "/borrow/issue";
                    if ("getPathInfo".equals(name)) return null;
                    if ("getDispatcherType".equals(name)) return DispatcherType.REQUEST;
                    if ("getSession".equals(name)) return mockSession;
                    if ("getAttribute".equals(name)) return attributes.get(args[0]);
                    if ("setAttribute".equals(name)) {
                        attributes.put((String) args[0], args[1]);
                        return null;
                    }
                    if ("removeAttribute".equals(name)) {
                        attributes.remove(args[0]);
                        return null;
                    }
                    if ("getCookies".equals(name)) return null;
                    return null;
                }
        );

        HttpServletResponse mockRes = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setHeader".equals(name)) {
                        headers.put((String) args[0], (String) args[1]);
                        return null;
                    }
                    if ("isCommitted".equals(name)) return false;
                    if ("getWriter".equals(name)) return writer;
                    return null;
                }
        );

        // Track execution order
        List<String> executionOrder = new ArrayList<>();

        RequestIdFilter reqIdFilter = new RequestIdFilter();
        AuthenticationFilter authFilter = new AuthenticationFilter();
        authFilter.init(null);
        AuthorizationFilter azFilter = new AuthorizationFilter();
        azFilter.init(null);
        ConcurrencyFilter concFilter = new ConcurrencyFilter();
        concFilter.init(null);

        // Chained execution representing web.xml order
        FilterChain finalServlet = (req, res) -> executionOrder.add("Servlet");
        FilterChain chain4 = (req, res) -> { executionOrder.add("ConcurrencyFilter"); concFilter.doFilter(req, res, finalServlet); };
        FilterChain chain3 = (req, res) -> { executionOrder.add("AuthorizationFilter"); azFilter.doFilter(req, res, chain4); };
        FilterChain chain2 = (req, res) -> { executionOrder.add("AuthenticationFilter"); authFilter.doFilter(req, res, chain3); };

        executionOrder.add("RequestIdFilter");
        reqIdFilter.doFilter(mockReq, mockRes, chain2);

        // Verify strict execution order
        assertEquals("RequestIdFilter", executionOrder.get(0));
        assertEquals("AuthenticationFilter", executionOrder.get(1));
        assertEquals("AuthorizationFilter", executionOrder.get(2));
        assertEquals("ConcurrencyFilter", executionOrder.get(3));
        assertEquals("Servlet", executionOrder.get(4));

        // Verify each filter executed EXACTLY ONCE
        assertEquals("RequestIdFilter must execute exactly once",
                Integer.valueOf(1), attributes.get(RequestIdFilter.INVOCATION_COUNT_ATTR));
        assertEquals("AuthenticationFilter must execute exactly once",
                Integer.valueOf(1), attributes.get(AuthenticationFilter.INVOCATION_COUNT_ATTR));
        assertEquals("AuthorizationFilter must execute exactly once",
                Integer.valueOf(1), attributes.get(AuthorizationFilter.INVOCATION_COUNT_ATTR));
        assertEquals("ConcurrencyFilter must execute exactly once",
                Integer.valueOf(1), attributes.get(ConcurrencyFilter.INVOCATION_COUNT_ATTR));

        // Verify correlation ID was set
        assertNotNull(attributes.get(RequestIdFilter.REQUEST_ID_ATTR));
        assertTrue(headers.containsKey(RequestIdFilter.REQUEST_ID_HEADER));
    }
}
