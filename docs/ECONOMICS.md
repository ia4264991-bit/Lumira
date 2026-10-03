# Vision Economics Architecture

**Status:** Documentation of the agreed conceptual economics model and pre-launch commercial hypotheses. This document does not claim that any economic controls or monetization flows have been implemented.

**Product name:** Vision. The repository, legacy application references, and technical identifiers may still use Lumira; they are not renamed here.

## 1. Purpose and scope

This document records how Vision separates access, operational limits, and compute affordability, and how future Sarah compute costs and credit accounting are intended to work. It does not define new API routes, payment-provider integration, database schema, or an implementation schedule.

For the current MVP, Core Utility means the Card-based study workspace and its in-scope study and collaboration capabilities: Cards, Resources, Notes, Study Sets, Quizzes, Flashcards, and Course Space sharing. Sarah computation is a separately metered capability. This list reflects the current MVP scope; it does not promise that every capability has shipped in the client.

### Decision-register status

The live `docs/DECISIONS.md` contains AD-081, which defines the Summary implementation boundary. It does not currently contain AD-082 or a registered economics decision. The reservation rules below are recorded as `[INVARIANT]` requirements from the agreed economics brief; this document does not assign them a decision number or amend the decision register. They must not be treated as an implemented or separately registered AD-082.

The live root-level `API_CONTRACT.md` defines Sarah's server-authoritative monthly request quota and existing rate-limit behavior. It does not define SCU pricing, credit reservations, or economic ledger endpoints. Nothing here changes that API contract.

## 2. Three independent checks

Every compute operation is subject to three distinct checks:

1. **Capability entitlement** — whether the account or workspace is allowed to request the capability.
2. **Operational quota** — whether the applicable server-side monthly allowance, rate limit, and concurrency capacity permit the operation.
3. **SCU credit affordability** — whether enough unreserved credits are available to reserve the estimated compute cost.

`[INVARIANT]` These checks remain independent. Credits cannot bypass a missing capability entitlement or an exhausted operational quota. Having an entitlement does not grant unlimited compute. A paid offer or positive credit balance does not alter server-authoritative authorization or quota decisions.

## 3. Standard Compute Units (SCUs)

An SCU (Standard Compute Unit) is an internal abstract unit for estimating and accounting for compute resources. It is not a fixed monetary currency and does not itself specify a customer price or provider bill.

`[INVARIANT]` Compute cost is calculated from a versioned operation/resource profile:

> **Operation SCU Cost = ceil(T_in × W_in + T_out × W_out + Vector IOPS × W_vec + Storage IOPS × W_storage)**

`T_in` and `T_out` are the operation's measured input and output token quantities. `Vector IOPS` and `Storage IOPS` are the applicable vector and storage operations. The weights (`W_in`, `W_out`, `W_vec`, and `W_storage`) are parameters from the selected versioned resource profile. The profile version used for an estimate must be identifiable so that later recalibration does not rewrite prior accounting.

`[INVARIANT]` No GHS-denominated constant belongs in this SCU calculation. GHS calibration is a separate commercial comparison only; changing a currency assumption must not silently change the abstract resource formula.

## 4. Reservation, execution, and append-only accounting

The intended economic lifecycle is:

> **Estimate → Reserve → External Execution → Settle/Release**

The accounting model consists of append-only records:

- **CreditGrant** records credits made available to an account.
- **CreditReservation** records credits authorized for a specific operation before external compute begins.
- **CreditConsumption** records the settled SCUs consumed by an operation.
- **CreditRelease** records reserved SCUs that were not consumed and are made available again.

`[INVARIANT]` These records are the accounting facts. Do not introduce mutable authoritative balance columns. Any displayed available balance is derived from grants, settled consumption, and active reservations under the defined accounting rules.

`[INVARIANT]` Settled consumption may never exceed the active reservation unless an explicit additional reservation and re-authorization occurs before the extra work is performed. On successful completion, settle the measured consumption and release any unused reservation. If execution does not proceed or fails before consumption is settled, release the applicable unused reservation.

Provider fallback must either fit entirely within the existing active reservation or pause/fail and obtain explicit additional reservation and re-authorization. Vision must not silently absorb arbitrary provider-cost overages or settle beyond the authorized reservation. A fallback provider is not permission to exceed the user's authorized spend.

The economic brief preserves these HTTP outcomes for future queued compute flows, without defining new routes or response bodies:

- **202 Accepted** when heavy work passes reservation and rate checks and is queued.
- **429 Too Many Requests** when concurrency or rate limits reject execution immediately.

These statements do not add other HTTP semantics and do not claim that a queue, reservation flow, or SCU ledger currently exists.

## 5. MVP monetization model

The agreed model has two tiers only:

### Tier 1 — Core Utility

`[CONFIGURABLE_HYPOTHESIS]` User-facing price: **Free / GHS 0.00**. Free to a student does not mean zero infrastructure cost to operate. Core Utility covers the in-scope Card study workspace and collaboration capabilities described in §1; it is not a promise of unlimited Sarah computation.

### Tier 2 — Sarah Compute Engine

Sarah computation is measured in SCUs and subject to capability entitlement, operational quota, and credit affordability as separate checks. The commercial values below are pre-launch hypotheses, not architectural constants or a launch commitment.

| Offer or calibration | Pre-launch value | Classification |
|---|---:|---|
| Free allocation | 25 SCUs per month | `[CONFIGURABLE_HYPOTHESIS]` |
| Exam Pass | GHS 25 for 350 SCUs over 14 days | `[CONFIGURABLE_HYPOTHESIS]` |
| Semester Pass | GHS 60 for 1,200 SCUs over 120 days | `[CONFIGURABLE_HYPOTHESIS]` |
| Initial baseline variable provider COGS | GHS 0.005 per SCU | `[CONFIGURABLE_HYPOTHESIS]` |

The GHS 0.005 value is a commercial calibration hypothesis only. It is not in the SCU formula, is not a fixed conversion guarantee, and must be recalibrated from observed workloads and provider billing evidence before launch. No additional paid product is defined here.

## 6. Provider prices and evidence

`[EXTERNALLY_SOURCED]` No current provider price is asserted in this document because the repository contains no authoritative, current billing evidence for one. Any provider price used in future costing is an illustrative external benchmark until verified against the provider's live billing configuration. Record its source, retrieval date, currency, units, and applicable service/configuration before relying on it commercially.

Provider prices are not SCU weights or architectural constants. Provider billing configuration and foreign-exchange configuration belong in the operational/commercial calibration process; they do not change the SCU formula by implication.

## 7. Workload model and scenario simulation

The distribution below is a modeled percentile-band workload distribution, not evidence of production use and not a claim that these values came from a log-normal simulation.

| Modeled student band | Students | SCUs each | Band SCUs |
|---|---:|---:|---:|
| Lower-use band | 500 | 243 | 121,500 |
| Middle-use band | 400 | 632 | 252,800 |
| Higher-use band | 90 | 2,222 | 199,980 |
| Highest-use band | 10 | 3,423 | 34,230 |
| **Total** | **1,000** | — | **608,510** |

`[ESTIMATED_WORKLOAD]` These four bands sum to 1,000 students and 608,510 SCUs. They are scenario inputs, not measured telemetry.

### Standard scenario

`[SCENARIO_SIMULATION_RESULT]` The following arithmetic uses the stated pre-launch assumptions. To make the supplied GHS 60,000 revenue input reproducible from the offer table, this scenario treats it as 1,000 Semester Pass purchases at GHS 60 each; it is not a forecast of actual uptake.

| Item | Reproducible calculation | Scenario amount |
|---|---|---:|
| Revenue | 1,000 × GHS 60 Semester Pass | GHS 60,000.00 |
| Variable compute COGS | 608,510 SCUs × GHS 0.005/SCU | GHS 3,042.55 |
| Payment fee | GHS 60,000 × 1.95% | GHS 1,170.00 |
| Fixed infrastructure | Scenario input | GHS 4,500.00 |
| Standard Scenario Operating Contribution | 60,000 − 3,042.55 − 1,170 − 4,500 | GHS 51,287.45 |
| Contribution margin | 51,287.45 ÷ 60,000 × 100 | 85.5% (approximately) |

`[CONFIGURABLE_HYPOTHESIS]` The 1.95% payment-fee assumption and GHS 4,500 fixed-infrastructure assumption are scenario inputs, not verified provider rates or architectural constants.

### 50% exam-surge cost sensitivity

`[SCENARIO_SIMULATION_RESULT]` This sensitivity raises the assumed variable cost per SCU by 50%, from GHS 0.005 to GHS 0.0075, while holding the modeled 608,510 SCUs, GHS 60,000 revenue, payment-fee amount, and fixed-infrastructure amount constant. It is a unit-cost sensitivity, not a claim of 50% more usage.

| Item | Reproducible calculation | Scenario amount |
|---|---|---:|
| Variable compute COGS | 608,510 SCUs × GHS 0.0075/SCU | GHS 4,563.825 |
| Scenario Operating Contribution | 60,000 − 4,563.825 − 1,170 − 4,500 | GHS 49,766.175 (GHS 49,766.18 rounded to cents) |
| Contribution margin | 49,766.175 ÷ 60,000 × 100 | 82.9% (approximately) |

The supplied approximate operating contribution of GHS 49,766.17 truncates the exact GHS 49,766.175 result. Conventional rounding to two decimal places gives GHS 49,766.18. All scenario results are simulations, not forecasts.

## 8. Infrastructure protection

`[INVARIANT]` Economic checks do not weaken server-side authorization, entitlements, operational quotas, or rate/concurrency protections. A compute operation that is queued must first pass its applicable checks and reservation. A request rejected immediately by a rate or concurrency limit uses **429 Too Many Requests**; accepted heavy work that is queued uses **202 Accepted**, as specified in §4.

No additional response codes, endpoint contracts, queue guarantees, retry behavior, or provider-failure semantics are defined here.

## 9. Telemetry and calibration roadmap

1. **Stage 1 — Measure real workloads.** Collect the token and resource-operation measurements needed to understand actual operation profiles.
2. **Stage 2 — Add commercial inputs.** Integrate provider billing/price telemetry and configurable foreign-exchange inputs, with source and effective-date provenance.
3. **Stage 3 — Recalibrate before launch.** Recalibrate operation SCU weights from production evidence and freeze the commercial model for launch review.

Observed workload measurements and external provider prices should remain distinguishable from configurable hypotheses and scenario simulations. Do not present modeled bands or assumed COGS as production evidence.

## 10. Implementation status and boundaries

This is a conceptual economics document. It does not state that entitlements, economic quotas, SCU estimation, credit grants, reservations, settlement, releases, payment integration, or provider billing telemetry exist in the application. It creates no database schema, balance field, API endpoint, provider integration, or implementation decision. The live architecture and API contract remain authoritative for what is currently defined and implemented.
