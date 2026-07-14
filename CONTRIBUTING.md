# Contributing

## Getting Started

1. Clone the repository and set up your Clojure development environment.
2. Run `clojure -M:test` to confirm tests pass locally.
3. Make your changes and add tests.

## Testing

All changes must include tests. Run:

```bash
clojure -M:test
```

## Scope Boundaries

This actor is designed for **administrative and logistical operations only**. See [README.md](README.md) for explicit scope boundaries. Any proposals or changes that would:

- Add operations touching weapons, munitions, or targeting systems
- Enable personnel deployment, command, or authority decisions
- Access classified or operational military information
- Create or enable lethal autonomous decisions

...are **out of scope** and will be rejected.

## Code Review

All pull requests must:

1. Pass the full test suite
2. Maintain the closed allowlist of permitted operations (see `src/defence/advisor.cljc`)
3. Include updated tests for new functionality
4. Include updated documentation if scope or design changes

## Licensing

All contributions are licensed under AGPL-3.0-or-later (see LICENSE).
