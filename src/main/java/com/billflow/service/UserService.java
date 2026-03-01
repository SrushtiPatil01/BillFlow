package com.billflow.service;

import com.billflow.exception.ResourceNotFoundException;
import com.billflow.model.User;
import com.billflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        userRepository.findByEmail(email).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "User already exists with email: " + email);
        });

        User user = new User(email);
        userRepository.save(user);
        log.info("Created user: id={}, email={}", user.getId(), user.getEmail());
        return user;
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional
    public User updateStripeCustomerId(Long userId, String stripeCustomerId) {
        User user = getUserById(userId);
        user.setStripeCustomerId(stripeCustomerId);
        userRepository.update(user);
        log.info("Updated Stripe customer ID for user {}: {}", userId, stripeCustomerId);
        return user;
    }
}
