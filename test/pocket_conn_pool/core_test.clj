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

(def state (atom []))

(defn run-close []
  (when-not (empty? @state)
    (core/close-connection (first @state))
    (swap! state next)))

(defn run-create []
  (let [conn (core/create-connection)]
    (when-not (= :timeout conn)
      (is (<= (count @state) core/max-connections))
      (is (= (instance? org.postgresql.jdbc4.Jdbc4Connection conn)))
      (swap! state conj conn))))

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

(defn setup []
  (doseq [conn (concat [] @core/in-use @core/available)]
    (.close conn))
  (dosync
   (alter core/available (fn [_] []))
   (alter core/in-use (fn [_] []))
   (alter core/waiting (fn [_] []))))

(core/enable-ref-watchers)
(dotimes [_ 1]
  (setup)
  (println "running commands")
  (is (into [] (map #(deref %) (run-futures (promise) 2)))))
