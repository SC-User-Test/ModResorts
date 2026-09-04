package com.acme.modres.mbean.reservation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.text.SimpleDateFormat;

import com.acme.modres.Constants;

public class ReservationCheckerData {
  private ReservationList reservations;
  private LocalDate selectedLocalDate;
  private Date selectedDate; // kept for backward compatibility
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

  public LocalDate getSelectedLocalDate() {
    return selectedLocalDate;
  }

  public boolean setSelectedDate(String dateStr) {
    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.DATA_FORMAT);
      selectedLocalDate = LocalDate.parse(dateStr, formatter);
      // Also set legacy Date for backward compatibility
      selectedDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(dateStr);
    } catch (DateTimeParseException | java.text.ParseException e) {
      return false;
    }
    return true;
  }

  public boolean isAvailible() {
    return available;
  }

  public void setAvailablility(boolean available) {
    this.available = available;
  }
}
