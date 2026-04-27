package com.acme.modres.mbean.reservation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import com.acme.modres.Constants;

/**
 * Cloud-native date checker using stateless processing.
 * Timer-based scheduling replaced with Azure Service Bus scheduled messages.
 * Fixes blocker: cr-java-0111
 */
public class DateChecker implements Runnable {
  
  private static final Logger logger = Logger.getLogger(DateChecker.class.getName());
  
  ReservationCheckerData data;
  List<Reservation> reservations;

  public DateChecker(ReservationCheckerData data) {
    this.data = data;
    this.reservations = data.getReservationList().getReservations();
  }

  public void run() {
    try {
      for (int i = 0; i < reservations.size(); i++) {
        Reservation reservation = reservations.get(i);
        Date selectedDate = data.getSelectedDate();

        try {
          Date fromDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getFromDate());
          Date toDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getToDate());
          if (selectedDate.after(fromDate) && selectedDate.before(toDate)) {
            data.setAvailablility(false);
            logger.info("Date " + selectedDate + " is not available (reserved from " + fromDate + " to " + toDate + ")");
            return;
          }
        } catch (ParseException ex) {
          logger.severe("Error parsing reservation dates: " + ex.getMessage());
          ex.printStackTrace();
        }
      }
      data.setAvailablility(true);
      logger.info("Date " + data.getSelectedDate() + " is available");
    } catch (Exception e) {
      logger.severe("Error in DateChecker: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
