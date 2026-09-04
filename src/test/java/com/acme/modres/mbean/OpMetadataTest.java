package com.acme.modres.mbean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpMetadataTest {

    private OpMetadata opMetadata;

    @BeforeEach
    void setUp() {
        opMetadata = new OpMetadata();
    }

    @Test
    void testDefaultConstructor_createsInstance() {
        OpMetadata metadata = new OpMetadata();
        assertNotNull(metadata);
    }

    @Test
    void testParameterizedConstructor_setsAllFields() {
        OpMetadata metadata = new OpMetadata("testOp", "A test operation", "void", 1);
        assertEquals("testOp", metadata.getName());
        assertEquals("A test operation", metadata.getDescription());
        assertEquals("void", metadata.getType());
        assertEquals(1, metadata.getImpact());
    }

    @Test
    void testSetName_andGetName() {
        opMetadata.setName("myOperation");
        assertEquals("myOperation", opMetadata.getName());
    }

    @Test
    void testSetDescription_andGetDescription() {
        opMetadata.setDescription("My description");
        assertEquals("My description", opMetadata.getDescription());
    }

    @Test
    void testSetType_andGetType() {
        opMetadata.setType("java.lang.String");
        assertEquals("java.lang.String", opMetadata.getType());
    }

    @Test
    void testSetImpact_andGetImpact() {
        opMetadata.setImpact(2);
        assertEquals(2, opMetadata.getImpact());
    }

    @Test
    void testSetName_withNull() {
        opMetadata.setName(null);
        assertNull(opMetadata.getName());
    }

    @Test
    void testSetDescription_withNull() {
        opMetadata.setDescription(null);
        assertNull(opMetadata.getDescription());
    }

    @Test
    void testSetType_withNull() {
        opMetadata.setType(null);
        assertNull(opMetadata.getType());
    }

    @Test
    void testSetImpact_withZero() {
        opMetadata.setImpact(0);
        assertEquals(0, opMetadata.getImpact());
    }

    @Test
    void testSetImpact_withNegativeValue() {
        opMetadata.setImpact(-1);
        assertEquals(-1, opMetadata.getImpact());
    }

    @Test
    void testParameterizedConstructor_withNullValues() {
        OpMetadata metadata = new OpMetadata(null, null, null, 0);
        assertNull(metadata.getName());
        assertNull(metadata.getDescription());
        assertNull(metadata.getType());
        assertEquals(0, metadata.getImpact());
    }

    @Test
    void testGetName_defaultIsNull() {
        assertNull(opMetadata.getName());
    }

    @Test
    void testGetDescription_defaultIsNull() {
        assertNull(opMetadata.getDescription());
    }

    @Test
    void testGetType_defaultIsNull() {
        assertNull(opMetadata.getType());
    }

    @Test
    void testGetImpact_defaultIsZero() {
        assertEquals(0, opMetadata.getImpact());
    }
}
