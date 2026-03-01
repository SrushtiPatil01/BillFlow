package com.billflow.repository;

import com.billflow.model.User;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private final SessionFactory sessionFactory;

    public UserRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    public User save(User user) {
        currentSession().persist(user);
        return user;
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(currentSession().get(User.class, id));
    }

    public Optional<User> findByEmail(String email) {
        return currentSession()
                .createQuery("FROM User u WHERE u.email = :email", User.class)
                .setParameter("email", email)
                .uniqueResultOptional();
    }

    public Optional<User> findByStripeCustomerId(String stripeCustomerId) {
        return currentSession()
                .createQuery(
                    "FROM User u WHERE u.stripeCustomerId = :cid", User.class)
                .setParameter("cid", stripeCustomerId)
                .uniqueResultOptional();
    }

    public User update(User user) {
        currentSession().merge(user);
        return user;
    }
}