(ns pocket-conn-pool.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [thread]]))


(def connection-uri "postgres://twl_dev:@localhost:5432/twl_dev")
(def max-connections 2)
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

(defn thread-1 []
  (future
    (let [conn1 (get-connection)
          _conn2 (get-connection)]
      (println "t1 => fetched conns")
      (println "t1 => sleeping 20 secs")
      (Thread/sleep 20000)
      (println "t1 => closed conn1")
      (close-connection conn1))))

(defn thread-2 []
  (future
    (println "t2 => sleeping 10 secs")
    (Thread/sleep 10000)
    (let [conn3 (get-connection)]
      (println "t2 => fetched conn")
      (println (time @conn3))
      (println "t2 => resolved conn"))))

(defn t []
  (do
    (thread-1)
    (thread-2)))
