package com.billflow.service;

import com.billflow.model.PaymentRetry;
import com.billflow.model.Subscription;
import com.billflow.model.User;
import com.billflow.model.enums.RetryStatus;
import com.billflow.model.enums.SubscriptionStatus;
import com.billflow.repository.PaymentRetryRepository;
import org.junit.jupiter.api.BeforeEach;
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
class PaymentRetryServiceTest {

    @Mock private PaymentRetryRepository retryRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private StripeService stripeService;

    @InjectMocks
    private PaymentRetryService retryService;

    private Subscription subscription;
    private PaymentRetry retry;

    @BeforeEach
    void setUp() {
        User user = new User("test@example.com");
        user.setId(1L);

        subscription = new Subscription(user, "price_test");
        subscription.setId(10L);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStripeSubscriptionId("sub_stripe_123");

        retry = new PaymentRetry(subscription, "in_invoice_123");
        retry.setId(100L);
    }

    @Test
    @DisplayName("Successful retry → SUCCEEDED + subscription ACTIVE")
    void retrySucceeds() {
        when(stripeService.retryInvoicePayment("in_invoice_123")).thenReturn(true);

        retryService.processRetry(retry);

        assertEquals(RetryStatus.SUCCEEDED, retry.getStatus());
        verify(subscriptionService).transitionStatus(10L, SubscriptionStatus.ACTIVE);
        verify(retryRepository).update(retry);
    }

    @Test
    @DisplayName("Failed retry (not exhausted) → schedule next attempt")
    void retryFailsNotExhausted() {
        retry.setAttemptCount(1);
        retry.setMaxAttempts(3);
        when(stripeService.retryInvoicePayment("in_invoice_123")).thenReturn(false);

        retryService.processRetry(retry);

        assertEquals(RetryStatus.PENDING, retry.getStatus());
        assertEquals(2, retry.getAttemptCount());
        assertNotNull(retry.getNextRetryAt());
        verify(subscriptionService, never()).transitionStatus(any(), any());
        verify(retryRepository).update(retry);
    }

    @Test
    @DisplayName("Failed retry (exhausted) → EXHAUSTED + subscription PAST_DUE")
    void retryFailsExhausted() {
        retry.setAttemptCount(3);
        retry.setMaxAttempts(3);
        when(stripeService.retryInvoicePayment("in_invoice_123")).thenReturn(false);

        retryService.processRetry(retry);

        assertEquals(RetryStatus.EXHAUSTED, retry.getStatus());
        verify(subscriptionService).transitionStatus(10L, SubscriptionStatus.PAST_DUE);
        verify(retryRepository).update(retry);
    }

    @Test
    @DisplayName("Create retry → sets correct initial state")
    void createRetry() {
        when(retryRepository.save(any(PaymentRetry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentRetry created = retryService.createRetry(subscription, "in_new_invoice");

        assertEquals(1, created.getAttemptCount());
        assertEquals(RetryStatus.PENDING, created.getStatus());
        assertNotNull(created.getNextRetryAt());
        verify(retryRepository).save(any(PaymentRetry.class));
    }
}