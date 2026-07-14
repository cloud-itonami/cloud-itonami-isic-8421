# ADR 0001: Foreign Consular Services Actor Architecture

## Status
Accepted

## Context

ISIC Rev.5 8421 (Foreign Affairs and Consular Services) is the administrative side of a foreign ministry's diplomatic operations. This is distinct from diplomatic negotiation, visa decisions, binding policy statements, or personnel command authority. The challenge is designing an actor that supports **authentic** consular administration (visa application intake, service scheduling, correspondence drafting, travel advisory logging) while enforcing **absolute scope boundaries** on what operations are permitted.

The threat model:
- **Unintended scope creep**: a well-intentioned actor designed for "visa administration" can drift into visa grant/deny decisions if the governance layer is not explicit.
- **LLM drift**: an LLM-backed advisor can hallucinate operations outside its intended scope (e.g., "grant this visa application" or "issue a binding diplomatic statement").
- **Human override**: even with escalation, a human operator under time pressure might approve a proposal at the boundary.

The solution is a **closed allowlist** of permitted operations, enforced in both the Advisor and Governor layers, with hard invariants that make some proposals structurally impossible to construct.

## Decision

Implement the Foreign Consular Services Actor as:

### 1. Closed Allowlist (Advisor Layer)

The `foreign.advisor` namespace restricts its proposal vocabulary to exactly these operations:
- `:intake-visa-application` — visa application intake and document verification
- `:schedule-consular-service` — consular service scheduling (passport renewals, notarizations)
- `:draft-correspondence` — diplomatic correspondence drafting
- `:log-travel-advisory` — travel advisory data logging

An LLM advisor is instructed to respond with `:op :unknown` and `:confidence 0.0` if the request implies any out-of-scope operation. A mock advisor validates that the op is in the permitted set and rejects it with zero confidence if not. Parsing failures also yield `:confidence 0.0`.

### 2. Hard Invariants (Governor Layer)

The `foreign.governor/check` function enforces three classes of hard violations that always route to `:hold` (no approval path):

1. **Applicant provenance**: The applicant must be registered in the store. An unregistered applicant is an immediate, non-overridable block.

2. **No direct actuation**: The proposal's `:effect` must be `:propose` only. Any proposal claiming `:effect :direct-write` or attempting to mutate the store directly is held.

3. **Scope boundary (permanent)**: Any proposal with `:op :unknown` (a catch-all for out-of-scope requests from the advisor) is immediately held. This is the structural gate: the advisor's closed allowlist + zero-confidence response to out-of-scope requests + the governor's rejection of `:unknown` ops create a two-layer enforcement.

Hard violations are **non-overridable**. There is no escalation path, no human approval route, and no threshold above which they are waived. If a proposal violates a hard invariant, it permanently `:hold`s.

### 3. Escalation Invariants (Human Sign-Off)

Operations that are **not** hard violations but carry higher risk require explicit human approval:

1. **Visa application intake** (`:op :intake-visa-application`) — the advisor proposes to **intake and verify documents**, NOT to grant or deny. The human visa decision-maker reviews the intake, verified documents, and supporting analysis, then makes the final grant/deny decision outside this actor's scope. Escalation ensures human oversight of all visa cases.

2. **Security concerns** (`:op :flag-security-concern`) — any proposal flagged as security-related always escalates for human judgment.

3. **Low advisor confidence** — proposals with `confidence < 0.6` are escalated regardless of operation type. This forces human judgment when the LLM is uncertain.

Escalation routes to `:request-approval`, an `interrupt-before` node that checkpoints the graph. Resume (human approval) is explicit: `(actor/approve! graph thread-id)` advances to `:commit`. This is the human-in-the-loop boundary: the operator sees the proposal, rationale, and risk assessment and decides whether to proceed.

### 4. SCOPE EXCLUSIONS (What This Actor Does NOT Do)

These operations are **permanently forbidden** — they are not gated by risk level or approval hierarchy, they cannot be escalated for human override, and the actor's proposal vocabulary has no path to construct them:

- **Visa grant/deny decisions** — the actor intakes and verifies documents, but **never** proposes to grant or deny a visa. That decision is exclusively human and outside this actor's vocabulary.
- **Binding diplomatic statements** — the actor drafts correspondence for human review and signature, but **never** issues a binding statement in the government's name.
- **Negotiating positions or policy** — the actor may log travel advisories or support briefing documents, but **never** sets or proposes a negotiating position.
- **Personnel deployment or command** — the actor has no notion of embassy staffing, personnel authority, or command decisions.

These are not "high-risk operations requiring escalation" — they are entirely outside the actor's design vocabulary. The governor will **permanently :hold** any proposal that touches these categories (it is not a matter of confidence, approval chain, or budget threshold).

### 5. Closed Allowlist Rationale

A closed allowlist ("`only` these ops are allowed") is stronger than a denylist ("`never` these ops"). A denylist is fragile: if the advisor design omits a forbidden op from the denylist, the actor can drift into new scope. A closed allowlist forces the actor design to be explicit about every permitted operation, and any new operation requires deliberate design review and testing.

Example: if the architect listed forbidden ops as `#{:grant-visa :deny-visa}`, an LLM advisor trained on general text might propose `:authorize-visa-issuance` or `:set-visa-quota`, operations that were never explicitly forbidden and thus would slip through. With a closed allowlist, any op outside `#{:intake-visa-application :schedule-consular-service :draft-correspondence :log-travel-advisory}` is structurally rejected at the governance layer.

## Consequences

### Positive

- **No scope drift**: the allowlist is explicit and must be reviewed to expand.
- **LLM safety**: even if an LLM advisor hallucinates an out-of-scope operation (visa grant, binding statement), the advisor layer catches it with zero confidence, and the governor holds it.
- **Clear human responsibility**: visa decisions and diplomatic authority remain exclusively human. This actor supports the decision-maker, not replacing them.
- **Auditability**: every held proposal leaves a ledger entry. A human audit can review the hard holds and confirm that no visa decisions, binding statements, or policy proposals ever reached a human reviewer.

### Negative

- **Strictness**: legitimate new operations require design review and code change. Operators cannot expand scope dynamically via config (this is a feature for safety, not a bug).
- **LLM instructions**: the system prompt and advisor logic must carefully encode the allowlist. If the LLM is updated, the instructions must be updated in parallel.

## Implementation Details

### Store Protocol

```clojure
(defprotocol Store
  (applicant [s applicant-id])           ; retrieve registered applicant
  (records-of [s applicant-id])          ; all consular/admin records for an applicant
  (ledger [s])                           ; append-only audit trail
  (register-applicant! [s applicant])    ; register an applicant
  (commit-record! [s record])            ; commit a consular/admin record
  (append-ledger! [s fact]))             ; append a ledger entry
```

### Advisor Interface

```clojure
(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

; Proposal:
{:op :intake-visa-application|:schedule-consular-service|:draft-correspondence|:log-travel-advisory
 :effect :propose
 :stake :low|:medium|:high
 :confidence 0.0-1.0
 :rationale "explanation"}

; Out-of-scope:
{:op :unknown
 :effect :propose
 :confidence 0.0
 :stake :high
 :rationale "operation not in permitted allowlist (scope boundary)"}
```

### Governor Verdict

```clojure
{:ok? bool              ; true if hard + escalation checks pass
 :violations [...]      ; list of hard violations (if any)
 :confidence n          ; advisor confidence (for audit)
 :hard? bool            ; true if any hard violation
 :escalate? bool}       ; true if escalation invariant triggered
```

### StateGraph

```
:intake -> :advise -> :govern -> :decide -+-> :commit           (:ok? true)
                                           +-> :request-approval  (:escalate? true, interrupt-before)
                                           +-> :hold              (:hard? true)
```

## Testing

Three tiers of tests:

1. **Governor tests** (`test/foreign/governor_test.clj`):
   - Hard violations (unregistered applicant, no-actuation, scope-boundary) always `:hard? true`
   - Escalation invariants (visa intake, security concerns, low confidence) always `:escalate? true`
   - Clean proposals (registered applicant, low-risk op, high confidence) pass through with `:ok? true`

2. **Actor/Graph tests** (`test/foreign/actor_test.clj`):
   - A clean request commits and records immediately
   - An unregistered-applicant request holds without committing
   - An escalated request (visa intake) interrupts, then commits after `approve!`
   - A scope-boundary violation (`:op :unknown`) holds hard

3. **Ledger/audit tests**:
   - Every proposal (commit or hold) leaves a ledger entry
   - Ledger is append-only and ordered by time of decision

## References

- ADR-2607011000: itonami Actor Pattern (langgraph StateGraph, Governor, Advisor separation)
- CLAUDE.md: Actors section (itonami pattern spec)
- `cloud-itonami-isic-8422`: Reference implementation (Defence Procurement actor, similar pattern)
- `cloud-itonami-isic-3510`: Reference implementation (Grid Transmission actor)
