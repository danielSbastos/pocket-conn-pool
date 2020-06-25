(ns pocket-conn-pool.core-test
  (:require [clojure.test :refer [is]]
            [pocket-conn-pool.core :as core]))

(def state [])

(def close-spec
  {:precondition (fn [state] (some? state))
   :command (fn [state] (core/close-connection (first state)))
   :postcondition (fn [_ _] (identity true))
   :next-state (fn [state result] (next state))})

(def create-spec
  {:precondition (fn [_] (identity true))
   :command (fn [_] (core/create-connection))
   :postcondition (fn [_ result]
                    (is (and (<= (count @core/in-use) core/max-connections)
                             (or (= :timeout result)
                                 (= true (instance? org.postgresql.jdbc4.Jdbc4Connection result))))))
   :next-state (fn [state result]
                 (when (not= :timeout result) (conj state result)))})

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
       (mapcat #(take (rand-int 100) (repeat %)))
       shuffle))

(defn run-specs [specs]
  (reduce
   (fn [state spec]
     (run-spec (execute-spec spec) state))
   []
   specs)
  true)

(defn run-in-future [latch specs]
  (future (do
            (deref latch)
            (run-specs specs))))

(defn setup []
  (doseq [conn (concat [] @core/in-use @core/available)]
    (.close conn))
  (dosync
   (alter core/available (fn [_] []))
   (alter core/in-use (fn [_] []))
   (alter core/waiting (fn [_] []))))

(defn execute-tests []
  (let [latch (promise)]
    (setup)
    (let [test-futures (->> (range 0 1)
                            (map (fn [_] (run-in-future latch (gen-specs))))
                            (into []))]
          (deliver latch :go!)
          (doseq [f test-futures]
            (deref f)))))

(core/enable-ref-watchers)
(execute-tests)
