package com.acme.modres.mbean.reservation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.logging.Logger;

import com.acme.modres.Constants;

public class ReservationCheckerData {
  private static final Logger logger = Logger.getLogger(ReservationCheckerData.class.getName());
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(Constants.DATA_FORMAT);
  
  private ReservationList reservations;
  private LocalDate selectedDate;
  private boolean available;

  public ReservationCheckerData(ReservationList reservations) {
    this.reservations = reservations;
    this.available = true;
  }

  public ReservationList getReservationList() {
    return reservations;
  }

  public LocalDate getSelectedLocalDate() {
    return selectedDate;
  }

  // Deprecated: Use getSelectedLocalDate() instead
  @Deprecated
  public Date getSelectedDate() {
    if (selectedDate == null) {
      return null;
    }
    return java.sql.Date.valueOf(selectedDate);
  }

  public boolean setSelectedDate(String dateStr) {
    try {
      selectedDate = LocalDate.parse(dateStr, DATE_FORMATTER);
      return true;
    } catch (DateTimeParseException e) {
      logger.warning("Failed to parse date: " + dateStr + " - " + e.getMessage());
      return false;
    }
  }

  public boolean isAvailible() {
    return available;
  }

  public void setAvailablility(boolean available) {
    this.available = available;
  }
}
