package com.acme.modres.mbean;

import org.junit.jupiter.api.Test;

import javax.management.MBeanOperationInfo;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DMBeanUtilsTest {

    @Test
    void testGetOps_withNullOpList_returnsNull() {
        MBeanOperationInfo[] result = DMBeanUtils.getOps(null);
        assertNull(result);
    }

    @Test
    void testGetOps_withNullInnerList_returnsNull() {
        OpMetadataList opList = new OpMetadataList();
        opList.setOpMetadatList(null);
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNull(result);
    }

    @Test
    void testGetOps_withEmptyList_returnsNull() {
        OpMetadataList opList = new OpMetadataList();
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNull(result);
    }

    @Test
    void testGetOps_withSingleOperation_returnsArrayOfOne() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("testOp", "A test operation", "void", MBeanOperationInfo.ACTION));
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNotNull(result);
        assertEquals(1, result.length);
    }

    @Test
    void testGetOps_withMultipleOperations_returnsCorrectCount() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("op1", "desc1", "void", MBeanOperationInfo.ACTION));
        opList.add(new OpMetadata("op2", "desc2", "java.lang.String", MBeanOperationInfo.INFO));
        opList.add(new OpMetadata("op3", "desc3", "int", MBeanOperationInfo.ACTION_INFO));
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNotNull(result);
        assertEquals(3, result.length);
    }

    @Test
    void testGetOps_operationNameIsCorrect() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("myOperation", "My operation description", "void", MBeanOperationInfo.ACTION));
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNotNull(result);
        assertEquals("myOperation", result[0].getName());
    }

    @Test
    void testGetOps_operationDescriptionIsCorrect() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("op", "My description", "void", MBeanOperationInfo.ACTION));
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNotNull(result);
        assertEquals("My description", result[0].getDescription());
    }

    @Test
    void testGetOps_operationReturnTypeIsCorrect() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("op", "desc", "java.lang.String", MBeanOperationInfo.ACTION));
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNotNull(result);
        assertEquals("java.lang.String", result[0].getReturnType());
    }

    @Test
    void testGetOps_operationImpactIsCorrect() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("op", "desc", "void", MBeanOperationInfo.INFO));
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNotNull(result);
        assertEquals(MBeanOperationInfo.INFO, result[0].getImpact());
    }

    @Test
    void testGetOps_withTwoOps_preservesOrder() {
        OpMetadataList opList = new OpMetadataList();
        opList.add(new OpMetadata("firstOp", "first desc", "void", MBeanOperationInfo.ACTION));
        opList.add(new OpMetadata("secondOp", "second desc", "String", MBeanOperationInfo.INFO));
        MBeanOperationInfo[] result = DMBeanUtils.getOps(opList);
        assertNotNull(result);
        assertEquals("firstOp", result[0].getName());
        assertEquals("secondOp", result[1].getName());
    }
}
