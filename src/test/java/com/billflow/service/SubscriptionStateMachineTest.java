package com.billflow.service;

import com.billflow.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.billflow.model.enums.SubscriptionStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionStateMachineTest {

    private SubscriptionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new SubscriptionStateMachine();
    }

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @Test
        @DisplayName("PENDING → ACTIVE (checkout completed)")
        void pendingToActive() {
            assertEquals(ACTIVE, stateMachine.transition(PENDING, ACTIVE));
        }

        @Test
        @DisplayName("ACTIVE → PAST_DUE (retries exhausted)")
        void activeToPastDue() {
            assertEquals(PAST_DUE, stateMachine.transition(ACTIVE, PAST_DUE));
        }

        @Test
        @DisplayName("ACTIVE → CANCELLED (user cancels)")
        void activeToCancelled() {
            assertEquals(CANCELLED, stateMachine.transition(ACTIVE, CANCELLED));
        }

        @Test
        @DisplayName("PAST_DUE → ACTIVE (retry succeeds)")
        void pastDueToActive() {
            assertEquals(ACTIVE, stateMachine.transition(PAST_DUE, ACTIVE));
        }

        @Test
        @DisplayName("PAST_DUE → CANCELLED (user cancels while past due)")
        void pastDueToCancelled() {
            assertEquals(CANCELLED, stateMachine.transition(PAST_DUE, CANCELLED));
        }
    }

    @Nested
    @DisplayName("Invalid transitions — must throw")
    class InvalidTransitions {

        @Test
        @DisplayName("PENDING → CANCELLED (cannot cancel before activating)")
        void pendingToCancelled() {
            assertThrows(InvalidStateTransitionException.class,
                    () -> stateMachine.transition(PENDING, CANCELLED));
        }

        @Test
        @DisplayName("PENDING → PAST_DUE (cannot fail before activating)")
        void pendingToPastDue() {
            assertThrows(InvalidStateTransitionException.class,
                    () -> stateMachine.transition(PENDING, PAST_DUE));
        }

        @Test
        @DisplayName("CANCELLED → ACTIVE (cannot reactivate)")
        void cancelledToActive() {
            assertThrows(InvalidStateTransitionException.class,
                    () -> stateMachine.transition(CANCELLED, ACTIVE));
        }

        @Test
        @DisplayName("CANCELLED → PENDING (terminal state)")
        void cancelledToPending() {
            assertThrows(InvalidStateTransitionException.class,
                    () -> stateMachine.transition(CANCELLED, PENDING));
        }

        @Test
        @DisplayName("CANCELLED → PAST_DUE (terminal state)")
        void cancelledToPastDue() {
            assertThrows(InvalidStateTransitionException.class,
                    () -> stateMachine.transition(CANCELLED, PAST_DUE));
        }

        @Test
        @DisplayName("ACTIVE → PENDING (cannot go backwards)")
        void activeToPending() {
            assertThrows(InvalidStateTransitionException.class,
                    () -> stateMachine.transition(ACTIVE, PENDING));
        }
    }

    @Nested
    @DisplayName("Exception message quality")
    class ExceptionMessages {

        @Test
        @DisplayName("Exception includes from and to states")
        void exceptionContainsStates() {
            InvalidStateTransitionException ex = assertThrows(
                    InvalidStateTransitionException.class,
                    () -> stateMachine.transition(CANCELLED, ACTIVE));

            assertEquals(CANCELLED, ex.getFrom());
            assertEquals(ACTIVE, ex.getTo());
            assertTrue(ex.getMessage().contains("CANCELLED"));
            assertTrue(ex.getMessage().contains("ACTIVE"));
        }
    }
}