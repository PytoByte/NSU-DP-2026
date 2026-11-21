(ns core)

;;;an empty route map
;;;it is enough to use either forward or backward part (they correspond to each other including shared reference to number of tickets)
;;;:forward is a map with route start point names as keys and nested map as values
;;;each nested map has route end point names as keys and route descriptor as values
;;;each route descriptor is a map (structure in fact) of the fixed structure where
;;;:price contains ticket price
;;;and :tickets contains reference to tickets number
;;;:backward has the same structure but start and end points are reverted
(def empty-map
  {:forward {},
   :backward {}})

(defn route
  "Add a new route (route) to the given route map
   route-map - route map to modify
   from - name (string) of the start point of the route
   to - name (string) of the end poiunt of the route
   price - ticket price
   tickets-num - number of tickets available"
  [route-map from to price tickets-num]
  (let [tickets (ref tickets-num :validator (fn [state] (>= state 0))),     ;reference for the number of tickets
        orig-source-desc (or (get-in route-map [:forward from]) {}),
        orig-reverse-dest-desc (or (get-in route-map [:backward to]) {}),
        route-desc {:price price,                                            ;route descriptor
                   :tickets tickets},
        source-desc (assoc orig-source-desc to route-desc),
        reverse-dest-desc (assoc orig-reverse-dest-desc from route-desc)]
    (-> route-map
      (assoc-in [:forward from] source-desc)
      (assoc-in [:backward to] reverse-dest-desc))))

;; счётчик (ре)стартов транзакций
(def transact-cnt (atom 0))

(defn book-tickets
  "Tries to book tickets and decrement appropriate references in route-map atomically
   returns map with either :price (for the whole route) and :path (a list of destination names) keys
          or with :error key that indicates that booking is impossible due to lack of tickets"
  [route-map from to]
  (if (= from to)
    {:path '(), :price 0}
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ;;TODO implement me using Dijkstra algorithm
    ;;implementation must be pure functional besides the transaction itself, tickets reference modification and
    ;;restarts monitoring (atom could be used for this)
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    (dosync
      ;; фиксируем каждую попытку транзакции (включая рестарты STM)
      (swap! transact-cnt inc)
      (let [graph (:forward route-map),
            ;; Дейкстра: distances - минимальная цена до вершины, previous - предыдущая вершина, visited - посещённые
            [distances previous]
            (loop [distances {from 0}, previous {}, visited #{}]
              ;; выбираем непосещённую вершину с минимальной текущей ценой
              (let [current (first (sort-by distances (remove visited (keys distances))))]
                (cond
                  (nil? current) [distances previous]
                  (= current to) [distances previous]
                  :else
                  (let [[distances previous]
                        ;; релаксация рёбер: учитываем только маршруты, где есть билеты
                        (reduce (fn [[distances previous] [neighbor route-info]]
                                  (if (pos? @(:tickets route-info))
                                    (let [new-distance (+ (distances current) (:price route-info)),
                                          old-distance (get distances neighbor)]
                                      (if (or (nil? old-distance) (< new-distance old-distance))
                                        [(assoc distances neighbor new-distance)
                                         (assoc previous neighbor current)]
                                        [distances previous]))
                                    [distances previous]))
                                [distances previous]
                                (get graph current))]
                    (recur distances previous (conj visited current))))))]
        (if-not (contains? distances to)
          {:error :no-tickets}
          ;; восстанавливаем путь от from до to (список пунктов назначения по порядку)
          (let [path (loop [current to, path (list to)]
                       (let [prev-node (get previous current)]
                         (if (= prev-node from)
                           path
                           (recur prev-node (conj path prev-node)))))]
            ;; атомарно уменьшаем число билетов на каждом ребре найденного пути
            (doseq [[from-city to-city] (partition 2 1 (cons from path))]
              (alter (:tickets (get-in graph [from-city to-city])) dec))
            {:path path, :price (get distances to)}))))))

;;;cities
(def spec1 (-> empty-map
             (route "City1" "Capital"    200 5)
             (route "Capital" "City1"    250 5)
             (route "City2" "Capital"    200 5)
             (route "Capital" "City2"    250 5)
             (route "City3" "Capital"    300 3)
             (route "Capital" "City3"    400 3)
             (route "City1" "Town1_X"    50 2)
             (route "Town1_X" "City1"    150 2)
             (route "Town1_X" "TownX_2"  50 2)
             (route "TownX_2" "Town1_X"  150 2)
             (route "Town1_X" "TownX_2"  50 2)
             (route "TownX_2" "City2"    50 3)
             (route "City2" "TownX_2"    150 3)
             (route "City2" "Town2_3"    50 2)
             (route "Town2_3" "City2"    150 2)
             (route "Town2_3" "City3"    50 3)
             (route "City3" "Town2_3"    150 2)))

(defn booking-future [route-map from to init-delay loop-delay]
  (future
    (Thread/sleep init-delay)
    (loop [bookings []]
      (Thread/sleep loop-delay)
      (let [booking (book-tickets route-map from to)]
        (if (booking :error)
          bookings
          (recur (conj bookings booking)))))))

(defn print-bookings [name ft]
  (println (str name ":") (count ft) "bookings")
  (doseq [booking ft]
    (println "price:" (booking :price) "path:" (booking :path) )))

(defn run []
  ;;try to tune timeouts in order to all the customers gain at least one booking
  (let [f1 (booking-future spec1 "City1" "City3" 0 10),
        f2 (booking-future spec1 "City1" "City2" 20 10),
        f3 (booking-future spec1 "City2" "City3" 40 10)]
    (print-bookings "City1->City3:" @f1)
    (print-bookings "City1->City2:" @f2)
    (print-bookings "City2->City3:" @f3)
    ;; механизм мониторинга числа (ре)стартов транзакций
    (println "Total (re-)starts:" @transact-cnt)))

(defn -main [& _]
  (run))
