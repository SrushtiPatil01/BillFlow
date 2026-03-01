package com.billflow.controller;

import com.billflow.service.InvoiceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * GET /api/invoices/{userId}/{invoiceId}
     * Generates a signed GCS URL for the invoice PDF and redirects to it.
     * The PDF is served directly from GCS — never passes through our server.
     */
    @GetMapping("/{userId}/{invoiceId}")
    public ResponseEntity<Void> getInvoice(@PathVariable Long userId,
                                            @PathVariable String invoiceId) {
        String objectPath = userId + "/" + invoiceId + ".pdf";
        URL signedUrl = invoiceService.generateSignedUrl(objectPath);

        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .header(HttpHeaders.LOCATION, signedUrl.toString())
                .build();
    }
}