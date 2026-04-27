package com.acme.modres.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Azure Blob Storage Service for cloud-native file operations.
 * Replaces local file system dependencies with Azure Blob Storage.
 */
@Service
public class AzureBlobStorageService {

    private static final Logger logger = Logger.getLogger(AzureBlobStorageService.class.getName());

    @Autowired
    private BlobServiceClient blobServiceClient;

    @Value("${azure.storage.container-name}")
    private String containerName;

    /**
     * Upload data to Azure Blob Storage
     */
    public void uploadBlob(String blobName, byte[] data) throws IOException {
        try {
            BlobContainerClient containerClient = getOrCreateContainer();
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                blobClient.upload(inputStream, data.length, true);
                logger.info("Successfully uploaded blob: " + blobName);
            }
        } catch (Exception e) {
            logger.severe("Failed to upload blob: " + blobName + " - " + e.getMessage());
            throw new IOException("Failed to upload to Azure Blob Storage", e);
        }
    }

    /**
     * Download data from Azure Blob Storage
     */
    public byte[] downloadBlob(String blobName) throws IOException {
        try {
            BlobContainerClient containerClient = getOrCreateContainer();
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            if (!blobClient.exists()) {
                logger.warning("Blob does not exist: " + blobName);
                return null;
            }
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.download(outputStream);
            logger.info("Successfully downloaded blob: " + blobName);
            return outputStream.toByteArray();
        } catch (Exception e) {
            logger.severe("Failed to download blob: " + blobName + " - " + e.getMessage());
            throw new IOException("Failed to download from Azure Blob Storage", e);
        }
    }

    /**
     * Get InputStream from Azure Blob Storage
     */
    public InputStream getBlobInputStream(String blobName) throws IOException {
        byte[] data = downloadBlob(blobName);
        if (data == null) {
            return null;
        }
        return new ByteArrayInputStream(data);
    }

    /**
     * Check if blob exists
     */
    public boolean blobExists(String blobName) {
        try {
            BlobContainerClient containerClient = getOrCreateContainer();
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            return blobClient.exists();
        } catch (Exception e) {
            logger.severe("Failed to check blob existence: " + blobName + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete blob from Azure Blob Storage
     */
    public void deleteBlob(String blobName) throws IOException {
        try {
            BlobContainerClient containerClient = getOrCreateContainer();
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            if (blobClient.exists()) {
                blobClient.delete();
                logger.info("Successfully deleted blob: " + blobName);
            }
        } catch (Exception e) {
            logger.severe("Failed to delete blob: " + blobName + " - " + e.getMessage());
            throw new IOException("Failed to delete from Azure Blob Storage", e);
        }
    }

    /**
     * Get or create container
     */
    private BlobContainerClient getOrCreateContainer() {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
            logger.info("Created container: " + containerName);
        }
        return containerClient;
    }
}
