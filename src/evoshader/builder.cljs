(ns evoshader.builder
  (:require [evoshader.shader-gp :refer [scratch-program
                                         breed-program
                                         compile-program]]
            [iglu.core :refer [iglu->glsl]]
            [clojure.string :as string]))

(defn scratch [{:keys [inputs outputs]
                :as builder}]
  (update builder
          :population
          conj
          (scratch-program inputs outputs)))

(defn breed [{:keys [population inputs outputs chaos]
              :as builder}]
  (update builder
          :population
          conj
          (breed-program population
                         inputs
                         outputs
                         chaos)))

(defn delete [{:keys [population]
               :as builder}
              index]
  (let [population-size (count population)]
    (if (and (> population-size 1)
             (>= index 0)
             (< index population-size))
      (-> builder
          (assoc :population (vec (concat (take index population)
                                          (drop (inc index) population))))
          (update :delete-history conj (population index)))
      builder)))

(defn undelete [{:keys [population
                        delete-history]
                 :as builder}]
  (if (empty? delete-history)
    builder
    (-> builder
        (update :population conj (first delete-history))
        (update :delete-history rest))))

(defn get-iglu-chunk [{:keys [function-name population inputs outputs]}
                      index]
  (let [population-size (count population)]
    (when (and (>= index 0)
               (< index population-size))
      (compile-program function-name inputs outputs (population index)))))

(defn create-builder [function-name inputs outputs & [chaos]]
  (scratch
   {:chaos (or chaos 0.5)
    :function-name function-name
    :inputs inputs
    :outputs outputs
    :population []}))

(defn population-string [{:keys [population]}]
  (string/replace (str population)
                  ") ("
                  ")\n("))
