package com.billflow.dto;

import com.billflow.model.Subscription;
import java.time.LocalDateTime;

public class SubscriptionResponse {

    private Long id;
    private Long userId;
    private String email;
    private String status;
    private String stripePriceId;
    private String stripeSubscriptionId;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime createdAt;

    public SubscriptionResponse() {}

    public static SubscriptionResponse from(Subscription sub) {
        SubscriptionResponse r = new SubscriptionResponse();
        r.id = sub.getId();
        r.userId = sub.getUser().getId();
        r.email = sub.getUser().getEmail();
        r.status = sub.getStatus().name();
        r.stripePriceId = sub.getStripePriceId();
        r.stripeSubscriptionId = sub.getStripeSubscriptionId();
        r.currentPeriodEnd = sub.getCurrentPeriodEnd();
        r.createdAt = sub.getCreatedAt();
        return r;
    }

    // --- Getters ---

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public String getStripePriceId() { return stripePriceId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public LocalDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}