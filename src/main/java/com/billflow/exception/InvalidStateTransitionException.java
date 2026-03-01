package com.billflow.exception;

import com.billflow.model.enums.SubscriptionStatus;

public class InvalidStateTransitionException extends RuntimeException {

    private final SubscriptionStatus from;
    private final SubscriptionStatus to;

    public InvalidStateTransitionException(SubscriptionStatus from, SubscriptionStatus to) {
        super(String.format("Invalid subscription state transition: %s → %s", from, to));
        this.from = from;
        this.to = to;
    }

    public SubscriptionStatus getFrom() { return from; }
    public SubscriptionStatus getTo() { return to; }
}
