package com.acme.modres.mbean.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationListTest {

    private ReservationList reservationList;

    @BeforeEach
    void setUp() {
        reservationList = new ReservationList();
    }

    @Test
    void testDefaultConstructor_createsEmptyList() {
        ReservationList list = new ReservationList();
        assertNotNull(list);
        assertNotNull(list.getReservations());
        assertTrue(list.getReservations().isEmpty());
    }

    @Test
    void testParameterizedConstructor_withList() {
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("01/01/2024", "01/15/2024"));
        ReservationList list = new ReservationList(reservations);
        assertNotNull(list);
        assertEquals(1, list.getReservations().size());
    }

    @Test
    void testAdd_singleReservation() {
        Reservation r = new Reservation("01/01/2024", "01/15/2024");
        reservationList.add(r);
        assertEquals(1, reservationList.getReservations().size());
    }

    @Test
    void testAdd_multipleReservations() {
        reservationList.add(new Reservation("01/01/2024", "01/15/2024"));
        reservationList.add(new Reservation("02/01/2024", "02/15/2024"));
        reservationList.add(new Reservation("03/01/2024", "03/15/2024"));
        assertEquals(3, reservationList.getReservations().size());
    }

    @Test
    void testGetReservations_returnsCorrectReservation() {
        Reservation r = new Reservation("05/01/2024", "05/10/2024");
        reservationList.add(r);
        List<Reservation> result = reservationList.getReservations();
        assertEquals("05/01/2024", result.get(0).getFromDate());
        assertEquals("05/10/2024", result.get(0).getToDate());
    }

    @Test
    void testParameterizedConstructor_withEmptyList() {
        ReservationList list = new ReservationList(new ArrayList<>());
        assertNotNull(list.getReservations());
        assertTrue(list.getReservations().isEmpty());
    }

    @Test
    void testAdd_preservesOrder() {
        Reservation r1 = new Reservation("01/01/2024", "01/10/2024");
        Reservation r2 = new Reservation("02/01/2024", "02/10/2024");
        reservationList.add(r1);
        reservationList.add(r2);
        assertEquals("01/01/2024", reservationList.getReservations().get(0).getFromDate());
        assertEquals("02/01/2024", reservationList.getReservations().get(1).getFromDate());
    }

    @Test
    void testParameterizedConstructor_withMultipleReservations() {
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("01/01/2024", "01/15/2024"));
        reservations.add(new Reservation("02/01/2024", "02/15/2024"));
        ReservationList list = new ReservationList(reservations);
        assertEquals(2, list.getReservations().size());
    }
}
