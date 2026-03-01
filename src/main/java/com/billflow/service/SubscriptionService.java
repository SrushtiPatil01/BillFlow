package com.billflow.service;

import com.billflow.exception.ResourceNotFoundException;
import com.billflow.model.Subscription;
import com.billflow.model.User;
import com.billflow.model.enums.SubscriptionStatus;
import com.billflow.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStateMachine stateMachine;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                SubscriptionStateMachine stateMachine) {
        this.subscriptionRepository = subscriptionRepository;
        this.stateMachine = stateMachine;
    }

    @Transactional
    public Subscription createPendingSubscription(User user, String stripePriceId) {
        Subscription sub = new Subscription(user, stripePriceId);
        subscriptionRepository.save(sub);
        log.info("Created PENDING subscription: id={}, userId={}, priceId={}",
                sub.getId(), user.getId(), stripePriceId);
        return sub;
    }

    @Transactional(readOnly = true)
    public Subscription getById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
    }

    @Transactional(readOnly = true)
    public Subscription getByStripeSubscriptionId(String stripeSubId) {
        return subscriptionRepository.findByStripeSubscriptionId(stripeSubId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription", "stripeId=" + stripeSubId));
    }

    /**
     * Transitions a subscription to a new status using the state machine.
     * The state machine validates that the transition is allowed.
     */
    @Transactional
    public Subscription transitionStatus(Long subscriptionId, SubscriptionStatus newStatus) {
        Subscription sub = getById(subscriptionId);
        SubscriptionStatus validated = stateMachine.transition(sub.getStatus(), newStatus);
        sub.setStatus(validated);
        subscriptionRepository.update(sub);
        log.info("Subscription {} transitioned to {}", subscriptionId, validated);
        return sub;
    }

    @Transactional
    public Subscription activateSubscription(String stripeSubId,
                                              String stripeSubscriptionId,
                                              LocalDateTime periodEnd) {
        Subscription sub = getByStripeSubscriptionId(stripeSubId);
        stateMachine.transition(sub.getStatus(), SubscriptionStatus.ACTIVE);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStripeSubscriptionId(stripeSubscriptionId);
        sub.setCurrentPeriodEnd(periodEnd);
        subscriptionRepository.update(sub);
        log.info("Activated subscription: id={}", sub.getId());
        return sub;
    }

    @Transactional
    public void setStripeSubscriptionId(Long subscriptionId, String stripeSubId) {
        Subscription sub = getById(subscriptionId);
        sub.setStripeSubscriptionId(stripeSubId);
        subscriptionRepository.update(sub);
    }
}