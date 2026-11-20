(ns core)

(def restarts (atom 0))

(defn make-fork []
  (ref {:count 0 :busy false})
)

(defn acquire-forks! [left right]
  (loop []
    (let [res (dosync
      (swap! restarts inc)
      (if (or (:busy @left) (:busy @right))
        :busy
        (do
          (alter left #(-> % (update :count inc) (assoc :busy true)))
          (alter right #(-> % (update :count inc) (assoc :busy true)))
          :ok
        )
      )
    )]
      (swap! restarts dec)
      (if (= res :ok)
        nil
        (recur)
      )
    )
  )
)

(defn release-forks! [left right]
  (dosync
    (swap! restarts inc)
    (alter left assoc :busy false)
    (alter right assoc :busy false)
  )
  (swap! restarts dec)
)

(defn philosopher [left right think-ms eat-ms meals]
  (dotimes [_ meals]
    (Thread/sleep (long think-ms))
    (acquire-forks! left right)
    (Thread/sleep (long eat-ms))
    (release-forks! left right)
  )
)

(defn run-sim [n-philosophers think-ms eat-ms meals]
  (reset! restarts 0)
  (let [
    forks (vec (repeatedly n-philosophers make-fork)),
    threads (map (fn [i]
      (let [
        left (nth forks i),
        right (nth forks (mod (inc i) n-philosophers))
      ]
        (Thread. #(philosopher left right think-ms eat-ms meals)))
      )
      (range n-philosophers)
    ),
    start (System/nanoTime)
  ]
    (doseq [t threads] (.start t))
    (doseq [t threads] (.join t))
    (let [time-ms (/ (double (- (System/nanoTime) start)) 1000000.0)]
      {:philosophers n-philosophers
       :think-ms think-ms
       :eat-ms eat-ms
       :meals meals
       :time-ms time-ms
       :restarts @restarts
       :fork-counts (map (fn [f] (:count @f)) forks)})
  )
)

(defn print-result [label result]
  (println label)
  (println "  philosophers:" (:philosophers result))
  (println "  think-ms:" (:think-ms result) "eat-ms:" (:eat-ms result) "meals:" (:meals result))
  (println "  time-ms:" (:time-ms result))
  (println "  tx-restarts:" (:restarts result))
  (println "  fork-counts:" (:fork-counts result))
  (println))

(defn -main [& args]
  (let [
    n1 (if (>= (count args) 1) (Integer/parseInt (nth args 0)) 5),
    think (if (>= (count args) 2) (Long/parseLong (nth args 1)) 10),
    eat (if (>= (count args) 3) (Long/parseLong (nth args 2)) 10),
    meals (if (>= (count args) 4) (Integer/parseInt (nth args 3)) 20)
  ]
    (println "=== Configurable run ===")
    (print-result "Run" (run-sim n1 think eat meals))

    (println "=== Even philosophers (4) ===")
    (print-result "Even" (run-sim 4 10 10 20))

    (println "=== Odd philosophers (5) ===")
    (print-result "Odd" (run-sim 5 10 10 20))

    (println "=== High contention / try livelock (4, no delays) ===")
    (print-result "Contention" (run-sim 4 0 0 100))
  )
)
