(ns pocket-conn-pool.core-test
  (:require [clojure.test :refer [is]]
            [pocket-conn-pool.core :as core]))

(def state [])

(def close-spec
  {:precondition (fn [state] (some? state))
   :args (fn [state] (first state))
   :command (fn [conn] (core/close-connection conn))
   :postcondition (fn [_] (identity true))
   :next-state (fn [state result] (next state))})

(def create-spec
  {:precondition (fn [_] (identity true))
   :args (fn [_] nil)
   :command (fn [_] (core/create-connection))
   :postcondition (fn [result]
                    (is (and (<= (count @core/in-use) core/max-connections)
                             (or (= :timeout result)
                                 (= true (instance? org.postgresql.jdbc4.Jdbc4Connection result))))))
   :next-state (fn [state result] (conj state result))})

(defn run-spec! [spec state]
  (when ((spec :precondition) state)
    (when-let [result ((spec :command) ((spec :args) state))]
      (when ((spec :postcondition) result)
        ((:next-state spec) state result)))))

(defn gen-specs [close-spec create-spec]
  (shuffle
   (concat
    (take (rand-int 200) (repeat create-spec))
    (take (rand-int 200) (repeat close-spec)))))

(defn run! [specs]
  (reduce
   (fn [state spec]
     (run-spec! spec state))
   []
   specs)
  true)

(core/enable-ref-watchers)
(is (run! (gen-specs create-spec close-spec)))
