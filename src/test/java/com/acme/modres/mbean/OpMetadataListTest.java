package com.acme.modres.mbean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpMetadataListTest {

    private OpMetadataList opMetadataList;

    @BeforeEach
    void setUp() {
        opMetadataList = new OpMetadataList();
    }

    @Test
    void testDefaultConstructor_createsEmptyList() {
        OpMetadataList list = new OpMetadataList();
        assertNotNull(list);
        assertNotNull(list.getOpMetadatList());
        assertTrue(list.getOpMetadatList().isEmpty());
    }

    @Test
    void testAdd_singleElement() {
        OpMetadata metadata = new OpMetadata("op1", "desc1", "void", 1);
        opMetadataList.add(metadata);
        assertEquals(1, opMetadataList.getOpMetadatList().size());
    }

    @Test
    void testAdd_multipleElements() {
        opMetadataList.add(new OpMetadata("op1", "desc1", "void", 1));
        opMetadataList.add(new OpMetadata("op2", "desc2", "String", 2));
        opMetadataList.add(new OpMetadata("op3", "desc3", "int", 3));
        assertEquals(3, opMetadataList.getOpMetadatList().size());
    }

    @Test
    void testGetOpMetadatList_returnsCorrectList() {
        OpMetadata metadata = new OpMetadata("op1", "desc1", "void", 1);
        opMetadataList.add(metadata);
        List<OpMetadata> list = opMetadataList.getOpMetadatList();
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("op1", list.get(0).getName());
    }

    @Test
    void testSetOpMetadatList_replacesExistingList() {
        opMetadataList.add(new OpMetadata("old", "old desc", "void", 0));
        List<OpMetadata> newList = new ArrayList<>();
        newList.add(new OpMetadata("new1", "new desc1", "String", 1));
        newList.add(new OpMetadata("new2", "new desc2", "int", 2));
        opMetadataList.setOpMetadatList(newList);
        assertEquals(2, opMetadataList.getOpMetadatList().size());
        assertEquals("new1", opMetadataList.getOpMetadatList().get(0).getName());
    }

    @Test
    void testSetOpMetadatList_withEmptyList() {
        opMetadataList.add(new OpMetadata("op1", "desc1", "void", 1));
        opMetadataList.setOpMetadatList(new ArrayList<>());
        assertTrue(opMetadataList.getOpMetadatList().isEmpty());
    }

    @Test
    void testSetOpMetadatList_withNull() {
        opMetadataList.setOpMetadatList(null);
        assertNull(opMetadataList.getOpMetadatList());
    }

    @Test
    void testAdd_preservesOrder() {
        OpMetadata op1 = new OpMetadata("first", "desc1", "void", 1);
        OpMetadata op2 = new OpMetadata("second", "desc2", "void", 2);
        opMetadataList.add(op1);
        opMetadataList.add(op2);
        assertEquals("first", opMetadataList.getOpMetadatList().get(0).getName());
        assertEquals("second", opMetadataList.getOpMetadatList().get(1).getName());
    }
}
