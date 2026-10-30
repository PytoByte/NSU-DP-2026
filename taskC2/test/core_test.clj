(ns core-test
  (:use core)
  (:require [clojure.test :as test])
)

(test/deftest first-10-primes
  (test/is (= '(2 3 5 7 11 13 17 19 23 29) (take 10 primes)))
)

(test/deftest prime-100
  (test/is (= 25 (count (take-while #(< % 100) primes))))
)

(test/deftest nth-primes
  (test/is (= 2    (nth primes 0)))
  (test/is (= 29   (nth primes 9)))
  (test/is (= 541  (nth primes 99)))
  (test/is (= 7919 (nth primes 999)))
)

(test/deftest first-10-sieve
  (test/is (= '(2 3 5 7 11 13 17 19 23 29) (take 10 (sieve (iterate inc 2)))))
)

(test/deftest sieve-delete-correct
  (test/is (= '(10 11 12 13 14 15 16 17 18 19 21) (take 11 (sieve (iterate inc 10)))))
)
