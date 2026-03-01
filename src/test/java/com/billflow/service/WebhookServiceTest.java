package com.billflow.service;

import com.billflow.repository.PaymentEventRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock private PaymentEventRepository paymentEventRepository;
    @Mock private StripeService stripeService;
    @Mock private KafkaProducer<String, String> kafkaProducer;

    @InjectMocks
    private WebhookService webhookService;

    @Test
    @DisplayName("Duplicate event → returns false, no Kafka publish")
    void duplicateEventSkipped() {
        // Simulate: stripeService.verifyAndParseWebhook returns an event with known ID
        com.stripe.model.Event mockEvent = mock(com.stripe.model.Event.class);
        when(mockEvent.getId()).thenReturn("evt_duplicate_123");
        when(mockEvent.getType()).thenReturn("checkout.session.completed");
        when(stripeService.verifyAndParseWebhook(any(), any())).thenReturn(mockEvent);

        // Event already exists in DB
        when(paymentEventRepository.existsByStripeEventId("evt_duplicate_123"))
                .thenReturn(true);

        boolean result = webhookService.handleWebhook("{}", "sig_header");

        assertFalse(result);
        verify(kafkaProducer, never()).send(any(ProducerRecord.class), any());
        verify(paymentEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("New event → saves to DB, publishes to Kafka, returns true")
    void newEventProcessed() {
        com.stripe.model.Event mockEvent = mock(com.stripe.model.Event.class);
        when(mockEvent.getId()).thenReturn("evt_new_456");
        when(mockEvent.getType()).thenReturn("invoice.payment_failed");
        when(stripeService.verifyAndParseWebhook(any(), any())).thenReturn(mockEvent);

        when(paymentEventRepository.existsByStripeEventId("evt_new_456"))
                .thenReturn(false);

        boolean result = webhookService.handleWebhook("{\"id\":\"evt_new_456\"}", "sig_header");

        assertTrue(result);
        verify(paymentEventRepository).save(any());
        verify(kafkaProducer).send(any(ProducerRecord.class), any());
    }
}

