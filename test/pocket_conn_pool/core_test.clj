(ns pocket-conn-pool.core-test
  (:require [pocket-conn-pool.core :as core]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop #?@(:cljs [:include-macros true])]))

(def gen-get
  (gen/tuple (gen/return :get) (gen/choose 0 20)))

(defn gen-close [key]
  (gen/tuple (gen/return :close) (gen/return key)))

(def gen-ops
  (gen/let [conn gen-get]
    (gen/vector
     (gen/one-of [gen-get (gen-close conn)]))))

(defn conn-run [ops]
  (doseq [[op val] ops]
    (case op
      :get "todo"
      :close "todo")))
