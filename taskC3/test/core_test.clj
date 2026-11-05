(ns core-test
  (:use clojure.test)
  (:require [core :refer [p-filter]])
)

(deftest test-basic
  (is (=
    (filter even? (range 20))
    (p-filter even? (range 20) 10)
  ))
  (is (=
    (filter odd? (range 20))
    (p-filter odd? (range 20) 10)
  ))
)

(deftest test-empty
  (is (= [] (p-filter even? [] 1)))
  (is (= [] (p-filter even? [] 100)))
)

(deftest test-unperfect-n-partition
  (is (= [0 2 4] (p-filter even? [0 1 2 3 4] 100)))
  (is (=
    (filter even? (range 10))
    (p-filter even? (range 10) 3)
  ))
  (is (=
    (filter even? (range 10))
    (p-filter even? (range 10) 1)
  ))
)

(deftest test-infinite-coll-lazy
  (is (= [0 2 4 6 8] (take 5 (p-filter even? (range) 10))))
  (is (= [0 1 2 3 4] (take 5 (p-filter (constantly true) (range) 10))))
  (is (= [] (take 0 (p-filter (constantly false) (range) 10))))
)
