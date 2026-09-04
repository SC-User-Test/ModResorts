package com.acme.modres.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomPermissionTest {

    @Test
    void testConstructorWithName_createsInstance() {
        CustomPermission permission = new CustomPermission("test.permission");
        assertNotNull(permission);
    }

    @Test
    void testConstructorWithNameAndActions_createsInstance() {
        CustomPermission permission = new CustomPermission("test.permission", "read");
        assertNotNull(permission);
    }

    @Test
    void testConstructorWithName_getName() {
        CustomPermission permission = new CustomPermission("my.permission");
        assertEquals("my.permission", permission.getName());
    }

    @Test
    void testConstructorWithNameAndActions_getName() {
        CustomPermission permission = new CustomPermission("my.permission", "write");
        assertEquals("my.permission", permission.getName());
    }

    @Test
    void testConstructorWithNameAndActions_getActions() {
        CustomPermission permission = new CustomPermission("my.permission", "read");
        // BasicPermission ignores actions, returns empty string
        assertNotNull(permission.getActions());
    }

    @Test
    void testConstructorWithName_impliesItself() {
        CustomPermission permission = new CustomPermission("test.permission");
        assertTrue(permission.implies(permission));
    }

    @Test
    void testConstructorWithName_doesNotImplyDifferentPermission() {
        CustomPermission p1 = new CustomPermission("test.permission");
        CustomPermission p2 = new CustomPermission("other.permission");
        assertFalse(p1.implies(p2));
    }

    @Test
    void testConstructorWithWildcard_impliesSpecific() {
        CustomPermission wildcard = new CustomPermission("test.*");
        CustomPermission specific = new CustomPermission("test.read");
        assertTrue(wildcard.implies(specific));
    }

    @Test
    void testEquals_samePermission_returnsTrue() {
        CustomPermission p1 = new CustomPermission("test.permission");
        CustomPermission p2 = new CustomPermission("test.permission");
        assertEquals(p1, p2);
    }

    @Test
    void testEquals_differentPermission_returnsFalse() {
        CustomPermission p1 = new CustomPermission("test.permission1");
        CustomPermission p2 = new CustomPermission("test.permission2");
        assertNotEquals(p1, p2);
    }

    @Test
    void testHashCode_samePermissions_sameHashCode() {
        CustomPermission p1 = new CustomPermission("test.permission");
        CustomPermission p2 = new CustomPermission("test.permission");
        assertEquals(p1.hashCode(), p2.hashCode());
    }
}
