-- ============================================================
-- BillFlow Database Schema
-- Run: mysql -u billflow -p billflow < schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS billflow
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE billflow;

-- -----------------------------------------------------------
-- Users
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    email               VARCHAR(255)    NOT NULL UNIQUE,
    stripe_customer_id  VARCHAR(255)    UNIQUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_users_email (email),
    INDEX idx_users_stripe_customer (stripe_customer_id)
) ENGINE=InnoDB;

-- -----------------------------------------------------------
-- Subscriptions
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS subscriptions (
    id                       BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id                  BIGINT          NOT NULL,
    stripe_subscription_id   VARCHAR(255)    UNIQUE,
    stripe_price_id          VARCHAR(255)    NOT NULL,
    status                   ENUM('PENDING', 'ACTIVE', 'PAST_DUE', 'CANCELLED')
                                             NOT NULL DEFAULT 'PENDING',
    current_period_end       TIMESTAMP       NULL,
    created_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT,

    INDEX idx_subscriptions_user (user_id),
    INDEX idx_subscriptions_stripe (stripe_subscription_id),
    INDEX idx_subscriptions_status (status)
) ENGINE=InnoDB;

-- -----------------------------------------------------------
-- Payment Events (idempotency table)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment_events (
    id                BIGINT          AUTO_INCREMENT PRIMARY KEY,
    stripe_event_id   VARCHAR(255)    NOT NULL UNIQUE,
    event_type        VARCHAR(100)    NOT NULL,
    processed_at      TIMESTAMP       NULL,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_payment_events_stripe (stripe_event_id)
) ENGINE=InnoDB;

-- -----------------------------------------------------------
-- Payment Retries
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment_retries (
    id                BIGINT          AUTO_INCREMENT PRIMARY KEY,
    subscription_id   BIGINT          NOT NULL,
    stripe_invoice_id VARCHAR(255)    NOT NULL,
    attempt_count     INT             NOT NULL DEFAULT 1,
    max_attempts      INT             NOT NULL DEFAULT 3,
    next_retry_at     TIMESTAMP       NOT NULL,
    status            ENUM('PENDING', 'SUCCEEDED', 'EXHAUSTED')
                                      NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_retries_subscription
        FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
        ON DELETE RESTRICT,

    INDEX idx_retries_subscription (subscription_id),
    INDEX idx_retries_next (next_retry_at, status),
    INDEX idx_retries_status (status)
) ENGINE=InnoDB;