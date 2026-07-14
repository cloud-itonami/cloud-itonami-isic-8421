(ns foreign.advisor
  "ForeignConsularAdvisor — proposes a consular or administrative
  operation (intake visa application, schedule consular service, draft
  correspondence, log travel advisory) for a registered applicant/
  correspondent. The advisor is swappable: `mock-advisor` (deterministic,
  default in dev/tests/CI) or `llm-advisor` (wraps a real
  `langchain.model/ChatModel`). Either way the advisor ONLY produces a
  PROPOSAL — it never writes to the store and has no notion of applicant
  provenance or security risk; `foreign.governor` is the independent
  system that decides whether the proposal may proceed, per the itonami
  actor pattern.

  A proposal is a map:
    {:op :intake-visa-application|:schedule-consular-service|:draft-correspondence|:log-travel-advisory
     :effect :propose        ; the advisor NEVER emits a raw store write
     :stake :low|:medium|:high
     :confidence 0.0-1.0
     :rationale str}

  CLOSED ALLOWLIST: the advisor's proposal vocabulary is restricted to
  ADMINISTRATIVE operations ONLY. Proposals touching visa decisions (grant/
  deny), binding diplomatic statements, negotiating positions, or personnel
  command are structurally impossible — the advisor cannot construct them.

  LLM parse failures always yield `:confidence 0.0` (never fabricate
  confidence), which forces the governor to escalate/hold."
  (:require [clojure.string :as str]))

; Closed allowlist: only these operations are permitted
(def permitted-ops #{:intake-visa-application
                      :schedule-consular-service
                      :draft-correspondence
                      :log-travel-advisory})

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer
  "Deterministic mock inference: reads the request's declared op/stake
  straight through (a stand-in for what an LLM would extract from free
  text), with a stake-derived confidence. Enforces closed allowlist:
  only permitted-ops are allowed."
  [_store {:keys [op stake] :as request}]
  (if (contains? permitted-ops op)
    {:op op
     :effect :propose
     :stake (or stake :low)
     :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
     :rationale (str "proposed " (name op) " for applicant " (:applicant-id request))}
    {:op :unknown
     :effect :propose
     :stake :high
     :confidence 0.0
     :rationale "operation not in permitted allowlist (scope boundary)"}))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a foreign consular services advisor. Given a consular or
   administrative request, propose ONLY ONE of these operations:
   :intake-visa-application, :schedule-consular-service,
   :draft-correspondence, :log-travel-advisory.

   STRICTLY FORBIDDEN: any operation that grants/denies a visa, issues
   a binding diplomatic statement, sets a negotiating position, or
   involves personnel deployment/command. If the request implies any of
   these, respond with :confidence 0.0 and :op :unknown.

   These administrative operations NEVER make visa decisions or issue
   diplomatic policy — they support the human decision-makers who do.

   Always provide an honest :confidence (0.0-1.0) and a :stake
   (:low/:medium/:high). Never fabricate confidence you don't have.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)
          op-valid? (and (map? p) (contains? permitted-ops (:op p)))]
      (if op-valid?
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "operation outside permitted allowlist"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  "Wraps a `langchain.model/ChatModel`. `gen-opts` is passed through to
  `model/-generate`. Kept decoupled from any concrete model so this ns
  has no hard dependency beyond `langchain.model`'s protocol."
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "consular request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
