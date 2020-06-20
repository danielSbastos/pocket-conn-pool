(ns pocket-conn-pool.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [thread]]))

(def connection-uri "postgres://twl_dev:@localhost:5432/twl_dev")
(def max-connections 10)
(def available (ref []))
(def in-use (ref []))
(def waiting (ref []))

(defn create-connection []
  (let [conn (promise)]
    (if (not-empty @available)
      (dosync
       (let [fetched-conn (first @available)]
         (alter available (partial drop 1))
         (alter in-use conj fetched-conn)
         (deliver conn fetched-conn))
       @conn)
      (if (< (count @in-use) max-connections)
        (dosync
         (let [fetched-conn (jdbc/get-connection connection-uri)]
           (alter in-use conj fetched-conn)
           (deliver conn fetched-conn))
         @conn)
        (dosync
         (alter waiting conj conn)
         (deref conn 300 :timeout))))))

(defn close-connection [conn]
  (if (> (count @waiting) 0)
    (dosync
     (alter in-use (partial remove #(= conn %)))
     (let [w-conn (first @waiting)]
       (alter waiting (partial drop 1))
       (alter in-use conj w-conn)
       (deliver w-conn conn)))
    (dosync
     (alter in-use (partial remove #(= conn %)))
     (alter available conj conn))))
