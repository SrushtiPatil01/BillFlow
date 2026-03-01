# BillFlow

Subscription billing backend with Stripe integration, idempotent webhook processing, and automated payment retries.

---

## What This Project Does

Users subscribe to a plan through Stripe Checkout. Stripe handles the payment and sends webhook notifications back to BillFlow whenever something happens: a payment succeeds, a payment fails, or a subscription is cancelled.

BillFlow receives these webhooks, publishes them to Kafka, and returns `200 OK` to Stripe immediately. A Kafka consumer then processes each event: activating subscriptions, scheduling retries for failed payments, or cancelling subscriptions.

The hard problems this solves:

- **Duplicate webhooks** — Stripe retries delivery if it doesn't get a response fast enough. Without protection, the same payment event could activate a subscription twice. BillFlow stores every processed event ID in a MySQL idempotency table and skips duplicates.
- **Failed payments** — When a charge fails, BillFlow doesn't just give up. It retries on a 1-day / 3-day / 7-day schedule. If all retries fail, the subscription moves to `PAST_DUE`.
- **Invalid state changes** — A cancelled subscription can't magically become active again. BillFlow enforces a state machine that only allows valid transitions and rejects everything else.
- **Invoice storage** — When a payment succeeds, the invoice PDF is downloaded from Stripe, uploaded to Google Cloud Storage, and served to users via signed URLs that expire after 15 minutes.

---

## Architecture

<img width="1841" height="1115" alt="image" src="https://github.com/user-attachments/assets/6178d268-fce2-43be-8403-236b3158ba8a" />


**How the pieces connect:**

| Flow | Path |
|------|------|
| **Subscribe** | User → API → Stripe Checkout → returns checkout URL |
| **Webhook** | Stripe → API → check idempotency → publish to Kafka → return `200 OK` |
| **Process Event** | Kafka → Billing Service → update subscription state in MySQL |
| **Retry Payment** | Scheduler → query `payment_retries` table → call Stripe → update state |
| **Invoice** | Billing Service → download PDF from Stripe → upload to GCS |
| **View Invoice** | User → API → generate signed GCS URL → redirect to PDF |

---

## Subscription State Machine

```
PENDING ──────▶ ACTIVE ──────▶ PAST_DUE
               (payment ok)   (payment fails after 3 retries)
                  │                │
                  │                ├── retry succeeds ──▶ ACTIVE
                  │                │
                  ▼                ▼
              CANCELLED        CANCELLED
```

Only valid transitions are allowed. Invalid transitions (e.g., `CANCELLED → ACTIVE`, `PENDING → PAST_DUE`) throw an exception.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Java 17 |
| **Backend** | Spring MVC, Hibernate ORM |
| **Database** | MySQL |
| **Messaging** | Apache Kafka |
| **Payments** | Stripe API |
| **Storage** | Google Cloud Storage |
| **Infrastructure** | Docker Compose |

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/users` | Create a new user |
| `POST` | `/subscribe` | Create Stripe Checkout session, return checkout URL |
| `GET` | `/subscriptions/:id` | Get subscription status |
| `POST` | `/subscriptions/:id/cancel` | Cancel subscription |
| `GET` | `/invoices/:id` | Get signed GCS URL for invoice PDF |
| `POST` | `/webhooks/stripe` | Receive Stripe webhook events |

---

