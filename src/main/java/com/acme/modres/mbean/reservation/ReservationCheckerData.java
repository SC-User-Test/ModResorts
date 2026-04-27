package com.acme.modres.mbean.reservation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import com.acme.modres.Constants;

/**
 * Cloud-native reservation checker with improved logging.
 * Fixes blocker: cr-java-0111
 */
public class ReservationCheckerData {
  
  private static final Logger logger = Logger.getLogger(ReservationCheckerData.class.getName());
  
  private ReservationList reservations;
  private Date selectedDate;
  private boolean available;

  public ReservationCheckerData(ReservationList reservations) {
    this.reservations = reservations;
    this.available = true;
  }

  public ReservationList getReservationList() {
    return reservations;
  }

  public Date getSelectedDate() {
    return selectedDate;
  }

  public boolean setSelectedDate(String dateStr) {
    try {
      selectedDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(dateStr);
      logger.info("Selected date set to: " + selectedDate);
      return true;
    } catch (Exception e) {
      logger.warning("Failed to parse date: " + dateStr + " - " + e.getMessage());
      return false;
    }
  }

  public boolean isAvailible() {
    return available;
  }

  public void setAvailablility(boolean available) {
    this.available = available;
    logger.info("Availability set to: " + available);
  }
}
