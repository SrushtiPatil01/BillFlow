package com.billflow.model;

import com.billflow.model.enums.RetryStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import static com.billflow.model.enums.RetryStatus.*;

@Entity
@Table(name = "payment_retries")
public class PaymentRetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(name = "stripe_invoice_id", nullable = false)
    private String stripeInvoiceId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetryStatus status = RetryStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PaymentRetry() {}

    public PaymentRetry(Subscription subscription, String stripeInvoiceId) {
        this.subscription = subscription;
        this.stripeInvoiceId = stripeInvoiceId;
        this.attemptCount = 1;
        this.nextRetryAt = LocalDateTime.now().plusDays(1);
        this.status = RetryStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Calculates the next retry delay based on attempt count.
     * Attempt 1 → retry after 1 day
     * Attempt 2 → retry after 3 days
     * Attempt 3 → retry after 7 days (final attempt)
     */
    public void scheduleNextRetry() {
        this.attemptCount++;
        int daysUntilRetry = switch (this.attemptCount) {
            case 2 -> 3;
            case 3 -> 7;
            default -> 1;
        };
        this.nextRetryAt = LocalDateTime.now().plusDays(daysUntilRetry);
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Subscription getSubscription() { return subscription; }
    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public String getStripeInvoiceId() { return stripeInvoiceId; }
    public void setStripeInvoiceId(String stripeInvoiceId) {
        this.stripeInvoiceId = stripeInvoiceId;
    }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public RetryStatus getStatus() { return status; }
    public void setStatus(RetryStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}