(ns core)

(defn sieve [xs]
  (lazy-seq
    (cons (first xs)
      (sieve (remove #(= 0 (mod % (first xs))) (rest xs)))
    )
  )
)

(def primes (sieve (iterate inc 2)))

(println (take 10 primes))

(println (take 3 primes))
