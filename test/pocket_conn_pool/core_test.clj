(ns pocket-conn-pool.core-test
  (:require [clojure.test :refer [is]]
            [pocket-conn-pool.core :as core]))

(def close-spec
  {:precondition (fn [state] (some? state))
   :command (fn [state] (core/close-connection (first state)))
   :postcondition (fn [_ _] (identity true))
   :next-state (fn [state result] (next state))})

(def create-spec
  {:precondition (fn [state] (identity true))
   :command (fn [_] (core/create-connection))
   :postcondition (fn [_ result]
                    (is (<= (count @core/in-use) core/max-connections))
                    (is (or (= :timeout result)
                            (= true (instance? org.postgresql.jdbc4.Jdbc4Connection result)))))
   :next-state (fn [state result]
                 (if (not= :timeout result)
                   (conj state result)
                   state))})

(defn execute-spec [spec]
  (fn [key & args]
    (apply (spec key) args)))

(defn run-spec [execute-entry state]
  (when (execute-entry :precondition state)
    (when-let [result (execute-entry :command state)]
      (when (execute-entry :postcondition state result)
        (execute-entry :next-state state result)))))

(defn gen-specs []
  (->> [create-spec close-spec]
       (mapcat #(take (rand-int 50) (repeat %)))
       shuffle))

(defn run-specs [specs]
  (reduce
   (fn [state spec]
     (run-spec (execute-spec spec) state))
   [] ;; initial state
   specs)
  true)

(defn run-in-future [latch specs]
  (future (do
            (deref latch)
            (run-specs specs))))

(defn setup []
  (doseq [pconn (concat [] @core/in-use @core/available)]
    (when-let [conn @pconn] (.close conn)))
  (dosync
   (ref-set core/available [])
   (ref-set core/in-use [])
   (ref-set core/waiting [])))

(defn execute-tests []
  (setup)
  (let [latch (promise)]
    (let [test-futures (->> (range 0 10)
                            (map (fn [_] (run-in-future latch (gen-specs))))
                            (into []))]
          (deliver latch :go!)
          (doseq [f test-futures] (deref f)))))

(execute-tests)
