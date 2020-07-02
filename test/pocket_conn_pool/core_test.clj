(ns pocket-conn-pool.core-test
  (:require [clojure.test :refer [is]]
            [pocket-conn-pool.core :as core]
            [clojure.test.check.generators :as gen]
            [stateful-check.core :refer [specification-correct?]]))

(def create-conn-specification
  {:command #'core/create-connection
   :next-state (fn [state _ result]
                 (println state)
                 (if (= :timeout result)
                   state
                   (conj state result)))
   :postcondition (fn [prev-state next-state _ result]
                    (let [conn-count (count next-state)]
                      (<= conn-count core/max-connections)))})

(def close-conn-specification
  {:requires (fn [state] (seq state))
   :args (fn [state] [(first state)])
   :command #'core/close-connection
   :next-state (fn [state _ _]
                 (rest state))})

(defn setup []
  (doseq [pconn (concat [] @core/in-use @core/available)]
    (when-let [conn @pconn] (.close conn)))
  (dosync
   (ref-set core/available [])
   (ref-set core/in-use [])
   (ref-set core/waiting [])))

(def conn-spec
  {:commands {:create #'create-conn-specification}
   :setup setup})

(is (specification-correct? conn-spec
                            {:gen {:threads 3}
                             :run {:max-tries 2}}))

