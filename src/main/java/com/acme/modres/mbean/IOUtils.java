package com.acme.modres.mbean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.acme.modres.mbean.reservation.ReservationList;
import com.acme.modres.service.AzureBlobStorageService;
import com.acme.modres.util.JsonInputStream;

/**
 * Cloud-native IOUtils using Azure Blob Storage instead of local file system.
 * Fixes blockers: cr-java-0062, cr-java-0112
 */
@Component
public final class IOUtils {

  private static final Logger logger = Logger.getLogger(IOUtils.class.getName());
  
  private static AzureBlobStorageService blobStorageService;

  @Autowired
  public void setBlobStorageService(AzureBlobStorageService service) {
    IOUtils.blobStorageService = service;
  }

  /**
   * Get InputStream from Azure Blob Storage or classpath resource.
   * Replaces local file system operations with cloud-native storage.
   */
  public static InputStream getInputStreamFromResource(String path) throws IOException {
    InputStream stream = null;
    
    // First try Azure Blob Storage if service is available
    if (blobStorageService != null) {
      try {
        stream = blobStorageService.getBlobInputStream(path);
        if (stream != null) {
          logger.info("Loaded resource from Azure Blob Storage: " + path);
          return stream;
        }
      } catch (Exception e) {
        logger.warning("Failed to load from Azure Blob Storage, falling back to classpath: " + e.getMessage());
      }
    }
    
    // Fallback to classpath resource
    stream = IOUtils.class.getClassLoader().getResourceAsStream(path);
    if (stream != null) {
      logger.info("Loaded resource from classpath: " + path);
      return stream;
    }
    
    throw new IOException("Resource not found: " + path);
  }

  /**
   * Write data to Azure Blob Storage.
   * Replaces local file system write operations.
   */
  public static void writeToStorage(String path, byte[] data) throws IOException {
    if (blobStorageService == null) {
      throw new IOException("Azure Blob Storage service not available");
    }
    
    blobStorageService.uploadBlob(path, data);
    logger.info("Wrote data to Azure Blob Storage: " + path);
  }

  public static OpMetadataList getOpListFromConfig() {
    try (InputStream stream = getInputStreamFromResource("ops.json")) {
      if (stream == null) {
        logger.warning("ops.json not found, returning empty list");
        return new OpMetadataList();
      }
      
      // Read stream into byte array for JsonInputStream
      byte[] buffer = new byte[stream.available()];
      stream.read(buffer);
      
      try (JsonInputStream jsonStream = new JsonInputStream(new ByteArrayInputStream(buffer))) {
        OpMetadataList opList = (OpMetadataList) jsonStream.parseJsonAs(OpMetadataList.class);
        return opList != null ? opList : new OpMetadataList();
      }
    } catch (IOException e) {
      logger.severe("Error loading ops.json: " + e.getMessage());
      e.printStackTrace();
      return new OpMetadataList();
    }
  }

  public static ReservationList getReservationListFromConfig() {
    try (InputStream stream = getInputStreamFromResource("reservations.json")) {
      if (stream == null) {
        logger.warning("reservations.json not found, returning empty list");
        return new ReservationList();
      }
      
      // Read stream into byte array for JsonInputStream
      byte[] buffer = new byte[stream.available()];
      stream.read(buffer);
      
      try (JsonInputStream jsonStream = new JsonInputStream(new ByteArrayInputStream(buffer))) {
        ReservationList reservationList = (ReservationList) jsonStream.parseJsonAs(ReservationList.class);
        return reservationList != null ? reservationList : new ReservationList();
      }
    } catch (IOException e) {
      logger.severe("Error loading reservations.json: " + e.getMessage());
      e.printStackTrace();
      return new ReservationList();
    }
  }
}
