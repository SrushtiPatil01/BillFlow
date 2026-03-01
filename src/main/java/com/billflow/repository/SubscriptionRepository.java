package com.billflow.repository;

import com.billflow.model.Subscription;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SubscriptionRepository {

    private final SessionFactory sessionFactory;

    public SubscriptionRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    public Subscription save(Subscription subscription) {
        currentSession().persist(subscription);
        return subscription;
    }

    public Optional<Subscription> findById(Long id) {
        return Optional.ofNullable(currentSession().get(Subscription.class, id));
    }

    public Optional<Subscription> findByStripeSubscriptionId(String stripeSubId) {
        return currentSession()
                .createQuery(
                    "FROM Subscription s WHERE s.stripeSubscriptionId = :sid",
                    Subscription.class)
                .setParameter("sid", stripeSubId)
                .uniqueResultOptional();
    }

    public Subscription update(Subscription subscription) {
        currentSession().merge(subscription);
        return subscription;
    }
}