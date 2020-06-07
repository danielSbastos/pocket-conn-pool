(ns pocket-conn-pool.core
  (:require [clojure.java.jdbc :as jdbc]))

(def connection-uri "postgres://twl_dev:@localhost:5432/twl_dev")
(def max-connections 10)
;; TODO: Can this be implemented in a deque? Do we need a "remove"? Can't it be a FIFO queue?
(def available-conns (atom ()))
(def alive-conns (atom ()))

;; =========================

(defn remove-alive-conns [conn]
  (first (swap! alive-conns (partial remove #(= conn %)))))

(defn pop-available-conns []
  (first (swap! available-conns (partial drop 1))))

(defn add-available-conns [conn]
  (first (swap! available-conns conj conn)))

(defn add-alive-conns [conn]
  (first (swap! alive-conns conj conn)))

(defn known-conn? [conn]
  (not (empty? (filter #(= conn %) @alive-conns))))

;; ========================

(defn get-connection []
  (if (not-empty @available-conns)
    (let [conn (pop-available-conns)]
      (add-alive-conns conn))
    (if (< (count @alive-conns) max-connections)
      (let [conn (jdbc/get-connection connection-uri)]
        (add-alive-conns conn))
      (throw (Exception. "Max connections reached")))))

(defn close-connection [conn]
  (if (known-conn? conn)
    (do
      (add-available-conns conn)
      (remove-alive-conns conn))
    (throw (Exception. "Cannot remove unknown connection"))))

;; ============================
;; NEXT: Add option for waiting conn queue
;; NEXT: Add option for timeout for waiting in conn queue

(def test
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
  (close-connection conn11))
