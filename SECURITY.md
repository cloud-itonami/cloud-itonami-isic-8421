# Security Policy

## Scope Boundaries

This actor is designed for defence procurement and logistics **administration only**. It has no capability to:

- Authorize or execute weapons procurement
- Deploy or command personnel
- Access operational military information
- Execute lethal or kinetic actions

Any attempt to use this actor for those purposes is a **misuse** of the system, not a security vulnerability in this code.

## Reporting Security Issues

If you discover a security vulnerability in the actor's governance or design:

1. **Do not open a public issue.** Email the maintainers privately with details.
2. Include a clear description of the vulnerability and any proof-of-concept.
3. Allow reasonable time for a fix before public disclosure.

## Hard Invariants

The actor enforces three permanent, non-overridable hard invariants:

1. **Vendor registration**: proposals from unregistered vendors are always held.
2. **No direct actuation**: proposals claiming direct store writes are always held.
3. **Scope boundary**: proposals touching weapons, personnel, classified operations, or lethal decisions are always held.

A "hard hold" means the proposal **cannot proceed**, no matter the confidence level or human approval chain. This is not a feature gate — it is a design invariant.

## Audit Trail

All proposals (committed or held) leave an append-only audit trail. Regular audit of the ledger can confirm:

- No out-of-scope proposals reached human approval
- All hard holds were applied consistently
- The allowlist of permitted operations has not drifted

## Testing

Security-critical code (Governor, Advisor, allowlist validation) is covered by tests in:

- `test/defence/governor_test.clj`
- `test/defence/actor_test.clj`

All changes to the Governor or permitted-operations allowlist must include tests.
