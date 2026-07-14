# cloud-itonami-isic-8421

Open Occupation Blueprint for **ISIC Rev.5 8421**: Foreign Affairs and Consular Services Administration.

This repository designs a forkable OSS business for a foreign ministry's consular and administrative operations: a document-handling and verification robot performs visa application intake and verification, consular service scheduling, and correspondence drafting under a governor-gated actor, so a foreign ministry keeps its own consular records and audit trail instead of renting a closed visa-processing SaaS.

## IMPORTANT: SCOPE BOUNDARIES

**This actor is EXPLICITLY NOT a diplomat, visa decision-maker, or policy authority.**

### What this actor DOES

- Administrative and consular operations only:
  - Visa application intake and document verification
  - Consular service scheduling (passport renewals, notarizations)
  - Diplomatic correspondence drafting (for human signature)
  - Travel advisory data logging
  - Applicant/correspondent registration and verification
  - Audit trail and compliance documentation

### What this actor DOES NOT (hard boundaries, permanently out of scope)

These operations are **permanently forbidden** — they are not gated by risk level or approval hierarchy, they cannot be escalated for human override, and the actor's proposal vocabulary has no path to construct them. A closed allowlist enforces this at the governance layer:

- **Visa grant/deny decisions** — the actor intakes and verifies documents, but **never** proposes to grant or deny a visa. That decision is exclusively human and outside this actor's vocabulary.
- **Binding diplomatic statements or positions** — the actor may draft correspondence for human review and signature, but never issues a binding statement in the government's name
- **Negotiating positions or policy** — no authority to set or propose negotiating positions, diplomatic strategy, or foreign policy
- **Personnel deployment or command authority** — no notion of embassy staffing, personnel decisions, or command authority

These are not "high-risk operations requiring escalation" — they are entirely outside the actor's design vocabulary. The governor will **permanently :hold** any proposal that touches these categories (it is not a matter of confidence, approval chain, or security threshold).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a document-handling and verification robot performs visa application intake and verification, consular service scheduling, and correspondence drafting under an actor that proposes actions and an independent **Foreign Consular Governor** that gates them. The governor never dispatches a robot action itself; `:medium`/`:high` stake actions (such as visa application intake escalation, or security-concern flagging) require human sign-off.

## Core Contract

```text
visa application + applicant identity + travel data
        |
        v
ForeignConsularAdvisor -> ForeignConsularGovernor -> visa intake, consular scheduling, correspondence draft, or human approval
        |
        v
robot actions (gated) + consular record + audit ledger
```

No automated advice can dispatch a consular action the governor refuses, verify an applicant outside its registered scope, or publish a consular record without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `8421`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/foreign/store.cljc` — `Store` protocol + `MemStore`:
  registered applicants, committed consular records, an append-only audit ledger.
- `src/foreign/advisor.cljc` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a consular operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a committed record, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/foreign/governor.cljc` — `ForeignConsularGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered applicant, a proposal whose `:effect` isn't `:propose`, any
  proposal touching visa decisions/binding statements/policy) always
  route to `:hold`. Escalation invariants (visa application intake,
  security concerns, or low advisor confidence) always route to
  `:request-approval` — an `interrupt-before` node that the graph
  checkpoints and only resumes on explicit human approval
  (`actor/approve!`).
- `src/foreign/actor.cljc` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry).

## License

AGPL-3.0-or-later.
