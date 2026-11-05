(ns core)

(defn p-filter [pred coll n]
  (->> coll
    (partition-all n)
    (partition-all n)
    (mapcat (fn [batch]
      (->> batch
        (map #(future (doall (filter pred %))))
        (doall)
        (map deref)
        (mapcat identity)
      )
    ))
  )
)

; prefomance test
(def coll (range 100))

(defn heavy-pred [x]
  (Thread/sleep 10)
  (not= (mod x 2) 0))

(defn -main [& _]
  (println "sequential")
  (time (doall (filter heavy-pred coll)))
  (println "parallel")
  (time (doall (p-filter heavy-pred coll 10)))
  (shutdown-agents)
)
