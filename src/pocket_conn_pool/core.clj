(ns pocket-conn-pool.core
  (:require [clojure.java.jdbc :as jdbc]))

(def connection-uri "postgres://twl_dev:@localhost:5432/twl_dev")
(def max-connections 20)
(def wait-conn-timeout 800)

(def available (ref []))
(def in-use (ref []))
(def waiting (ref []))

(defn create-connection []
  ((let [conn (promise)]
     (dosync
      (if (not-empty @available)
        (let [fetched-conn (first @available)]
          (alter available (partial drop 1))
          (alter in-use conj conn)
          #(deref (deliver conn @fetched-conn)))
        (if (< (count @in-use) max-connections)
          (do
            (alter in-use conj conn)
            #(deref (deliver conn (jdbc/get-connection connection-uri))))
          (do
            (alter waiting conj conn)
            #(deref conn wait-conn-timeout :timeout))))))))

(defn close-connection [conn]
  (let [same-conn? #(= conn (deref %))]
    (when (filter same-conn? conn)
      ((dosync
        (alter in-use (partial remove same-conn?))
        (if (not-empty @waiting)
          (let [w-conn (first @waiting)]
            (alter waiting next)
            (alter in-use conj w-conn)
            #(deliver w-conn conn))
          (let [p-conn (promise)]
            (alter available conj p-conn)
            #(deliver p-conn conn))))))))
