(ns pocket-conn-pool.core-test
  (:require [clojure.test :refer [is]]
            [pocket-conn-pool.core :as core]
            [clojure.test.check.generators :as gen]
            [stateful-check.core :refer [specification-correct?]]))

(def create-conn-specification
  {:command #'core/create-connection
   :next-state (fn [state _ result]
                 (when-not (= :timeout result)
                   (conj state result)))
   :postcondition (fn [prev-state next-state _ result]
                    (let [conn-count (count next-state)]
                      (<= conn-count 10)))})

(def close-conn-specification
  {:requires (fn [state] (seq state))
   :args (fn [state] (do (println state) [(first state)]))
   :command #'core/close-connection
   :next-state (fn [state _ _]
                 (rest state))})

(def conn-spec
  {:commands {:create #'create-conn-specification
              :close #'close-conn-specification}})

(is (specification-correct? conn-spec
                            {:gen {:threads 2}
                             :run {:max-tries 2}}))
