(ns pocket-conn-pool.core-test
  (:require [clojure.test :refer [is]]
            [clojure.test.check.generators :as gen]
            [pocket-conn-pool.core :as core]))

(def gen-create
  (gen/return :create))

(def gen-close
  (gen/return :close))

(defn commands-per-thread [thread-count]
  (let [gen-commands (concat [] (gen/sample
                                 (gen/one-of [gen-create gen-close])
                                 (* 20 thread-count)))]
    (partition (/ (count gen-commands) thread-count) gen-commands)))

(def timeouts-count (atom 0))
(def state (atom []))

(defn run-close []
  (core/close-connection (first @state))
  (swap! state next))

(defn run-create []
  (let [conn (core/create-connection)]
    (case conn
      :timeout (swap! timeouts-count inc)
      (do
        (is (and
             (<= (count @state) core/max-connections)
             (= (instance? org.postgresql.jdbc4.Jdbc4Connection conn))))
        (swap! state conj conn)))))

(defn run-future [latch commands]
  (future (do
            (deref latch)
            (doseq [cmd commands]
              (case cmd
                :create (run-create)
                :close (run-close))))))

(defn run-futures [latch parallel-size]
  (let [cmds-per-thread (commands-per-thread parallel-size)]
    (let [futures (into [] (map #(run-future latch %) cmds-per-thread))]
      (deliver latch :go!)
      futures)))

(core/enable-ref-watchers)
(is (into [] (map #(deref %) (run-futures (promise) 3))))

;; TODO: add explicit postcondition
;; TODO: calculate total of timeouts
