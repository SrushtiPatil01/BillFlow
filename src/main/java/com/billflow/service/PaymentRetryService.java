package com.billflow.service;

import com.billflow.model.PaymentRetry;
import com.billflow.model.Subscription;
import com.billflow.model.enums.RetryStatus;
import com.billflow.model.enums.SubscriptionStatus;
import com.billflow.repository.PaymentRetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentRetryService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRetryService.class);

    private final PaymentRetryRepository retryRepository;
    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;

    public PaymentRetryService(PaymentRetryRepository retryRepository,
                                SubscriptionService subscriptionService,
                                StripeService stripeService) {
        this.retryRepository = retryRepository;
        this.subscriptionService = subscriptionService;
        this.stripeService = stripeService;
    }

    /**
     * Creates a new retry record for a failed payment.
     * First retry is scheduled for 1 day from now.
     */
    @Transactional
    public PaymentRetry createRetry(Subscription subscription, String stripeInvoiceId) {
        PaymentRetry retry = new PaymentRetry(subscription, stripeInvoiceId);
        retryRepository.save(retry);
        log.info("Created payment retry: subscriptionId={}, invoiceId={}, nextRetryAt={}",
                subscription.getId(), stripeInvoiceId, retry.getNextRetryAt());
        return retry;
    }

    /**
     * Processes a single retry attempt.
     *
     * On success: mark retry as SUCCEEDED, transition subscription to ACTIVE.
     * On failure:
     *   - If attempts < max: schedule next retry with backoff
     *   - If attempts exhausted: mark EXHAUSTED, transition subscription to PAST_DUE
     *
     * This method is @Transactional to ensure database consistency.
     * If the Stripe call succeeds but the DB update fails, the transaction
     * rolls back and the retry will be attempted again on the next scheduler run.
     */
    @Transactional
    public void processRetry(PaymentRetry retry) {
        log.info("Processing retry: id={}, attempt={}/{}, invoiceId={}",
                retry.getId(), retry.getAttemptCount(),
                retry.getMaxAttempts(), retry.getStripeInvoiceId());

        boolean paymentSucceeded = stripeService.retryInvoicePayment(
                retry.getStripeInvoiceId());

        if (paymentSucceeded) {
            retry.setStatus(RetryStatus.SUCCEEDED);
            retryRepository.update(retry);

            subscriptionService.transitionStatus(
                    retry.getSubscription().getId(),
                    SubscriptionStatus.ACTIVE);

            log.info("Payment retry succeeded: subscriptionId={}",
                    retry.getSubscription().getId());

        } else if (retry.getAttemptCount() >= retry.getMaxAttempts()) {
            // All retries exhausted
            retry.setStatus(RetryStatus.EXHAUSTED);
            retryRepository.update(retry);

            subscriptionService.transitionStatus(
                    retry.getSubscription().getId(),
                    SubscriptionStatus.PAST_DUE);

            log.warn("Payment retries exhausted: subscriptionId={}, moving to PAST_DUE",
                    retry.getSubscription().getId());

        } else {
            // Schedule next retry with exponential backoff
            retry.scheduleNextRetry();
            retryRepository.update(retry);

            log.info("Payment retry failed, scheduling next: attempt={}, nextRetryAt={}",
                    retry.getAttemptCount(), retry.getNextRetryAt());
        }
    }
}