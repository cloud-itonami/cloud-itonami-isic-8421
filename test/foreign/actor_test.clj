(ns foreign.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [foreign.actor :as actor]
            [foreign.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-applicant! st {:applicant-id "app-1" :name "Alice" :verified-at "2026-01-01"})
    st))

(deftest commits-a-clean-low-risk-request
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:applicant-id "app-1" :op :log-travel-advisory :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "app-1"))))))

(deftest holds-on-unregistered-applicant-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:applicant-id "no-such-app" :op :log-travel-advisory :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "no-such-app")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval-for-visa
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; visa application always escalates (governor invariant)
        request {:applicant-id "app-1" :op :intake-visa-application :stake :medium}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "app-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "app-1")))))))

(deftest holds-on-scope-boundary-violation
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; :unknown op (out-of-scope) should hard-hold
        request {:applicant-id "app-1" :op :unknown :stake :high}
        result (actor/run-request! graph request {} "thread-4")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "app-1")))
    (is (= :hold (:disposition (:state result))))))
