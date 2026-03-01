package com.billflow.service;

import com.billflow.config.KafkaConfig;
import com.billflow.model.PaymentEvent;
import com.billflow.model.Subscription;
import com.billflow.model.enums.SubscriptionStatus;
import com.billflow.repository.PaymentEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background Kafka consumer that processes Stripe events from the
 * stripe.events topic. Runs on a dedicated thread within the same JVM.
 *
 * Handles three event types:
 *   - checkout.session.completed  → activate subscription
 *   - invoice.payment_failed      → create retry record
 *   - customer.subscription.deleted → cancel subscription
 */
@Component
public class KafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final KafkaConsumer<String, String> consumer;
    private final SubscriptionService subscriptionService;
    private final PaymentRetryService paymentRetryService;
    private final InvoiceService invoiceService;
    private final PaymentEventRepository paymentEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    public KafkaEventConsumer(KafkaConsumer<String, String> consumer,
                               SubscriptionService subscriptionService,
                               PaymentRetryService paymentRetryService,
                               InvoiceService invoiceService,
                               PaymentEventRepository paymentEventRepository,
                               ObjectMapper objectMapper,
                               PlatformTransactionManager txManager) {
        this.consumer = consumer;
        this.subscriptionService = subscriptionService;
        this.paymentRetryService = paymentRetryService;
        this.invoiceService = invoiceService;
        this.paymentEventRepository = paymentEventRepository;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @PostConstruct
    public void start() {
        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-event-consumer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::consumeLoop);
        log.info("Kafka event consumer started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        consumer.wakeup();
        if (executor != null) {
            executor.shutdown();
        }
        log.info("Kafka event consumer stopped");
    }

    private void consumeLoop() {
        consumer.subscribe(Collections.singletonList(KafkaConfig.TOPIC));

        while (running.get()) {
            try {
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    processRecord(record);
                }

                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            } catch (org.apache.kafka.common.errors.WakeupException e) {
                if (running.get()) {
                    throw e;
                }
                // Expected during shutdown — exit gracefully
            } catch (Exception e) {
                log.error("Error in Kafka consumer loop", e);
            }
        }
    }

    private void processRecord(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("type").asText();
            String eventId = root.path("id").asText();

            log.info("Processing Kafka event: {} ({})", eventId, eventType);

            switch (eventType) {
                case "checkout.session.completed" ->
                        handleCheckoutCompleted(root, eventId);
                case "invoice.payment_failed" ->
                        handlePaymentFailed(root, eventId);
                case "customer.subscription.deleted" ->
                        handleSubscriptionDeleted(root, eventId);
                default ->
                        log.debug("Ignoring unhandled event type: {}", eventType);
            }

            // Mark event as processed
            txTemplate.executeWithoutResult(status -> {
                paymentEventRepository.findByStripeEventId(eventId)
                        .ifPresent(pe -> {
                            pe.setProcessedAt(LocalDateTime.now());
                            paymentEventRepository.save(pe);
                        });
            });

        } catch (Exception e) {
            log.error("Failed to process Kafka record: key={}", record.key(), e);
        }
    }

    /**
     * Handles checkout.session.completed:
     * - Finds subscription by metadata
     * - Sets the Stripe subscription ID
     * - Transitions status from PENDING → ACTIVE
     * - Uploads invoice PDF to GCS
     */
    private void handleCheckoutCompleted(JsonNode root, String eventId) {
        JsonNode sessionData = root.path("data").path("object");
        String stripeSubId = sessionData.path("subscription").asText();
        String metadataSubId = sessionData.path("metadata")
                .path("billflow_subscription_id").asText();
        String stripeCustomerId = sessionData.path("customer").asText();

        if (metadataSubId == null || metadataSubId.isEmpty()) {
            log.warn("No billflow_subscription_id in checkout metadata for event {}",
                    eventId);
            return;
        }

        Long subscriptionId = Long.parseLong(metadataSubId);

        txTemplate.executeWithoutResult(status -> {
            Subscription sub = subscriptionService.getById(subscriptionId);

            // Set the Stripe subscription ID from the checkout session
            sub.setStripeSubscriptionId(stripeSubId);

            // Update user's Stripe customer ID if not set
            if (sub.getUser().getStripeCustomerId() == null) {
                sub.getUser().setStripeCustomerId(stripeCustomerId);
            }

            // Transition PENDING → ACTIVE
            subscriptionService.transitionStatus(subscriptionId,
                    SubscriptionStatus.ACTIVE);

            // Set period end if available
            long periodEndEpoch = sessionData.path("current_period_end").asLong(0);
            if (periodEndEpoch > 0) {
                LocalDateTime periodEnd = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(periodEndEpoch), ZoneId.systemDefault());
                sub.setCurrentPeriodEnd(periodEnd);
            }
        });

        // Upload invoice PDF to GCS (non-critical — don't fail the event)
        try {
            String invoiceId = sessionData.path("invoice").asText();
            if (invoiceId != null && !invoiceId.isEmpty()) {
                Subscription sub = subscriptionService.getById(subscriptionId);
                invoiceService.uploadInvoicePdf(sub.getUser().getId(), invoiceId);
            }
        } catch (Exception e) {
            log.warn("Failed to upload invoice PDF for event {}", eventId, e);
        }

        log.info("Checkout completed: subscriptionId={}, stripeSubId={}",
                subscriptionId, stripeSubId);
    }

    /**
     * Handles invoice.payment_failed:
     * - Finds subscription by Stripe subscription ID
     * - Creates a payment retry record
     */
    private void handlePaymentFailed(JsonNode root, String eventId) {
        JsonNode invoiceData = root.path("data").path("object");
        String stripeSubId = invoiceData.path("subscription").asText();
        String stripeInvoiceId = invoiceData.path("id").asText();

        txTemplate.executeWithoutResult(status -> {
            Subscription sub = subscriptionService.getByStripeSubscriptionId(stripeSubId);
            paymentRetryService.createRetry(sub, stripeInvoiceId);
        });

        log.info("Payment failed recorded: stripeSubId={}, invoiceId={}",
                stripeSubId, stripeInvoiceId);
    }

    /**
     * Handles customer.subscription.deleted:
     * - Finds subscription by Stripe subscription ID
     * - Transitions to CANCELLED
     */
    private void handleSubscriptionDeleted(JsonNode root, String eventId) {
        JsonNode subData = root.path("data").path("object");
        String stripeSubId = subData.path("id").asText();

        txTemplate.executeWithoutResult(status -> {
            Subscription sub = subscriptionService.getByStripeSubscriptionId(stripeSubId);
            subscriptionService.transitionStatus(sub.getId(),
                    SubscriptionStatus.CANCELLED);
        });

        log.info("Subscription deleted: stripeSubId={}", stripeSubId);
    }
}