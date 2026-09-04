package com.acme.modres.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SSLUtilsTest {

    @Test
    void testClass_canBeInstantiated() {
        // SSLUtils has no public methods but can be instantiated
        SSLUtils sslUtils = new SSLUtils();
        assertNotNull(sslUtils);
    }

    @Test
    void testClass_isNotNull() {
        SSLUtils sslUtils = new SSLUtils();
        assertNotNull(sslUtils.getClass());
    }

    @Test
    void testClassName_isSSLUtils() {
        SSLUtils sslUtils = new SSLUtils();
        assertEquals("SSLUtils", sslUtils.getClass().getSimpleName());
    }
}
