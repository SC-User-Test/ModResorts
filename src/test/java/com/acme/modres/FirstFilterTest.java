package com.acme.modres;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Mock
    private FilterConfig filterConfig;

    private FirstFilter filter;

    @BeforeEach
    void setUp() {
        filter = new FirstFilter();
    }

    @Test
    void testInit_doesNotThrow() {
        assertDoesNotThrow(() -> filter.init(filterConfig));
    }

    @Test
    void testDestroy_doesNotThrow() {
        assertDoesNotThrow(() -> filter.destroy());
    }

    @Test
    void testDoFilter_withUserParam_writesWelcomeWithUser() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(request.getParameter("user")).thenReturn("Alice");
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("Welcome Alice"));
    }

    @Test
    void testDoFilter_withNullUser_writesWelcomeWithDefaultUser() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(request.getParameter("user")).thenReturn(null);
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("Welcome defaultUser"));
    }

    @Test
    void testDoFilter_setsContentTypeToTextPlain() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(request.getParameter("user")).thenReturn("Bob");
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        verify(response).setContentType("text/plain");
    }

    @Test
    void testDoFilter_callsChainDoFilter() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(request.getParameter("user")).thenReturn("Charlie");
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilter_withEmptyUser_writesWelcomeWithEmptyUser() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(request.getParameter("user")).thenReturn("");
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("Welcome "));
    }

    @Test
    void testFilter_isNotNull() {
        assertNotNull(filter);
    }
}
