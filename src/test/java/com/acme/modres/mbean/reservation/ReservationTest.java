package com.acme.modres.mbean.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservation = new Reservation();
    }

    @Test
    void testDefaultConstructor_createsInstance() {
        Reservation r = new Reservation();
        assertNotNull(r);
    }

    @Test
    void testParameterizedConstructor_setsFromAndToDate() {
        Reservation r = new Reservation("01/01/2024", "01/15/2024");
        assertEquals("01/01/2024", r.getFromDate());
        assertEquals("01/15/2024", r.getToDate());
    }

    @Test
    void testSetFromDate_andGetFromDate() {
        reservation.setFromDate("03/10/2024");
        assertEquals("03/10/2024", reservation.getFromDate());
    }

    @Test
    void testSetToDate_andGetToDate() {
        reservation.setToDate("03/20/2024");
        assertEquals("03/20/2024", reservation.getToDate());
    }

    @Test
    void testSetFromDate_withNull() {
        reservation.setFromDate(null);
        assertNull(reservation.getFromDate());
    }

    @Test
    void testSetToDate_withNull() {
        reservation.setToDate(null);
        assertNull(reservation.getToDate());
    }

    @Test
    void testGetFromDate_defaultIsNull() {
        assertNull(reservation.getFromDate());
    }

    @Test
    void testGetToDate_defaultIsNull() {
        assertNull(reservation.getToDate());
    }

    @Test
    void testParameterizedConstructor_withNullValues() {
        Reservation r = new Reservation(null, null);
        assertNull(r.getFromDate());
        assertNull(r.getToDate());
    }

    @Test
    void testSetFromDate_overwritesPreviousValue() {
        reservation.setFromDate("01/01/2024");
        reservation.setFromDate("02/01/2024");
        assertEquals("02/01/2024", reservation.getFromDate());
    }

    @Test
    void testSetToDate_overwritesPreviousValue() {
        reservation.setToDate("01/31/2024");
        reservation.setToDate("02/28/2024");
        assertEquals("02/28/2024", reservation.getToDate());
    }
}
