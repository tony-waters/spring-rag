# Refund Policy

This document describes how refund eligibility is determined. It is intended as a knowledge
base for support agents and AI assistants answering refund questions.

**Scope note:** this policy determines whether an order *qualifies* for a refund. It does not
cover how a refund payment is actually issued once approved.

## Summary

An order is eligible for a refund only if **all** of the following are true:

1. A valid customer and a valid order are identified.
2. The order belongs to that customer.
3. The customer's account is active.
4. The customer is not on the blocked list (a customer on the watch list is still eligible,
   but the case should be flagged for manual review before the refund is issued).
5. The order has not already been refunded.
6. The order has been delivered.
7. The order has a recorded delivery date.
8. The order was delivered within the refund window (default: **30 days** before the current
   date).

If any rule fails, the order is **ineligible**. Rules are generally checked in the order
above, and the first rule that fails is the one reported as the reason for ineligibility —
later rules are not evaluated once an earlier one has already failed.

If every rule passes, the order is **eligible**, and the refundable amount is the full order
total, in the order's original currency.

## Eligibility rules, in order

| # | Rule | Condition to pass | Reason reported if it fails |
|---|------|--------------------|------------------------------|
| 1 | Identification required | Both a customer and an order are specified | "Customer and order are required." |
| 2 | Customer must exist | The customer can be found | "Customer was not found." |
| 3 | Order must exist | The order can be found | "Order was not found." |
| 4 | Order must belong to customer | The order was placed by the customer in question | "Order does not belong to the selected customer." |
| 5 | Customer must be active | The customer's account status is active | "Customer is not active." |
| 6 | Customer must not be blocked | The customer's risk level is not "blocked" | "Customer risk level blocks this refund." |
| 7 | No existing refund | The order has not already been refunded | "Order has already been refunded." |
| 8 | Order must be delivered | The order's status is "delivered" | "Order must be delivered before a refund can be considered." |
| 9 | Delivery date must be known | A delivery date is recorded for the order | "Order has no recorded delivery date." |
| 10 | Within refund window | Delivery date falls within the refund window | "Order was delivered outside the refund window." |

If all rules pass, the reason reported is: *"Order was delivered within the refund window and
has no previous refund."*

## Customer standing

Two aspects of a customer's standing affect refund eligibility:

- **Account status** — must be active. A suspended, closed, or otherwise inactive account
  blocks the refund outright, regardless of the order itself.
- **Risk level** — one of:
  - **Normal** — no effect on eligibility.
  - **Watch** — does **not** block eligibility, but the refund should be flagged for human
    review before it is drafted or issued. Treat a watch-list refund as "eligible, with
    caution," not as a fully automatic approval.
  - **Blocked** — blocks the refund entirely. A blocked customer cannot receive a refund
    through this process under any circumstances.

## Order status and timing

- The order must currently show as **delivered**. Orders that are still pending, in transit,
  cancelled, or already refunded do not qualify.
- The order must have a known delivery date. An order marked delivered without a recorded
  delivery date is treated as incomplete data and is blocked rather than assumed eligible.
- **Refund window**: the order must have been delivered within the refund window, measured
  backwards from the current date. The default window is **30 days**. A delivery date exactly
  at the boundary of the window still counts as within it; only a delivery date strictly
  before the cutoff fails this rule.
- Once an order has been refunded, it cannot become eligible again — a refund decision is not
  reversible or re-grantable under this policy.

## Refund amount and currency

- The refundable amount, when eligible, is the **full order total**, in the order's original
  currency.
- This policy does not support partial refunds, restocking fees, or discretionary amount
  reductions — eligibility is all-or-nothing for the full order value.
- When an order is ineligible, no refundable amount applies.

## What this policy does not cover

- How the refund payment itself is issued or reversed once a refund is approved.
- Partial refunds or discretionary amount adjustments.
- A formal override or exception-approval process — a watch-list customer is flagged for
  human review, but there is no automated escalation path beyond that flag.
- Whether the 30-day window applies uniformly across all regions, products, or promotions —
  confirm current settings before treating the window length as fixed if this matters for a
  specific case.

## Example scenarios

| Situation | Outcome |
|-----------|---------|
| Active, normal-risk customer; order delivered recently; not previously refunded | **Eligible** — full order total refundable |
| Active, normal-risk customer; order already shows a refund on record | **Ineligible** — "Order has already been refunded." |
| Active, normal-risk customer; order delivered well over 30 days ago | **Ineligible** — "Order was delivered outside the refund window." |
| Blocked customer; order otherwise meets every other rule | **Ineligible** — "Customer risk level blocks this refund." |
| Watch-list customer; order otherwise meets every other rule | **Eligible**, but flagged for manual review before the refund is drafted |

## Data model reference

Refund eligibility is evaluated against two tables:

**`customers`**

| Column | Meaning |
|--------|---------|
| `id` | Unique customer identifier |
| `name` | Customer's name |
| `email` | Customer's email address |
| `status` | Account status (e.g. `active`); must be `active` for a refund to proceed |
| `risk_level` | `normal`, `watch`, or `blocked` — see "Customer standing" above |

**`customer_orders`**

| Column | Meaning |
|--------|---------|
| `id` | Unique order identifier |
| `customer_id` | The customer who placed the order (foreign key to `customers.id`) |
| `placed_at` | Date/time the order was placed |
| `delivered_at` | Date/time the order was delivered; null if not yet delivered — this is the field the refund window is measured from |
| `status` | Order status (e.g. `delivered`); must be `delivered` for a refund to proceed |
| `total_minor` | Order total in minor currency units (e.g. pence/cents); this is the refundable amount when eligible |
| `currency` | ISO 4217 currency code for the order total (e.g. `GBP`) |
| `refunded_at` | Date/time a refund was recorded against this order; null means no refund has been issued yet |

An order is only eligible when `customer_orders.customer_id` matches the customer's `id`,
`customers.status` is `active`, `customers.risk_level` is not `blocked`, `refunded_at` is
null, `status` is `delivered`, `delivered_at` is not null, and `delivered_at` falls within the
refund window measured against the current date.

## Guidance for answering refund questions

When answering a refund question, resolve the customer and the order first, then apply the
rules above in order. Report both the overall verdict (eligible / ineligible) and the specific
rule that determined it — not just a yes/no answer. If the customer is on the watch list,
make clear in the answer that the case still needs human review even though it is "eligible."
