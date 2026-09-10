(ns foreign.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [foreign.governor :as governor]
            [foreign.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-applicant! st {:applicant-id "app-1" :name "Alice" :verified-at "2026-01-01"})
    st))

(deftest allows-clean-low-risk-proposal
  (let [st (fresh-store)
        request {:applicant-id "app-1" :op :log-travel-advisory :stake :low}
        proposal {:op :log-travel-advisory :effect :propose :stake :low :confidence 0.95}
        verdict (governor/check request {} proposal st)]
    (is (true? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (false? (:escalate? verdict)))))

(deftest holds-on-unregistered-applicant
  (let [st (fresh-store)
        request {:applicant-id "no-such-app" :op :log-travel-advisory :stake :low}
        proposal {:op :log-travel-advisory :effect :propose :stake :low :confidence 0.95}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))
    (is (some? (first (:violations verdict))))))

(deftest holds-on-non-propose-effect
  (let [st (fresh-store)
        request {:applicant-id "app-1" :op :log-travel-advisory :stake :low}
        proposal {:op :log-travel-advisory :effect :direct-write :stake :low :confidence 0.95}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))))

(deftest escalates-on-visa-application
  (let [st (fresh-store)
        request {:applicant-id "app-1" :op :intake-visa-application :stake :medium}
        proposal {:op :intake-visa-application :effect :propose :stake :medium :confidence 0.85}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (true? (:escalate? verdict)))))

(deftest escalates-on-security-concern
  (let [st (fresh-store)
        request {:applicant-id "app-1" :op :flag-security-concern :stake :high}
        proposal {:op :flag-security-concern :effect :propose :stake :high :confidence 0.8}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (true? (:escalate? verdict)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        request {:applicant-id "app-1" :op :schedule-consular-service :stake :low}
        proposal {:op :schedule-consular-service :effect :propose :stake :low :confidence 0.5}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (true? (:escalate? verdict)))))

(deftest holds-on-out-of-scope-operation
  (let [st (fresh-store)
        request {:applicant-id "app-1" :op :unknown :stake :high}
        proposal {:op :unknown :effect :propose :stake :high :confidence 0.0}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))))
