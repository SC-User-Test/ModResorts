package com.acme.modres.mbean.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ReservationCheckerDataTest {

    private ReservationList reservationList;
    private ReservationCheckerData checkerData;

    @BeforeEach
    void setUp() {
        reservationList = new ReservationList();
        checkerData = new ReservationCheckerData(reservationList);
    }

    @Test
    void testConstructor_setsReservationList() {
        assertNotNull(checkerData.getReservationList());
        assertEquals(reservationList, checkerData.getReservationList());
    }

    @Test
    void testConstructor_defaultAvailabilityIsTrue() {
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testSetSelectedDate_withValidDate_returnsTrue() {
        boolean result = checkerData.setSelectedDate("01/15/2024");
        assertTrue(result);
    }

    @Test
    void testSetSelectedDate_withInvalidDate_returnsFalse() {
        boolean result = checkerData.setSelectedDate("not-a-date");
        assertFalse(result);
    }

    @Test
    void testSetSelectedDate_withEmptyString_returnsFalse() {
        boolean result = checkerData.setSelectedDate("");
        assertFalse(result);
    }

    @Test
    void testSetSelectedDate_setsLocalDate() {
        checkerData.setSelectedDate("06/15/2024");
        assertNotNull(checkerData.getSelectedLocalDate());
    }

    @Test
    void testSetSelectedDate_setsLegacyDate() {
        checkerData.setSelectedDate("06/15/2024");
        assertNotNull(checkerData.getSelectedDate());
    }

    @Test
    void testSetAvailability_toFalse() {
        checkerData.setAvailablility(false);
        assertFalse(checkerData.isAvailible());
    }

    @Test
    void testSetAvailability_toTrue() {
        checkerData.setAvailablility(false);
        checkerData.setAvailablility(true);
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testGetReservationList_returnsCorrectList() {
        ReservationList list = new ReservationList();
        list.add(new Reservation("01/01/2024", "01/10/2024"));
        ReservationCheckerData data = new ReservationCheckerData(list);
        assertEquals(1, data.getReservationList().getReservations().size());
    }

    @Test
    void testSetSelectedDate_withWrongFormat_returnsFalse() {
        boolean result = checkerData.setSelectedDate("2024-01-15");
        assertFalse(result);
    }

    @Test
    void testSetSelectedDate_withValidDate_localDateHasCorrectMonth() {
        checkerData.setSelectedDate("06/15/2024");
        assertNotNull(checkerData.getSelectedLocalDate());
        assertEquals(6, checkerData.getSelectedLocalDate().getMonthValue());
        assertEquals(15, checkerData.getSelectedLocalDate().getDayOfMonth());
        assertEquals(2024, checkerData.getSelectedLocalDate().getYear());
    }

    @Test
    void testGetSelectedDate_beforeSettingDate_isNull() {
        assertNull(checkerData.getSelectedDate());
    }

    @Test
    void testGetSelectedLocalDate_beforeSettingDate_isNull() {
        assertNull(checkerData.getSelectedLocalDate());
    }
}
