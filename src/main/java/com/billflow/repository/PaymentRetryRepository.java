package com.billflow.repository;

import com.billflow.model.PaymentRetry;
import com.billflow.model.enums.RetryStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class PaymentRetryRepository {

    private final SessionFactory sessionFactory;

    public PaymentRetryRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    public PaymentRetry save(PaymentRetry retry) {
        currentSession().persist(retry);
        return retry;
    }

    public PaymentRetry update(PaymentRetry retry) {
        currentSession().merge(retry);
        return retry;
    }

    /**
     * Finds all pending retries whose next_retry_at has passed.
     * Used by the daily retry scheduler.
     */
    public List<PaymentRetry> findPendingRetriesDue() {
        return currentSession()
                .createQuery(
                    "FROM PaymentRetry r " +
                    "WHERE r.status = :status AND r.nextRetryAt <= :now",
                    PaymentRetry.class)
                .setParameter("status", RetryStatus.PENDING)
                .setParameter("now", LocalDateTime.now())
                .getResultList();
    }
}