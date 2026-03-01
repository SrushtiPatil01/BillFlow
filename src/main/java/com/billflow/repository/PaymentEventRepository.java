package com.billflow.repository;

import com.billflow.model.PaymentEvent;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PaymentEventRepository {

    private final SessionFactory sessionFactory;

    public PaymentEventRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    public PaymentEvent save(PaymentEvent event) {
        currentSession().persist(event);
        return event;
    }

    /**
     * Check if a Stripe event has already been processed.
     * This is the core of the idempotency mechanism.
     */
    public boolean existsByStripeEventId(String stripeEventId) {
        Long count = currentSession()
                .createQuery(
                    "SELECT COUNT(e) FROM PaymentEvent e WHERE e.stripeEventId = :eid",
                    Long.class)
                .setParameter("eid", stripeEventId)
                .uniqueResult();
        return count != null && count > 0;
    }

    public Optional<PaymentEvent> findByStripeEventId(String stripeEventId) {
        return currentSession()
                .createQuery(
                    "FROM PaymentEvent e WHERE e.stripeEventId = :eid",
                    PaymentEvent.class)
                .setParameter("eid", stripeEventId)
                .uniqueResultOptional();
    }
}