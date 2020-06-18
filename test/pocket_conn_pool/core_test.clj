(ns pocket-conn-pool.core-test
  (:require [clojure.test :refer [is]]
            [pocket-conn-pool.core :as core]
            [clojure.test.check.generators :as gen]
            [stateful-check.core :refer [specification-correct?]]))


(def create-conn-specification
  {:command #'core/create-connection
   :next-state (fn [state _ result] (conj state result))
   :postcondition (fn [prev-state next-state _ result]
                    (if (<= (count next-state) 10)
                      (do
                        (< (count prev-state) (count next-state))
                        (= (true (realized? result))))
                      true))})

(def close-conn-specification
  {:requires (fn [state] (seq state))
   :args (fn [state] [(first state)])
   :command #'core/close-connection
   :next-state (fn [state _ _]
                 (rest state))
   :postcondition (fn [prev-state current-state _ result]
                    (> (count prev-state) (count current-state)))})

(def conn-spec
  {:commands {:create #'create-conn-specification
              :close #'close-conn-specification}})

(is (specification-correct? conn-spec
                            {:gen {:threads 1}
                             :run {:max-tries 2}}))
