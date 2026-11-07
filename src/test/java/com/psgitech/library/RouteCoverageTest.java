package com.psgitech.library;

import com.psgitech.library.concurrency.RouteClassifier;
import org.junit.Test;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class RouteCoverageTest {

    @Test
    public void testAllServletsWithMutatingMethodsAreClassifiedOrExcluded() throws Exception {
        List<Class<?>> servletClasses = findServletClasses("com.psgitech.library.servlet");
        assertFalse("Must find servlet classes", servletClasses.isEmpty());

        List<String> unmappedMutations = new ArrayList<>();

        for (Class<?> clazz : servletClasses) {
            WebServlet annotation = clazz.getAnnotation(WebServlet.class);
            if (annotation == null) continue;

            boolean hasPost = hasOverriddenMethod(clazz, "doPost");
            boolean hasPut = hasOverriddenMethod(clazz, "doPut");
            boolean hasDelete = hasOverriddenMethod(clazz, "doDelete");
            boolean hasPatch = hasOverriddenMethod(clazz, "doPatch");

            if (hasPost || hasPut || hasDelete || hasPatch) {
                String[] urlPatterns = annotation.urlPatterns().length > 0 ? annotation.urlPatterns() : annotation.value();
                for (String pattern : urlPatterns) {
                    String normalized = pattern.endsWith("/") && pattern.length() > 1 
                            ? pattern.substring(0, pattern.length() - 1) 
                            : pattern;

                    boolean isMapped = RouteClassifier.getPostRoutes().containsKey(normalized);
                    boolean isExcluded = RouteClassifier.isExplicitlyExcluded(normalized);

                    if (!isMapped && !isExcluded) {
                        unmappedMutations.add(clazz.getSimpleName() + " maps " + pattern + " but is neither classified nor excluded!");
                    }
                }
            }
        }

        if (!unmappedMutations.isEmpty()) {
            fail("Route coverage failure! Unclassified mutating routes detected:\n" + String.join("\n", unmappedMutations));
        }
    }

    private boolean hasOverriddenMethod(Class<?> clazz, String methodName) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private List<Class<?>> findServletClasses(String packageName) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL res = cl.getResource(path);
        if (res != null) {
            File dir = new File(res.toURI());
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".class")) {
                            String className = packageName + '.' + f.getName().substring(0, f.getName().length() - 6);
                            Class<?> c = Class.forName(className);
                            if (HttpServlet.class.isAssignableFrom(c)) {
                                classes.add(c);
                            }
                        }
                    }
                }
            }
        }
        return classes;
    }
}

