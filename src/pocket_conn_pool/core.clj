(ns pocket-conn-pool.core
  (:require [clojure.java.jdbc :as jdbc]))

(def connection-uri "postgres://twl_dev:@localhost:5432/twl_dev")
(def max-connections 4)
(def wait-conn-timeout 200)

(def available (ref []))
(def in-use (ref []))
(def waiting (ref []))

(defn get-in-db [key db-fns]
  ((key db-fns)))

;; ============= Correct version ===============

(defn create-connection [db]
  (let [conn (promise)]
    ((dosync
      (if (not-empty @available)
        (let [fetched-conn (first @available)]
          (alter available (partial drop 1))
          (alter in-use conj conn)
          #(deref (deliver conn @fetched-conn)))
        (if (< (count @in-use) max-connections)
          (do
            (alter in-use conj conn)
            #(deref (deliver conn (get-in-db :connection db))))
          (do
            (alter waiting conj conn)
            #(deref conn wait-conn-timeout :timeout))))))))

(defn close-connection [_db conn]
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

;; ============= Buggy version ===============

(defn create-connection [db]
  (let [conn (promise)]
    (dosync
     (if (not-empty @available)
        (let [fetched-conn (first @available)]
          (alter available (partial drop 1))
          (alter in-use conj fetched-conn)
          (deliver conn fetched-conn)
          @conn)
       (if (< (count @in-use) max-connections)
         (let [fetched-conn (get-in-db :connection db)]
            (alter in-use conj fetched-conn)
            (deliver conn fetched-conn)
            @conn)
         (do
           (alter waiting conj conn)
           (deref conn 800 :timeout)))))))

(defn close-connection [_db conn]
  (dosync
   (if (> (count @waiting) 0)
     (do
      (alter in-use (partial remove #(= conn %)))
       (let [w-conn (first @waiting)]
         (alter waiting next)
         (deliver w-conn conn)
         (alter in-use conj @w-conn)))
     (do
       (alter in-use (partial remove #(= conn %)))
       (alter available conj conn)))))
