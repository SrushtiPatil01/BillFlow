package com.billflow.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcsConfig {

    public static final String BUCKET_NAME =
            System.getenv("GCS_BUCKET_NAME") != null
                    ? System.getenv("GCS_BUCKET_NAME")
                    : "billflow-invoices";

    @Bean
    public Storage gcsStorage() {
        String projectId = System.getenv("GCP_PROJECT_ID");
        if (projectId == null || projectId.isBlank()) {
            projectId = "billflow-local";
        }
        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .build()
                .getService();
    }
}