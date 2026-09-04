package com.acme.modres.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTest {

    private Service service;

    @BeforeEach
    void setUp() {
        service = new Service();
    }

    @Test
    void testOperationConstant_hasCorrectValue() {
        assertEquals("my-operation", Service.OPERATION);
    }

    @Test
    void testOperation_doesNotThrow() {
        assertDoesNotThrow(() -> service.operation());
    }

    @Test
    void testConstructor_createsInstance() {
        Service s = new Service();
        assertNotNull(s);
    }

    @Test
    void testOperation_canBeCalledMultipleTimes() {
        assertDoesNotThrow(() -> {
            service.operation();
            service.operation();
            service.operation();
        });
    }

    @Test
    void testOperationConstant_isNotNull() {
        assertNotNull(Service.OPERATION);
    }

    @Test
    void testOperationConstant_isNotEmpty() {
        assertFalse(Service.OPERATION.isEmpty());
    }
}
