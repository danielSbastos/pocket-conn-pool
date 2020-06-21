(ns pocket-conn-pool.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [thread]]))

(def connection-uri "postgres://twl_dev:@localhost:5432/twl_dev")
(def max-connections 8)

(def available (ref []))
(def in-use (ref []))
(def waiting (ref []))

(defn add-watcher [ref-ref ref-name]
  (add-watch ref-ref :watcher
             (fn [key atom old-state new-state]
               (println "--------" ref-name "-----------")
               (println (.getName (Thread/currentThread)))
               (println "old-state count" (count old-state))
               (println "new-state count" (count new-state)))))

(defn enable-ref-watchers []
  (add-watcher available "available")
  (add-watcher in-use "in-use")
  (add-watcher waiting "waiting"))

(defn jdbc-connection []
  (jdbc/get-connection connection-uri))

(defn create-connection []
  (let [conn (promise)]
    (dosync
     (if (not-empty @available)
       (dosync
        (let [fetched-conn (first @available)]
          (alter available (partial drop 1))
          (alter in-use conj fetched-conn)
          (deliver conn fetched-conn))
        @conn)
       (if (< (count @in-use) max-connections)
         (dosync
          (let [fetched-conn (jdbc-connection)]
            (alter in-use conj fetched-conn)
            (deliver conn fetched-conn))
          @conn)
         (dosync
           (alter waiting conj conn)
           (deref conn 300 :timeout)))))))

(defn close-connection [conn]
  (dosync
   (if (> (count @waiting) 0)
     (dosync
      (alter in-use (partial remove #(= conn %)))
       (let [w-conn (first @waiting)]
         (alter waiting next)
         (deliver w-conn conn)
         (alter in-use conj @w-conn)))
     (dosync
       (alter in-use (partial remove #(= conn %)))
       (alter available conj conn)))))
