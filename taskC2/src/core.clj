(ns core)

(defn sieve [xs]
  (lazy-seq
    (let [p (first xs)]
      (cons p
        (sieve (remove #(= 0 (mod % p)) (rest xs)))
      )
    )
  )
)

(def primes (sieve (iterate inc 2)))

(println (take 10 primes))

(println (take 3 primes))
