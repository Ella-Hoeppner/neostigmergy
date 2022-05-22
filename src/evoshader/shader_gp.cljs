(ns evoshader.shader-gp
  (:require [evoshader.single-stack-gp :refer [lit-num-generator
                                               environment-distribution
                                               rand-program
                                               mutate
                                               input-influences
                                               get-max-values
                                               simplify-program]]
            [clojure.walk :refer [postwalk-replace]]
            [clojure.set :refer [union]]))

(def gp-env
  (merge
   (apply hash-map
          (flatten
           (map (fn [[op-key & outs]]
                  [op-key
                   {:in-count
                    (inc
                     (apply max
                            (map (fn [element]
                                   (when (symbol? element)
                                     (let [s (str element)
                                           index (js/parseInt (subs s 3))]
                                       (when (and (= "arg" (subs s 0 3))
                                                  (not (js/isNaN index)))
                                         index))))
                                 (flatten outs))))
                    :out-count (count outs)
                    :outs (vec outs)}])
                '[[:+ (+ arg0 arg1)]
                  [:* (* arg0 arg1)]
                  [:- (- arg0 arg1)]
                  [:div (safeDiv arg0 arg1)]
                  [:mod (if (== arg0 "0.0") "0.0" (mod (abs arg0) (abs arg1)))]
                  [:neg (- arg0)]
                  [:inv (safeDiv "1.0" arg0)]
                  [:sin (sin arg0)]
                  [:cos (cos arg0)]
                  [:tan (tan arg0)]
                  [:sinh (sinh arg0)]
                  [:cosh (cosh arg0)]
                  [:tanh (tanh arg0)]
                  [:exp (exp arg0)]
                  [:ln (if (== arg0 "0.0")
                         "0.0"
                         (* (sign arg0)
                            (log (abs arg0))))]
                  [:sigmoid (sigmoid arg0)]
                  [:pow (* (sign arg0)
                           (pow (abs arg0) arg1))]
                  [:min (min arg0 arg1)]
                  [:max (max arg0 arg1)]
                  [:abs (abs arg0)]
                  [:floor (floor arg0)]
                  [:ceil (ceil arg0)]
                  [:fract (fract arg0)]
                  [:sqrt (* (sign arg0) (sqrt (abs arg0)))]

                  [:c-+ (+ arg0 arg2) (+ arg1 arg3)]
                  [:c-*
                   (- (* arg0 arg2) (* arg1 arg3))
                   (+ (* arg0 arg3) (* arg1 arg2))]
                  [:c-- (- arg0 arg2) (- arg1 arg3)]
                  [:c-neg (- arg0) (- arg1)]
                  [:c-inv
                   (safeDiv arg0 (+ (* arg0 arg0) (* arg1 arg1)))
                   (safeDiv (- arg1) (+ (* arg0 arg0) (* arg1 arg1)))]
                  [:c-scale (* arg0 arg2) (* arg1 arg2)]
                  [:c-sin
                   (* (sin arg0) (cosh (- arg1)))
                   (* (- (cos arg0)) (sinh (- arg1)))]
                  [:c-cos
                   (* (cos arg0) (cosh (- arg1)))
                   (* (sin arg0) (sinh (- arg1)))]
                  [:c-tan
                   (safeDiv (sin (* "2.0" arg0))
                            (+ (cos (* "2.0" arg0))
                               (cosh (* "2.0" arg1))))
                   (safeDiv (sinh (* "2.0" arg1))
                            (+ (cos (* "2.0" arg0))
                               (cosh (* "2.0" arg1))))]
                  [:c-sinh
                   (* (sinh arg0) (cos arg1))
                   (* (cosh arg0) (sin arg1))]
                  [:c-cosh
                   (* (cosh arg0) (cos arg1))
                   (* (- (sin arg0)) (sinh arg1))]
                  [:c-tanh
                   (safeDiv (sinh (* "2.0" arg0))
                            (+ (cosh (* "2.0" arg0))
                               (cos (* "2.0" arg1))))
                   (safeDiv (sin (* "2.0" arg1))
                            (+ (cosh (* "2.0" arg0))
                               (cos (* "2.0" arg1))))]
                  [:c-exp
                   (* (exp arg0) (cos arg1))
                   (* (exp arg0) (sin arg1))]
                  [:c-ln
                   (* 0.5
                      (cos (+ (* arg0 arg0)
                              (* arg1 arg1))))
                   (if (== arg1 "0.0")
                     "0.0"
                     (atan arg0 arg1))]

                  [:if
                   (+ (* (step arg0 "0.0")
                         arg1)
                      (* (step "0.0" arg0)
                         arg2))]

                  [:dup1-2 arg0 arg0]
                  [:dup1-3 arg0 arg0 arg0]
                  [:dup1-4 arg0 arg0 arg0 arg0]

                  [:dup2-2 arg0 arg1 arg0 arg1]
                  [:dup2-3 arg0 arg1 arg0 arg1 arg0 arg1]
                  [:dup2-4 arg0 arg1 arg0 arg1 arg0 arg1 arg0 arg1]])))
   {:swap {:in-count 2
           :out-count 2
           :outs '[arg1 arg0]
           :influence-assigner (fn [arg0 arg1]
                                 [arg1 arg0])}
    :swap2 {:in-count 4
            :out-count 4
            :outs '[arg2 arg3 arg0 arg1]
            :influence-assigner (fn [arg0 arg1 arg2 arg3]
                                  [arg2 arg3 arg0 arg1])}
    :swap3 {:in-count 6
            :out-count 6
            :outs '[arg3 arg4 arg5 arg0 arg1 arg2]
            :influence-assigner (fn [arg0 arg1 arg2 arg3 arg4 arg5]
                                  [arg3 arg4 arg5 arg0 arg1 arg2])}}
   {:drop {:in-count 1 :out-count 0 :outs []}
    :drop2 {:in-count 2 :out-count 0 :outs []}
    :drop3 {:in-count 3 :out-count 0 :outs []}}))

(def gp-dist
  (environment-distribution
   (keys gp-env)
   {:num-chance 0.25
    :op-weights
    {}
    :literal-generator
    (lit-num-generator 1)}))

(defn scratch-program [input-count output-count]
  (some #(when (and (seq %)
                    (= (apply union
                              (take output-count (input-influences %
                                                                   input-count
                                                                   gp-env)))
                       (set (range input-count))))
           %)
        (repeatedly
         #(rand-program gp-dist))))

(defn breed-program [population input-count output-count & [chaos]]
  (some #(when (and (seq %)
                    (= (apply union
                              (take output-count
                                    (input-influences %
                                                      input-count
                                                      gp-env)))
                       (set (range input-count))))
           %)
        (repeatedly
         #(mutate (rand-nth population)
                  population
                  {:distribution gp-dist
                   :chaos (or chaos 0.9)}))))

(defn program->glsl-fn [simplified-program input-count output-count]
  (let [max-values (get-max-values simplified-program
                                   gp-env
                                   input-count)
        max-op-input-count (or (apply max
                                      (map (fn [op]
                                             (if (keyword? op)
                                               (:in-count (gp-env op))
                                               1))
                                           simplified-program))
                               0)]
    (loop [remaining-program simplified-program
           steps
           (vec
            (conj
             (map (fn [index]
                    (list '=
                          (str "stackValue" index)
                          (str "input" index)))
                  (range input-count))
             "//Input stack values"))
           current-value-count input-count]
      (if (empty? remaining-program)
        (concat
         (list (mapv #(symbol (str "input" %))
                     (range input-count)))
         (map #(str "  float stackValue" % "=0.0;")
              (range max-values))
         (map #(str "  float arg" % "=0.0;")
              (range max-op-input-count))
         (conj steps
               (if (= output-count 1)
                 (if (pos? current-value-count)
                   (str "stackValue" (dec current-value-count))
                   "0.0")
                 (conj (map (fn [index]
                              (if (>= current-value-count index)
                                (str "stackValue" (- current-value-count index))
                                "0.0"))
                            (range 1 (inc output-count)))
                       (symbol (str "vec" output-count))))))
        (let [op (first remaining-program)]
          (if (keyword? op)
            (let [{:keys [in-count outs]} (gp-env op)]
              (recur
               (rest remaining-program)
               (let [first-index (- current-value-count in-count)]
                 (reduce conj
                         steps
                         (conj
                          (concat
                           (map (fn [index]
                                  (list
                                   '=
                                   (str "arg" index)
                                   (str "stackValue" (+ first-index index))))
                                (range in-count))
                           (map (fn [out index]
                                  (list
                                   '=
                                   (str "stackValue"  (+ first-index index))
                                   out))
                                outs
                                (range)))
                          (str "//OP: " (subs (str op) 1)))))
               (+ current-value-count (- in-count) (count outs))))
            (recur (rest remaining-program)
                   (conj steps
                         (str "//LIT: " op)
                         (list '=
                               (str "stackValue" current-value-count)
                               (str op)))
                   (inc current-value-count))))))))

(defn compile-program [function-name inputs outputs program]
  {:signatures
   (assoc '{sinh ([float] float)
            cosh ([float] float)
            tanh ([float] float)
            safeDiv ([float float] float)
            sigmoid ([float] float)
            main ([] void)}
          function-name
          (list (vec (repeat inputs 'float))
                ('{1 float
                   2 vec2
                   3 vec3
                   4 vec4}
                 outputs)))
   :functions
   (assoc {'sigmoid
           '([x] (/ "1.0" (+ "1.0" (exp (- "0.0" x)))))
           'safeDiv
           '([x y] (if (== y "0.0") "0.0" (/ x y)))}
          function-name
          (program->glsl-fn (simplify-program program
                                              gp-env
                                              inputs)
                            inputs
                            outputs))})
