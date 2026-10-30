
(defn extend-strings [alphabet result]
  (reduce
    (fn [result s]
      (reduce
        (fn [result c]
          (cons (str s c) result)
        )
        result
        (remove #(= % (str (last s))) alphabet)
      )
    )
    '()
    result
  )
)


(defn all-strings [alphabet n]
  (reduce
    (fn [result _] (extend-strings alphabet result))
    '("")
    (range n)))

(println (all-strings '("а" "b") 4))

(println (all-strings '("а" "b" "c") 3))

(println (all-strings '("а" "b" "c") 2))

(println (all-strings '("а" "b" "c") 1))

(println (all-strings '("а" "b" "c") 0))

(println (all-strings '("а") 2))
