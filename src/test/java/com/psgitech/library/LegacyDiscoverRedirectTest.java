package com.psgitech.library;

import com.psgitech.library.servlet.LegacyDiscoverRedirectServlet;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class LegacyDiscoverRedirectTest {

    @Test
    public void testLegacyDiscoverRedirectsToIndexHtml() throws Exception {
        LegacyDiscoverRedirectServlet servlet = new LegacyDiscoverRedirectServlet();
        AtomicReference<String> redirectedUrl = new AtomicReference<>();

        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    if ("getContextPath".equals(m.getName())) return "/library";
                    if ("getQueryString".equals(m.getName())) return null;
                    if ("getMethod".equals(m.getName())) return "GET";
                    return null;
                }
        );

        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    if ("sendRedirect".equals(m.getName())) {
                        redirectedUrl.set((String) args[0]);
                        return null;
                    }
                    return null;
                }
        );

        servlet.service(req, resp);

        assertEquals("/library/index.html", redirectedUrl.get());
    }

    @Test
    public void testLegacyDiscoverPreservesQueryParametersOnRedirect() throws Exception {
        LegacyDiscoverRedirectServlet servlet = new LegacyDiscoverRedirectServlet();
        AtomicReference<String> redirectedUrl = new AtomicReference<>();

        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    if ("getContextPath".equals(m.getName())) return "/library";
                    if ("getQueryString".equals(m.getName())) return "q=Algorithms&category=CSE";
                    if ("getMethod".equals(m.getName())) return "GET";
                    return null;
                }
        );

        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    if ("sendRedirect".equals(m.getName())) {
                        redirectedUrl.set((String) args[0]);
                        return null;
                    }
                    return null;
                }
        );

        servlet.service(req, resp);

        assertEquals("/library/index.html?q=Algorithms&category=CSE", redirectedUrl.get());
    }
}
