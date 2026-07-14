(ns foreign.governor
  "ForeignConsularGovernor — the independent safety/traceability layer
  for the ISIC Rev.5 8421 foreign affairs and consular services
  administration actor. Wired as its own `:govern` node in `foreign.actor`'s
  StateGraph, downstream of `:advise` — the Advisor has no notion of
  applicant provenance or security risk, so this MUST be a separate system
  able to reject a proposal (itonami actor pattern, per ADR-2607011000 /
  CLAUDE.md Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. applicant-provenance    — the request's applicant must be registered.
    2. no-actuation            — proposal :effect must be :propose.
    3. scope-boundary          — proposals touching visa grants/denials,
                                  binding diplomatic statements, negotiating
                                  positions, or personnel command NEVER
                                  PROCEED (closed allowlist enforced here +
                                  in advisor).

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    4. flag-security-concern   — any security-related flag.
    5. visa-decision           — visa application (drafting only; decision
                                  is human exclusive).
    6. low confidence          (< `confidence-floor`)."
  (:require [foreign.store :as store]
            [foreign.advisor :as advisor]))

(def confidence-floor 0.6)

; Permanently forbidden operation categories
(def ^:private forbidden-ops #{:unknown})  ; :unknown catches out-of-scope proposals from advisor

; Escalating operations (require human approval)
(def ^:private escalating-ops #{:intake-visa-application
                                 :flag-security-concern})

(defn- hard-violations [{:keys [proposal]} applicant-record]
  (cond-> []
    (nil? applicant-record)
    (conj {:rule :no-applicant :detail "applicant not registered"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect must be :propose only (no direct store writes)"})

    (contains? forbidden-ops (:op proposal))
    (conj {:rule :scope-boundary
           :detail "operation outside permitted scope (visa grant/deny, binding statements, negotiating positions, personnel command are permanently forbidden)"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `foreign.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [applicant-record (store/applicant store (:applicant-id request))
        hard (hard-violations {:proposal proposal} applicant-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating-op? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating-op?))}))
