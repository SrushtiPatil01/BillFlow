package com.billflow.service;

import com.billflow.exception.WebhookVerificationException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.InvoicePayParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Encapsulates all Stripe API interactions.
 * Keeps Stripe SDK details out of controllers and other services.
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final String webhookSecret;

    public StripeService() {
        this.webhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET");
    }

    @PostConstruct
    public void init() {
        String apiKey = System.getenv("STRIPE_SECRET_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("STRIPE_SECRET_KEY not set — Stripe calls will fail");
        }
        Stripe.apiKey = apiKey;
    }

    // -------------------------------------------------------
    // Checkout Session
    // -------------------------------------------------------

    /**
     * Creates a Stripe Checkout Session for subscription billing.
     * Returns the session which contains the checkout URL.
     */
    public Session createCheckoutSession(String customerEmail,
                                          String stripePriceId,
                                          String successUrl,
                                          String cancelUrl,
                                          Long subscriptionId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(customerEmail)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(stripePriceId)
                        .setQuantity(1L)
                        .build()
                )
                .putMetadata("billflow_subscription_id", subscriptionId.toString())
                .build();

        Session session = Session.create(params);
        log.info("Created Stripe Checkout Session: {}", session.getId());
        return session;
    }

    // -------------------------------------------------------
    // Webhook signature verification
    // -------------------------------------------------------

    /**
     * Verifies the Stripe webhook signature using HMAC-SHA256.
     *
     * CRITICAL: The rawBody must be the original request bytes BEFORE
     * any JSON parsing. Parsing the body first changes the byte sequence
     * and causes signature verification to fail. This is a common bug.
     */
    public Event verifyAndParseWebhook(String rawBody, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new WebhookVerificationException(
                    "STRIPE_WEBHOOK_SECRET not configured");
        }
        try {
            return Webhook.constructEvent(rawBody, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new WebhookVerificationException(
                    "Invalid Stripe webhook signature", e);
        }
    }

    // -------------------------------------------------------
    // Subscription cancellation
    // -------------------------------------------------------

    public Subscription cancelSubscription(String stripeSubscriptionId)
            throws StripeException {
        Subscription subscription =
                Subscription.retrieve(stripeSubscriptionId);
        SubscriptionCancelParams params =
                SubscriptionCancelParams.builder().build();
        Subscription cancelled = subscription.cancel(params);
        log.info("Cancelled Stripe subscription: {}", stripeSubscriptionId);
        return cancelled;
    }

    // -------------------------------------------------------
    // Invoice retry
    // -------------------------------------------------------

    /**
     * Attempts to pay an unpaid invoice.
     * Returns true if payment succeeds, false otherwise.
     */
    public boolean retryInvoicePayment(String stripeInvoiceId) {
        try {
            Invoice invoice = Invoice.retrieve(stripeInvoiceId);
            Invoice paid = invoice.pay(InvoicePayParams.builder().build());
            boolean success = "paid".equals(paid.getStatus());
            log.info("Invoice {} retry result: {}", stripeInvoiceId,
                    success ? "SUCCESS" : "FAILED");
            return success;
        } catch (StripeException e) {
            log.warn("Invoice {} retry failed with Stripe error: {}",
                    stripeInvoiceId, e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------
    // Invoice PDF retrieval
    // -------------------------------------------------------

    /**
     * Returns the URL to download the invoice PDF from Stripe.
     * This URL is temporary — we download the PDF and store it in GCS.
     */
    public String getInvoicePdfUrl(String stripeInvoiceId) throws StripeException {
        Invoice invoice = Invoice.retrieve(stripeInvoiceId);
        return invoice.getInvoicePdf();
    }
}