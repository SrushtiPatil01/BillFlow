package com.billflow.controller;

import com.billflow.dto.CheckoutResponse;
import com.billflow.dto.SubscribeRequest;
import com.billflow.dto.SubscriptionResponse;
import com.billflow.model.Subscription;
import com.billflow.model.User;
import com.billflow.model.enums.SubscriptionStatus;
import com.billflow.service.StripeService;
import com.billflow.service.SubscriptionService;
import com.billflow.service.UserService;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;
    private final UserService userService;
    private final StripeService stripeService;

    // Default URLs — in production these come from config
    private static final String SUCCESS_URL = "http://localhost:8080/success?session_id={CHECKOUT_SESSION_ID}";
    private static final String CANCEL_URL = "http://localhost:8080/cancel";

    public SubscriptionController(SubscriptionService subscriptionService,
                                   UserService userService,
                                   StripeService stripeService) {
        this.subscriptionService = subscriptionService;
        this.userService = userService;
        this.stripeService = stripeService;
    }

    /**
     * POST /api/subscribe
     * Creates a Stripe Checkout Session and returns the checkout URL.
     * Also creates a PENDING subscription record in our database.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<CheckoutResponse> subscribe(
            @RequestBody SubscribeRequest request) throws Exception {
        // Validate input
        if (request.getUserId() == null || request.getPriceId() == null) {
            throw new IllegalArgumentException("userId and priceId are required");
        }

        // Get user
        User user = userService.getUserById(request.getUserId());

        // Create PENDING subscription in our DB
        Subscription sub = subscriptionService.createPendingSubscription(
                user, request.getPriceId());

        // Create Stripe Checkout Session
        Session session = stripeService.createCheckoutSession(
                user.getEmail(),
                request.getPriceId(),
                SUCCESS_URL,
                CANCEL_URL,
                sub.getId()
        );

        log.info("Created checkout session for user {} subscription {}",
                user.getId(), sub.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CheckoutResponse(session.getUrl(), sub.getId()));
    }

    /**
     * GET /api/subscriptions/{id}
     * Returns subscription status and details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> getSubscription(@PathVariable Long id) {
        Subscription sub = subscriptionService.getById(id);
        return ResponseEntity.ok(SubscriptionResponse.from(sub));
    }

    /**
     * POST /api/subscriptions/{id}/cancel
     * Cancels the subscription both in Stripe and locally.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(@PathVariable Long id) 
            throws Exception {
        Subscription sub = subscriptionService.getById(id);

        // Cancel in Stripe first
        if (sub.getStripeSubscriptionId() != null) {
            stripeService.cancelSubscription(sub.getStripeSubscriptionId());
        }

        // Transition locally — the state machine validates this is allowed
        // Note: Stripe will also send a customer.subscription.deleted webhook,
        // but our idempotency handling will skip it since we've already cancelled.
        Subscription cancelled = subscriptionService.transitionStatus(
                id, SubscriptionStatus.CANCELLED);

        log.info("Cancelled subscription {}", id);
        return ResponseEntity.ok(SubscriptionResponse.from(cancelled));
    }
}
