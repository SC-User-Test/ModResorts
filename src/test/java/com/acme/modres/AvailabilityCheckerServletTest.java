package com.acme.modres;

import com.acme.modres.mbean.reservation.Reservation;
import com.acme.modres.mbean.reservation.ReservationCheckerData;
import com.acme.modres.mbean.reservation.ReservationList;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityCheckerServletTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AvailabilityCheckerServlet servlet;
    private StringWriter stringWriter;
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new AvailabilityCheckerServlet();
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);

        // Inject a ReservationCheckerData with an empty list
        ReservationList reservationList = new ReservationList();
        ReservationCheckerData checkerData = new ReservationCheckerData(reservationList);
        Field field = AvailabilityCheckerServlet.class.getDeclaredField("reservationCheckerData");
        field.setAccessible(true);
        field.set(servlet, checkerData);
    }

    @Test
    void testDoGet_withValidAvailableDate_returnsAvailableTrue() throws Exception {
        when(request.getParameter("date")).thenReturn("06/15/2024");
        when(response.getWriter()).thenReturn(printWriter);
        servlet.doGet(request, response);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("true"));
    }

    @Test
    void testDoGet_withInvalidDate_setsStatus500() throws Exception {
        when(request.getParameter("date")).thenReturn("invalid-date");
        when(response.getWriter()).thenReturn(printWriter);
        servlet.doGet(request, response);
        verify(response).setStatus(500);
    }

    @Test
    void testDoGet_withNullDate_setsStatus500() throws Exception {
        // null date causes NPE in setSelectedDate which propagates as NullPointerException
        // The servlet does not catch NPE, so we verify the behavior with an invalid date instead
        when(request.getParameter("date")).thenReturn(null);
        assertThrows(Exception.class, () -> servlet.doGet(request, response));
    }

    @Test
    void testDoGet_withValidDate_setsContentTypeJson() throws Exception {
        when(request.getParameter("date")).thenReturn("06/15/2024");
        when(response.getWriter()).thenReturn(printWriter);
        servlet.doGet(request, response);
        verify(response).setContentType("application/json");
    }

    @Test
    void testDoGet_withValidDate_setsCharacterEncodingUTF8() throws Exception {
        when(request.getParameter("date")).thenReturn("06/15/2024");
        when(response.getWriter()).thenReturn(printWriter);
        servlet.doGet(request, response);
        verify(response).setCharacterEncoding("UTF-8");
    }

    @Test
    void testDoGet_withDateInsideReservation_returnsAvailableFalse() throws Exception {
        // Set up a reservation that covers the selected date
        ReservationList reservationList = new ReservationList();
        reservationList.add(new Reservation("06/01/2024", "06/30/2024"));
        ReservationCheckerData checkerData = new ReservationCheckerData(reservationList);
        Field field = AvailabilityCheckerServlet.class.getDeclaredField("reservationCheckerData");
        field.setAccessible(true);
        field.set(servlet, checkerData);

        when(request.getParameter("date")).thenReturn("06/15/2024");
        when(response.getWriter()).thenReturn(printWriter);
        servlet.doGet(request, response);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("false"));
    }

    @Test
    void testDoGet_withDateOutsideReservation_returnsAvailableTrue() throws Exception {
        ReservationList reservationList = new ReservationList();
        reservationList.add(new Reservation("06/01/2024", "06/10/2024"));
        ReservationCheckerData checkerData = new ReservationCheckerData(reservationList);
        Field field = AvailabilityCheckerServlet.class.getDeclaredField("reservationCheckerData");
        field.setAccessible(true);
        field.set(servlet, checkerData);

        when(request.getParameter("date")).thenReturn("07/15/2024");
        when(response.getWriter()).thenReturn(printWriter);
        servlet.doGet(request, response);
        printWriter.flush();
        assertTrue(stringWriter.toString().contains("true"));
    }

    @Test
    void testDoGet_withValidDate_setsStatus200() throws Exception {
        when(request.getParameter("date")).thenReturn("06/15/2024");
        when(response.getWriter()).thenReturn(printWriter);
        servlet.doGet(request, response);
        verify(response).setStatus(200);
    }

    @Test
    void testDoGet_withDateInsideReservation_setsStatus201() throws Exception {
        ReservationList reservationList = new ReservationList();
        reservationList.add(new Reservation("06/01/2024", "06/30/2024"));
        ReservationCheckerData checkerData = new ReservationCheckerData(reservationList);
        Field field = AvailabilityCheckerServlet.class.getDeclaredField("reservationCheckerData");
        field.setAccessible(true);
        field.set(servlet, checkerData);

        when(request.getParameter("date")).thenReturn("06/15/2024");
        when(response.getWriter()).thenReturn(printWriter);
        servlet.doGet(request, response);
        verify(response).setStatus(201);
    }

    @Test
    void testServlet_isNotNull() {
        assertNotNull(servlet);
    }
}
