package com.acme.modres.mbean.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DateCheckerTest {

    private ReservationList reservationList;
    private ReservationCheckerData checkerData;

    @BeforeEach
    void setUp() {
        reservationList = new ReservationList();
        checkerData = new ReservationCheckerData(reservationList);
    }

    @Test
    void testRun_withNoReservations_remainsAvailable() {
        checkerData.setSelectedDate("06/15/2024");
        DateChecker checker = new DateChecker(checkerData);
        checker.run();
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testRun_withDateOutsideReservation_remainsAvailable() {
        reservationList.add(new Reservation("06/01/2024", "06/10/2024"));
        checkerData.setSelectedDate("06/20/2024");
        DateChecker checker = new DateChecker(checkerData);
        checker.run();
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testRun_withDateInsideReservation_setsUnavailable() {
        reservationList.add(new Reservation("06/01/2024", "06/30/2024"));
        checkerData.setSelectedDate("06/15/2024");
        DateChecker checker = new DateChecker(checkerData);
        checker.run();
        assertFalse(checkerData.isAvailible());
    }

    @Test
    void testRun_withDateOnFromDate_remainsAvailable() {
        // isAfter(fromDate) is strict, so on fromDate it should still be available
        reservationList.add(new Reservation("06/15/2024", "06/30/2024"));
        checkerData.setSelectedDate("06/15/2024");
        DateChecker checker = new DateChecker(checkerData);
        checker.run();
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testRun_withDateOnToDate_remainsAvailable() {
        // isBefore(toDate) is strict, so on toDate it should still be available
        reservationList.add(new Reservation("06/01/2024", "06/15/2024"));
        checkerData.setSelectedDate("06/15/2024");
        DateChecker checker = new DateChecker(checkerData);
        checker.run();
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testRun_withMultipleReservations_dateInSecond_setsUnavailable() {
        reservationList.add(new Reservation("01/01/2024", "01/10/2024"));
        reservationList.add(new Reservation("06/01/2024", "06/30/2024"));
        checkerData.setSelectedDate("06/15/2024");
        DateChecker checker = new DateChecker(checkerData);
        checker.run();
        assertFalse(checkerData.isAvailible());
    }

    @Test
    void testRun_withMultipleReservations_dateInNone_remainsAvailable() {
        reservationList.add(new Reservation("01/01/2024", "01/10/2024"));
        reservationList.add(new Reservation("06/01/2024", "06/30/2024"));
        checkerData.setSelectedDate("03/15/2024");
        DateChecker checker = new DateChecker(checkerData);
        checker.run();
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testRun_withInvalidReservationDates_doesNotThrow() {
        reservationList.add(new Reservation("invalid-date", "also-invalid"));
        checkerData.setSelectedDate("06/15/2024");
        DateChecker checker = new DateChecker(checkerData);
        assertDoesNotThrow(() -> checker.run());
    }

    @Test
    void testConstructor_setsDataAndReservations() {
        reservationList.add(new Reservation("01/01/2024", "01/10/2024"));
        DateChecker checker = new DateChecker(checkerData);
        assertNotNull(checker);
    }

    @Test
    void testRun_withDateBeforeAllReservations_remainsAvailable() {
        reservationList.add(new Reservation("06/01/2024", "06/30/2024"));
        checkerData.setSelectedDate("01/01/2024");
        DateChecker checker = new DateChecker(checkerData);
        checker.run();
        assertTrue(checkerData.isAvailible());
    }
}
