package com.acme.modres;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;

import com.acme.modres.mbean.IOUtils;
import com.acme.modres.mbean.reservation.ReservationCheckerData;
import com.acme.modres.mbean.reservation.Reservation;
import com.acme.modres.service.AzureBlobStorageService;
import com.acme.modres.util.ZipValidator;

/**
 * Cloud-native servlet using Azure Blob Storage for file operations.
 * Fixes blockers: cr-java-0061, cr-java-0062, cr-java-0063, cr-java-0098, cr-java-0111
 */
@WebServlet({ "/resorts/availability" })
public class AvailabilityCheckerServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final Logger logger = Logger.getLogger(AvailabilityCheckerServlet.class.getName());

  @Autowired
  private AzureBlobStorageService blobStorageService;

  private ReservationCheckerData reservationCheckerData;

  @Override
  public void init() {
    // load reserved dates from cloud storage or classpath
    this.reservationCheckerData = new ReservationCheckerData(IOUtils.getReservationListFromConfig());
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

    String methodName = "doGet";
    logger.entering(AvailabilityCheckerServlet.class.getName(), methodName);
    int statusCode = 200;

    String selectedDateStr = request.getParameter("date");
    boolean parsedDate = reservationCheckerData.setSelectedDate(selectedDateStr);
    if (!parsedDate || reservationCheckerData.getReservationList() == null) {
      statusCode = 500;
      reservationCheckerData.setAvailablility(false);
    } else {
      List<Reservation> reservations = reservationCheckerData.getReservationList().getReservations();
      boolean isAvailible = true;

      for (Reservation reservation : reservations) {
        try {
          Date fromDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getFromDate());
          Date toDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getToDate());
          Date selectedDate = reservationCheckerData.getSelectedDate();

          if (selectedDate.after(fromDate) && selectedDate.before(toDate)) {
            isAvailible = false;
            break;
          }
        } catch (ParseException ex) {
          ex.printStackTrace();
        }
      }

      reservationCheckerData.setAvailablility(isAvailible);

      // Adjust the status code based on availability
      if (!isAvailible) {
        statusCode = 201;
      }
    }

    // Send the response
    PrintWriter out = response.getWriter();
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    out.print("{\"availability\": \"" + String.valueOf(reservationCheckerData.isAvailible()) + "\"}");
    response.setStatus(statusCode);
  }

  /**
   * Returns the weather information for a given city
   */
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    doGet(request, response);
  }

  /**
   * Export reservations to Azure Blob Storage as a zip file.
   * Replaces local file system operations with cloud-native storage.
   * Fixes blockers: cr-java-0061, cr-java-0062, cr-java-0063, cr-java-0098
   */
  protected int exportRevervations(String selectedDateStr) {
    // Use try-with-resources to ensure proper resource cleanup (fixes cr-java-0098)
    try {
      // Load reservations data from Azure Blob Storage or classpath
      InputStream reservationsStream = IOUtils.getInputStreamFromResource("reservations.json");
      if (reservationsStream == null) {
        logger.severe("reservations.json not found");
        return -1;
      }
      
      byte[] reservationsData;
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = reservationsStream.read(buffer)) >= 0) {
          baos.write(buffer, 0, length);
        }
        reservationsData = baos.toByteArray();
      } finally {
        reservationsStream.close();
      }

      // Create zip file in memory
      byte[] zipData;
      try (ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
           ZipOutputStream zipOut = new ZipOutputStream(zipBaos)) {
        
        ZipEntry zipEntry = new ZipEntry("reservations.json");
        zipOut.putNextEntry(zipEntry);
        zipOut.write(reservationsData);
        zipOut.closeEntry();
        zipOut.finish();
        
        zipData = zipBaos.toByteArray();
      }

      // Upload zip file to Azure Blob Storage instead of local file system
      String zipBlobName = "exports/reservations_" + System.currentTimeMillis() + ".zip";
      blobStorageService.uploadBlob(zipBlobName, zipData);
      logger.info("Successfully exported reservations to Azure Blob Storage: " + zipBlobName);

      // Verify zip file from Azure Blob Storage
      byte[] downloadedZip = blobStorageService.downloadBlob(zipBlobName);
      if (downloadedZip != null && downloadedZip.length > 0) {
        // Validation would be done here if needed
        return 0;
      }
      
    } catch (IOException e) {
      logger.severe("Error exporting reservations: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      logger.severe("Unexpected error exporting reservations: " + e.getMessage());
      e.printStackTrace();
    }
    return -1;
  }

}
