package com.billflow.service;

import com.billflow.config.GcsConfig;
import com.google.cloud.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

/**
 * Handles invoice PDF storage in Google Cloud Storage
 * and signed URL generation for secure access.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final Storage gcsStorage;
    private final StripeService stripeService;

    public InvoiceService(Storage gcsStorage, StripeService stripeService) {
        this.gcsStorage = gcsStorage;
        this.stripeService = stripeService;
    }

    /**
     * Downloads an invoice PDF from Stripe and uploads it to GCS.
     * Returns the GCS object path: {userId}/{invoiceId}.pdf
     *
     * The PDF is stored in GCS so it persists even if Stripe's
     * temporary download URL expires.
     */
    public String uploadInvoicePdf(Long userId, String stripeInvoiceId) {
        try {
            // Get the temporary PDF URL from Stripe
            String pdfUrl = stripeService.getInvoicePdfUrl(stripeInvoiceId);
            if (pdfUrl == null) {
                log.warn("No PDF URL available for invoice {}", stripeInvoiceId);
                return null;
            }

            // Download PDF bytes from Stripe
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pdfUrl))
                    .GET()
                    .build();
            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("Failed to download invoice PDF: HTTP {}",
                        response.statusCode());
                return null;
            }

            byte[] pdfBytes = response.body();

            // Upload to GCS: {userId}/{invoiceId}.pdf
            String objectPath = userId + "/" + stripeInvoiceId + ".pdf";
            BlobId blobId = BlobId.of(GcsConfig.BUCKET_NAME, objectPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("application/pdf")
                    .build();

            gcsStorage.create(blobInfo, pdfBytes);
            log.info("Uploaded invoice PDF to GCS: {}", objectPath);
            return objectPath;

        } catch (Exception e) {
            log.error("Failed to upload invoice PDF for user {} invoice {}",
                    userId, stripeInvoiceId, e);
            return null;
        }
    }

    /**
     * Generates a signed URL for a GCS object that expires in 15 minutes.
     * The user is redirected to this URL — the PDF is served directly
     * from GCS and never passes through our application server.
     */
    public URL generateSignedUrl(String objectPath) {
        BlobInfo blobInfo = BlobInfo.newBuilder(
                BlobId.of(GcsConfig.BUCKET_NAME, objectPath)).build();

        URL signedUrl = gcsStorage.signUrl(
                blobInfo,
                15,
                TimeUnit.MINUTES,
                Storage.SignUrlOption.withV4Signature()
        );

        log.info("Generated signed URL for: {} (expires in 15 min)", objectPath);
        return signedUrl;
    }
}