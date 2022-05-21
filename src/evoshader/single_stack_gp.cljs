(ns evoshader.single-stack-gp
  (:require [clojure.set :refer [union]]))

(defn lit-num-generator [factor]
  (fn []
    (* (rand-nth [1 -1])
       factor
       (Math/log (rand)))))

(defn environment-distribution
  [ops &
   [{:keys [op-weights literal-chance literal-generator]
     :or {op-weights {}
          literal-chance 0.15
          literal-generator (lit-num-generator 1)}}]]
  (let [ops (vec ops)
        weights (mapv (fn [op]
                        (or (op-weights op)
                            1))
                      ops)
        probs (mapv #(/ % (apply + weights))
                    weights)
        rand-op (fn []
                  (loop [index 0
                         choice (rand)]
                    (let [new-choice (- choice (probs index))]
                      (if (<= new-choice 0)
                        (ops index)
                        (recur (inc index)
                               new-choice)))))]
    (fn []
      (if (< (rand) literal-chance)
        (literal-generator)
        (rand-op)))))

(def default-distribution
  (environment-distribution [:+ :- :* :/]
                            {:op-weights
                             {:+ 3
                              :* 3}}))

(defn rand-program [& [distribution max-size]]
  (let [max-size (or max-size 30)]
    (repeatedly (rand-int max-size)
                (or distribution default-distribution))))

(defn uniform-addition [initial-program distribution mutation-chance]
  (or (loop [program-size (count initial-program)
             index 0
             program (seq initial-program)]
        (if (>= index program-size)
          program
          (if (>= (rand) mutation-chance)
            (recur program-size
                   (inc index)
                   program)
            (recur (inc program-size)
                   (inc index)
                   (concat (take index program)
                           (list (distribution))
                           (drop index program))))))
      '()))

(defn uniform-deletion [initial-program mutation-chance]
  (or (loop [program-size (count initial-program)
             index 0
             program (seq initial-program)]
        (if (>= index (dec program-size))
          program
          (if (>= (rand) mutation-chance)
            (recur program-size
                   (inc index)
                   program)
            (recur (dec program-size)
                   index
                   (concat (take index program)
                           (drop (inc index) program))))))
      '()))

(defn umad [program distribution mutation-chance]
  (let [mutation-chance (or mutation-chance 0.1)]
    (-> program
        (uniform-addition distribution mutation-chance)
        (uniform-deletion (* 2 mutation-chance)))))

(defn crossover [program1 program2]
  (let [[p1 p2] (shuffle [program1 program2])
        size1 (count p1)
        size2 (count p2)
        switch-point (rand)]
    (or (concat (take (- size1
                         (rand-int (- size1
                                      (Math/floor
                                       (* switch-point size1)))))
                      p1)
                (drop (rand-int (Math/ceil (* switch-point size2)))
                      p2))
        '())))


(defn simplify-program [program environment input-count]
  (apply list
         (:new-program
          (reduce (fn [{:keys [new-program value-count]} op]
                    (if (keyword? op)
                      (let [{:keys [in-count out-count]} (environment op)]
                        (if (>= value-count in-count)
                          {:new-program (conj new-program op)
                           :value-count (+ value-count (- in-count) out-count)}
                          {:new-program new-program
                           :value-count value-count}))
                      {:new-program (conj new-program op)
                       :value-count (inc value-count)}))
                  {:new-program []
                   :value-count input-count}
                  program))))

(defn output-count [simplified-program environment input-count]
  (reduce (fn [value-count op]
            (if (keyword? op)
              (- (+ (:out-count (environment op))
                    value-count)
                 (:in-count (environment op)))
              (inc value-count)))
          input-count
          simplified-program))

(defn input-influences [simple-program input-count environment]
  (loop [remaining-program simple-program
         influences (map hash-set (range input-count))]
    (if (empty? remaining-program)
      influences
      (recur (rest remaining-program)
             (let [op (first remaining-program)]
               (if (keyword? op)
                 (let [{:keys [in-count out-count]} (environment op)
                       consumed-influences (apply union
                                                  (take in-count
                                                        influences))]
                   (concat (repeat out-count consumed-influences)
                           (drop in-count
                                 influences)))
                 (conj influences #{})))))))

(defn get-max-values [program environment input-count]
  (apply max
         (reduce (fn [value-counts op]
                   (let [last-count (last value-counts)]
                     (if (keyword? op)
                       (let [{:keys [in-count out-count]} (environment op)]
                         (if (>= last-count in-count)
                           (conj value-counts (+ last-count
                                                 (- in-count)
                                                 out-count))
                           (conj value-counts last-count)))
                       (conj value-counts
                             (inc last-count)))))
                 [input-count]
                 program)))

(defn mutate [program population &
              [{:keys [distribution crossover-chance umad-mutation-chance chaos]
                :or {distribution default-distribution
                     crossover-chance 0.5
                     umad-mutation-chance 0.1
                     chaos 0}
                :as argmap}]]
  (let [new-program (if (< (rand) crossover-chance)
                      (crossover program (rand-nth population))
                      (umad program distribution umad-mutation-chance))]
    (if (>= (rand) chaos)
      new-program
      (mutate new-program
              population
              argmap))))
