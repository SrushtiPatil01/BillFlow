package com.billflow.model;

import com.billflow.model.enums.RetryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRetryTest {

    @Test
    @DisplayName("New retry → defaults to attempt 1, PENDING, +1 day")
    void newRetryDefaults() {
        User user = new User("test@example.com");
        Subscription sub = new Subscription(user, "price_abc");
        PaymentRetry retry = new PaymentRetry(sub, "in_test_123");

        assertEquals(1, retry.getAttemptCount());
        assertEquals(3, retry.getMaxAttempts());
        assertEquals(RetryStatus.PENDING, retry.getStatus());

        // next retry should be approximately 1 day from now
        LocalDateTime expected = LocalDateTime.now().plusDays(1);
        assertTrue(retry.getNextRetryAt().isAfter(LocalDateTime.now()));
        assertTrue(retry.getNextRetryAt().isBefore(expected.plusMinutes(1)));
    }

    @Test
    @DisplayName("scheduleNextRetry → increments count and sets backoff delay")
    void scheduleNextRetryBackoff() {
        User user = new User("test@example.com");
        Subscription sub = new Subscription(user, "price_abc");
        PaymentRetry retry = new PaymentRetry(sub, "in_test_123");

        // After first failure (attempt 1), schedule next → attempt 2, +3 days
        retry.scheduleNextRetry();
        assertEquals(2, retry.getAttemptCount());
        assertTrue(retry.getNextRetryAt().isAfter(LocalDateTime.now().plusDays(2)));

        // After second failure (attempt 2), schedule next → attempt 3, +7 days
        retry.scheduleNextRetry();
        assertEquals(3, retry.getAttemptCount());
        assertTrue(retry.getNextRetryAt().isAfter(LocalDateTime.now().plusDays(6)));
    }
}