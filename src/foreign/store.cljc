(ns foreign.store
  "SSoT for the ISIC Rev.5 8421 foreign affairs and consular services
  administration actor. Store is a protocol injected into the
  `foreign.actor` StateGraph — `MemStore` is the default, deterministic,
  zero-dep backend; a Datomic/kotoba-server-backed implementation can be
  swapped in without touching the actor or governor (itonami actor
  pattern, per ADR-2607011000 / CLAUDE.md's Actors section).

  Domain:

    applicant     — a registered applicant/correspondent (:applicant-id,
                    :name, :verified-at)
    record        — a committed consular or administrative operating record
                    (visa application intake, consular service scheduling,
                    correspondence draft, travel advisory log) — written
                    ONLY via commit-record!, never mutated in place
    ledger        — an append-only audit trail of every proposal/verdict/
                    disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (applicant [s applicant-id])
  (records-of [s applicant-id])
  (ledger [s])
  (register-applicant! [s applicant])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (applicant [_ applicant-id] (get-in @a [:applicants applicant-id]))
  (records-of [_ applicant-id] (filter #(= applicant-id (:applicant-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-applicant! [s applicant]
    (swap! a assoc-in [:applicants (:applicant-id applicant)] applicant) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:applicants {} :records [] :ledger []} seed)))))
