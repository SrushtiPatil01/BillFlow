package com.billflow.controller;

import com.billflow.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Receives Stripe webhook events.
 *
 * CRITICAL IMPLEMENTATION NOTE:
 * We read the raw request body bytes BEFORE any JSON parsing.
 * Stripe's HMAC-SHA256 signature is computed over the raw bytes.
 * If the body is parsed to JSON and re-serialized, the bytes change
 * and signature verification fails. This is a common, hard-to-debug bug.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(HttpServletRequest request)
            throws IOException {

        // CRITICAL: Read raw bytes BEFORE any parsing.
        // Using request.getReader() or @RequestBody would trigger
        // parsing and change the byte sequence.
        String rawBody = new String(
                request.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        String signatureHeader = request.getHeader("Stripe-Signature");
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Webhook request missing Stripe-Signature header");
            return ResponseEntity.badRequest().body("Missing Stripe-Signature header");
        }

        boolean isNewEvent = webhookService.handleWebhook(rawBody, signatureHeader);

        // Always return 200 to Stripe — even for duplicates.
        // Returning non-200 causes Stripe to retry delivery.
        if (isNewEvent) {
            return ResponseEntity.ok("Event received");
        } else {
            return ResponseEntity.ok("Duplicate event — already processed");
        }
    }
}