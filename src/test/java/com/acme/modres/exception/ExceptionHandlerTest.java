package com.acme.modres.exception;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHandlerTest {

    private static final Logger logger = Logger.getLogger(ExceptionHandlerTest.class.getName());

    @Test
    void testHandleException_withNullException_throwsServletException() {
        assertThrows(ServletException.class, () ->
            ExceptionHandler.handleException(null, "Error occurred", logger)
        );
    }

    @Test
    void testHandleException_withNonNullException_throwsServletException() {
        Exception cause = new RuntimeException("Root cause");
        assertThrows(ServletException.class, () ->
            ExceptionHandler.handleException(cause, "Error occurred", logger)
        );
    }

    @Test
    void testHandleException_withNullException_servletExceptionHasCorrectMessage() {
        try {
            ExceptionHandler.handleException(null, "My error message", logger);
            fail("Expected ServletException");
        } catch (ServletException e) {
            assertEquals("My error message", e.getMessage());
        }
    }

    @Test
    void testHandleException_withNonNullException_servletExceptionHasCorrectMessage() {
        Exception cause = new IllegalArgumentException("bad arg");
        try {
            ExceptionHandler.handleException(cause, "Wrapped error", logger);
            fail("Expected ServletException");
        } catch (ServletException e) {
            assertEquals("Wrapped error", e.getMessage());
        }
    }

    @Test
    void testHandleException_withNonNullException_servletExceptionHasCause() {
        Exception cause = new RuntimeException("original cause");
        try {
            ExceptionHandler.handleException(cause, "Error", logger);
            fail("Expected ServletException");
        } catch (ServletException e) {
            assertNotNull(e.getCause());
            assertEquals("original cause", e.getCause().getMessage());
        }
    }

    @Test
    void testHandleException_withNullException_servletExceptionHasNoCause() {
        try {
            ExceptionHandler.handleException(null, "Error", logger);
            fail("Expected ServletException");
        } catch (ServletException e) {
            // ServletException(String) constructor - cause may be null
            assertNull(e.getCause());
        }
    }

    @Test
    void testHandleException_withIOException_throwsServletException() {
        java.io.IOException ioEx = new java.io.IOException("IO error");
        assertThrows(ServletException.class, () ->
            ExceptionHandler.handleException(ioEx, "IO error occurred", logger)
        );
    }

    @Test
    void testHandleException_withEmptyErrorMessage_throwsServletException() {
        assertThrows(ServletException.class, () ->
            ExceptionHandler.handleException(null, "", logger)
        );
    }

    @Test
    void testHandleException_withNullErrorMessage_throwsServletException() {
        assertThrows(ServletException.class, () ->
            ExceptionHandler.handleException(null, null, logger)
        );
    }
}
