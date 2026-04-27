package com.acme.modres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

@SpringBootApplication
@ServletComponentScan
@EnableRedisHttpSession
public class ModResortsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModResortsApplication.class, args);
    }

    @Bean
    public BlobServiceClient blobServiceClient() {
        String connectionString = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
        if (connectionString != null && !connectionString.isEmpty()) {
            return new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
        } else {
            // Use Managed Identity for authentication
            String accountUrl = System.getenv("AZURE_STORAGE_ACCOUNT_URL");
            if (accountUrl == null || accountUrl.isEmpty()) {
                accountUrl = "https://modresortsstorage.blob.core.windows.net";
            }
            return new BlobServiceClientBuilder()
                    .endpoint(accountUrl)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
        }
    }
}
