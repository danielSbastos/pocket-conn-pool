(ns pocket-conn-pool.core
  (:require [clojure.java.jdbc :as jdbc]))

(def connection-uri "postgres://twl_dev:@localhost:5432/twl_dev")
(def max-connections 3)
(def available (ref []))
(def in-use (ref []))
(def waiting (ref []))

(defn get-connection []
  (let [conn (promise)]
    (if (not-empty @available)
      (dosync
       (let [fetched-conn (first @available)]
         (alter available (partial drop 1))
         (alter in-use conj fetched-conn)
         (deliver conn fetched-conn))
       conn)
      (if (< (count @in-use) max-connections)
        (dosync
         (let [fetched-conn (jdbc/get-connection connection-uri)]
           (alter in-use conj fetched-conn)
           (deliver conn fetched-conn))
         conn)
        (dosync
         (alter waiting conj conn)
         conn)))))

(defn close-connection [pconn]
  (if (> (count @waiting) 0)
    (dosync
     (alter in-use (partial remove #(= @pconn %)))
     (let [w-conn (first @waiting)]
       (alter waiting (partial drop 1))
       (alter in-use conj w-conn)
       (deliver w-conn @pconn)))
    (dosync
      (alter in-use (partial remove #(= @pconn %)))
      (alter available conj @pconn))))

(count @in-use)
(count @available)
(count @waiting)
(def conn1 (get-connection))
(def conn2 (get-connection))
(def conn3 (get-connection))
(def conn4 (get-connection))
(def conn5 (get-connection))
(def conn6 (get-connection))
(def conn7 (get-connection))
(def conn8 (get-connection))
(def conn9 (get-connection))
(def conn10 (get-connection))
(def conn11 (get-connection))
(close-connection conn1)
(close-connection conn2)
(close-connection conn3)
(close-connection conn4)
(close-connection conn5)
(close-connection conn6)
(close-connection conn7)
(close-connection conn8)
(close-connection conn9)
(close-connection conn10)
(close-connection conn11)
