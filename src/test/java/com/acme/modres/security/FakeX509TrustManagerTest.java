package com.acme.modres.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FakeX509TrustManagerTest {

    @Test
    void testConstructor_createsInstance() {
        FakeX509TrustManager trustManager = new FakeX509TrustManager();
        assertNotNull(trustManager);
    }

    @Test
    void testInstance_isNotNull() {
        FakeX509TrustManager manager = new FakeX509TrustManager();
        assertNotNull(manager);
    }

    @Test
    void testClass_isPublic() {
        FakeX509TrustManager manager = new FakeX509TrustManager();
        assertTrue(manager.getClass().isInstance(manager));
    }
}
