(ns pocket-conn-pool.core-test
  (:require [clojure.test :refer [is]]
            [pocket-conn-pool.core :as core]
            [clojure.test.check.generators :as gen]
            [clojure.java.jdbc :as jdbc]
            [stateful-check.core :refer [specification-correct?]]))

(def create-conn-specification
  {:args (fn [state] [(:db state)])
   :command #'core/create-connection
   :next-state (fn [state _ result]
                 (if (= :timeout result)
                   state
                   (update-in state [:conns] conj result)))
   :postcondition (fn [state _ _ _]
                    (let [pool @(-> state :db :pool)]
                      (<= (count pool) core/max-connections)))})

(def close-conn-specification
  {:requires (fn [state] (and (vector? (:conns state))
                              (not (empty? (:conns state)))))
   :args (fn [state] [(:db state) (first (:conns state))])
   :command #'core/close-connection
   :next-state (fn [state _ _] (update-in state [:conns] next))})

(def pool (atom []))
(defn get-connection []
  (let [conn (jdbc/get-connection core/connection-uri)]
    (swap! pool conj conn)
    conn))

(defn cleanup []
  (doseq [conn @pool]
    (.close conn))
  (reset! pool [])
  (dosync
   (ref-set core/available [])
   (ref-set core/in-use [])
   (ref-set core/waiting [])))

(def initial-state
  {:db {:connection get-connection
        :pool pool}
   :conns []})

(def conn-spec
  {:commands {:create #'create-conn-specification
              :close #'close-conn-specification}
   :cleanup cleanup
   :initial-state #(identity initial-state)})

 (is (specification-correct? conn-spec
                            {:gen {:threads 2
                                   :max-length {:sequential 10
                                                :parallel 5}}
                             :run {:max-tries 2
                                   :num-tests 20}}))
