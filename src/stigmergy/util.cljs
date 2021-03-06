(ns stigmergy.util)

(defn now []
  (js/Date.now))

(defn fn->map [f map-keys]
  (reduce #(assoc %1
                  %2
                  (f %2))
          {}
          map-keys))

(defn log [& vals]
  (doseq [val vals]
    (js/console.log (clj->js val)))
  (last vals))

(defn scale
  ([from-min from-max to-min to-max value]
   (+ (* (/ (- value from-min)
            (- from-max from-min))
         (- to-max to-min))
      to-min))
  ([from-min from-max to-min to-max]
   #(+ (* (/ (- % from-min)
             (- from-max from-min))
          (- to-max to-min))
       to-min))
  ([to-min to-max value]
   (scale 0 1 to-min to-max value))
  ([to-min to-max]
   (scale 0 1 to-min to-max)))

(defn prop-range [n & [open]]
  (map #(/ %
           (if open
             n
             (dec n)))
       (range n)))

(defn clamp
  ([bottom top value] (min top (max bottom value)))
  ([min max] #(clamp min max %))
  ([value] (clamp 0 1 value)))

(def sigmoid (comp / inc Math/exp -))

(defn update! [transient-coll key f]
  (assoc! transient-coll
          key
          (f (transient-coll key))))

(def TAU (* Math/PI 2))
