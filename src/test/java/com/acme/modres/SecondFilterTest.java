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

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecondFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Mock
    private FilterConfig filterConfig;

    private SecondFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecondFilter();
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
    void testDoFilter_withBodyContent_writesContent() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        String body = "Hello";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("Hello"));
    }

    @Test
    void testDoFilter_setsContentTypeToTextPlain() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        verify(response).setContentType("text/plain");
    }

    @Test
    void testDoFilter_callsChainDoFilter() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("test")));
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilter_withEmptyBody_writesEmptyContent() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("to our site!"));
    }

    @Test
    void testDoFilter_appendsSiteMessage() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("Welcome")));
        when(response.getWriter()).thenReturn(printWriter);
        filter.doFilter(request, response, chain);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("to our site!"));
    }

    @Test
    void testFilter_isNotNull() {
        assertNotNull(filter);
    }
}
