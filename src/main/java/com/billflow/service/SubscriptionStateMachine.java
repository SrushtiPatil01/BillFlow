package com.billflow.service;

import com.billflow.exception.InvalidStateTransitionException;
import com.billflow.model.enums.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.billflow.model.enums.SubscriptionStatus.*;

/**
 * Enforces valid subscription state transitions.
 *
 * Valid transitions:
 *   PENDING   → ACTIVE       (checkout completed)
 *   ACTIVE    → PAST_DUE     (3 retries exhausted)
 *   ACTIVE    → CANCELLED    (user cancels)
 *   PAST_DUE  → ACTIVE       (retry succeeds)
 *   PAST_DUE  → CANCELLED    (user cancels while past due)
 *   CANCELLED → (terminal — no transitions allowed)
 *
 * Any other transition throws InvalidStateTransitionException.
 */
@Component
public class SubscriptionStateMachine {

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> VALID_TRANSITIONS =
            Map.of(
                PENDING,   Set.of(ACTIVE),
                ACTIVE,    Set.of(PAST_DUE, CANCELLED),
                PAST_DUE,  Set.of(ACTIVE, CANCELLED),
                CANCELLED, Set.of()
            );

    /**
     * Validates and returns the new status if the transition is allowed.
     * Throws InvalidStateTransitionException if not.
     */
    public SubscriptionStatus transition(SubscriptionStatus current, SubscriptionStatus desired) {
        Set<SubscriptionStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(desired)) {
            throw new InvalidStateTransitionException(current, desired);
        }
        return desired;
    }
}
