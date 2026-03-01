package com.billflow.scheduler;

import com.billflow.model.PaymentRetry;
import com.billflow.repository.PaymentRetryRepository;
import com.billflow.service.PaymentRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Runs once daily at 9:00 AM to process pending payment retries.
 *
 * Queries the payment_retries table for all records where:
 *   - status = PENDING
 *   - next_retry_at <= now
 *
 * Each retry is processed individually so that a failure in one
 * does not block the others.
 */
@Component
public class PaymentRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentRetryScheduler.class);

    private final PaymentRetryRepository retryRepository;
    private final PaymentRetryService retryService;

    public PaymentRetryScheduler(PaymentRetryRepository retryRepository,
                                  PaymentRetryService retryService) {
        this.retryRepository = retryRepository;
        this.retryService = retryService;
    }

    /**
     * Cron: second minute hour day month weekday
     * "0 0 9 * * *" = every day at 09:00:00
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void runPendingRetries() {
        log.info("Payment retry scheduler started");

        List<PaymentRetry> pendingRetries = retryRepository.findPendingRetriesDue();
        log.info("Found {} pending retries to process", pendingRetries.size());

        int succeeded = 0;
        int failed = 0;

        for (PaymentRetry retry : pendingRetries) {
            try {
                retryService.processRetry(retry);
                succeeded++;
            } catch (Exception e) {
                log.error("Failed to process retry id={}: {}",
                        retry.getId(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("Payment retry scheduler completed: {} succeeded, {} failed",
                succeeded, failed);
    }
}