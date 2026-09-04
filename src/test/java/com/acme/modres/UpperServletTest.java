package com.acme.modres;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpperServletTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private UpperServlet servlet;
    private StringWriter stringWriter;
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() throws IOException {
        servlet = new UpperServlet();
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
    }

    @Test
    void testDoGet_withNormalInput_returnsUpperCase() throws Exception {
        when(request.getParameter("input")).thenReturn("hello");
        servlet.doGet(request, response);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("HELLO"));
    }

    @Test
    void testDoGet_withNullInput_returnsEmptyUpperCase() throws Exception {
        when(request.getParameter("input")).thenReturn(null);
        servlet.doGet(request, response);
        printWriter.flush();
        String output = stringWriter.toString();
        assertNotNull(output);
    }

    @Test
    void testDoGet_withMixedCaseInput_returnsAllUpperCase() throws Exception {
        when(request.getParameter("input")).thenReturn("HeLLo WoRLd");
        servlet.doGet(request, response);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("HELLO WORLD"));
    }

    @Test
    void testDoGet_withSpecialCharacters_encodesHtml() throws Exception {
        when(request.getParameter("input")).thenReturn("<script>");
        servlet.doGet(request, response);
        printWriter.flush();
        String output = stringWriter.toString();
        assertFalse(output.contains("<SCRIPT>"));
        assertTrue(output.contains("&lt;SCRIPT&gt;"));
    }

    @Test
    void testDoGet_withAmpersand_encodesHtml() throws Exception {
        when(request.getParameter("input")).thenReturn("a&b");
        servlet.doGet(request, response);
        printWriter.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("&amp;"));
    }

    @Test
    void testDoGet_withDoubleQuote_encodesHtml() throws Exception {
        when(request.getParameter("input")).thenReturn("say \"hello\"");
        servlet.doGet(request, response);
        printWriter.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("&quot;"));
    }

    @Test
    void testDoGet_withSingleQuote_encodesHtml() throws Exception {
        when(request.getParameter("input")).thenReturn("it's");
        servlet.doGet(request, response);
        printWriter.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("&#x27;"));
    }

    @Test
    void testDoGet_setsContentTypeToHtml() throws Exception {
        when(request.getParameter("input")).thenReturn("test");
        servlet.doGet(request, response);
        verify(response).setContentType("text/html");
    }

    @Test
    void testDoGet_withEmptyInput_returnsEmptyUpperCase() throws Exception {
        when(request.getParameter("input")).thenReturn("");
        servlet.doGet(request, response);
        printWriter.flush();
        assertNotNull(stringWriter.toString());
    }

    @Test
    void testDoGet_withNumericInput_returnsUpperCase() throws Exception {
        when(request.getParameter("input")).thenReturn("abc123");
        servlet.doGet(request, response);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("ABC123"));
    }
}
