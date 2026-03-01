package com.billflow.service;

import com.billflow.config.KafkaConfig;
import com.billflow.model.PaymentEvent;
import com.billflow.repository.PaymentEventRepository;
import com.stripe.model.Event;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the three-step webhook processing pipeline:
 *   1. Verify signature (done in StripeService)
 *   2. Check idempotency (this service)
 *   3. Publish to Kafka (this service)
 *
 * The order is critical:
 *   - Signature verification happens BEFORE idempotency check
 *   - Idempotency check happens BEFORE Kafka publish
 *   - We return 200 to Stripe BEFORE the consumer processes the event
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentEventRepository paymentEventRepository;
    private final StripeService stripeService;
    private final KafkaProducer<String, String> kafkaProducer;

    public WebhookService(PaymentEventRepository paymentEventRepository,
                           StripeService stripeService,
                           KafkaProducer<String, String> kafkaProducer) {
        this.paymentEventRepository = paymentEventRepository;
        this.stripeService = stripeService;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Processes an incoming Stripe webhook request.
     *
     * @param rawBody         raw request body bytes — must be read BEFORE JSON parsing
     * @param signatureHeader Stripe-Signature header value
     * @return true if event was newly processed, false if it was a duplicate
     */
    @Transactional
    public boolean handleWebhook(String rawBody, String signatureHeader) {
        // Step 1: Verify the webhook signature (throws on failure)
        Event event = stripeService.verifyAndParseWebhook(rawBody, signatureHeader);

        String eventId = event.getId();
        String eventType = event.getType();

        // Step 2: Idempotency check — skip if already processed
        if (paymentEventRepository.existsByStripeEventId(eventId)) {
            log.info("Duplicate webhook event skipped: {} ({})", eventId, eventType);
            return false;
        }

        // Step 3: Record the event and publish to Kafka
        PaymentEvent paymentEvent = new PaymentEvent(eventId, eventType);
        paymentEventRepository.save(paymentEvent);

        ProducerRecord<String, String> record =
                new ProducerRecord<>(KafkaConfig.TOPIC, eventId, rawBody);
        kafkaProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Failed to publish event {} to Kafka", eventId, exception);
            } else {
                log.info("Published event {} to Kafka topic {} partition {} offset {}",
                        eventId, metadata.topic(), metadata.partition(), metadata.offset());
            }
        });

        log.info("Webhook processed: {} ({})", eventId, eventType);
        return true;
    }
}