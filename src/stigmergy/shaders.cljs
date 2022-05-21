(ns stigmergy.shaders
  (:require [stigmergy.glsl-util :refer [reorder-functions]]
            [iglu.core :refer [iglu->glsl]]
            [clojure.walk :refer [postwalk-replace]]
            [stigmergy.config :refer [substrate-resolution
                                 gaussian-radius
                                 gaussian-sigma-1
                                 gaussian-sigma-2
                                 uint16-max
                                 point-size
                                 trail-opacity]]
            [clojure.string :as string]))

(defn generate-gaussian-expression [value-fn radius sigma]
  (let [side-length (inc (* 2 radius))
        coords (map (fn [i]
                      [(- (mod i side-length) radius)
                       (- (quot i side-length) radius)])
                    (range (Math/pow side-length 2)))
        factors (map (fn [[x y]]
                       (Math/exp
                        (/ (- (+ (* x x) (* y y)))
                           (* 2 sigma sigma))))
                     coords)
        factor-sum (apply + factors)]
    (conj (map (fn [[x y] factor]
                 (postwalk-replace
                  {:x (.toFixed x 1)
                   :y (.toFixed y 1)
                   :factor (.toFixed (/ factor factor-sum) 8)
                   :value-fn value-fn}
                  '(* (:value-fn :x :y) :factor)))
               coords
               factors)
          '+)))

(defn iglu-wrapper [iglu-code & [fn-order]]
  (let [text (iglu->glsl iglu-code)
        fixed-text (string/replace text
                                   "uniform usampler2D"
                                   "uniform highp usampler2D")]
    (if fn-order
      (reorder-functions fixed-text
                         fn-order)
      fixed-text)))

(def trivial-vert-source
  (iglu-wrapper
   {:version "300 es"
    :precision "highp float"
    :inputs '{vertPos vec4}
    :signatures '{main ([] void)}
    :functions
    '{main
      ([]
       (= gl_Position vertPos))}}))

(def draw-frag-source
  (iglu-wrapper
   (postwalk-replace
    {:uint16-max (.toFixed uint16-max 8)}
    {:version "300 es"
     :precision "highp float"
     :uniforms '{size float
                 substrate usampler2D}
     :outputs '{fragColor vec4}
     :signatures '{main ([] void)}
     :functions
     '{main
       ([]
        (=uvec4 substrateValue (texture substrate
                                        (/ gl_FragCoord.xy size)))
        (= fragColor
           (/ (vec4 substrateValue) :uint16-max)))}})))

(def substrate-frag-source
  (iglu-wrapper
   (postwalk-replace
    {:substrate-resolution (.toFixed substrate-resolution 8)
     :uint16-max (.toFixed uint16-max 8)}
    {:version "300 es"
     :precision "highp float"
     :uniforms '{oldSubstrate usampler2D
                 trail usampler2D}
     :outputs '{fragColor uvec4}
     :signatures '{getValue1 ([float float] float)
                   getValue2 ([float float] float)
                   main ([] void)}
     :functions
     {'getValue1
      '([x y]
        (/ (float
            (.x
             (texture oldSubstrate
                      (/ (+ gl_FragCoord.xy
                            (vec2 x y))
                         :substrate-resolution))))
           :uint16-max))
      'getValue2
      '([x y]
        (/ (float
            (.y
             (texture oldSubstrate
                      (/ (+ gl_FragCoord.xy
                            (vec2 x y))
                         :substrate-resolution))))
           :uint16-max))
      'main
      (postwalk-replace
       {:blur-exp-1
        (generate-gaussian-expression 'getValue1
                                      gaussian-radius
                                      gaussian-sigma-1)
        :blur-exp-2
        (generate-gaussian-expression 'getValue2
                                      gaussian-radius
                                      gaussian-sigma-2)
        :trail-opacity (.toFixed trail-opacity 8)}
       '([]
         (=uvec4 trailColor (texture trail
                                     (/ gl_FragCoord.xy
                                        :substrate-resolution)))
         (=float trailValue1 (- (* "2.0"
                                   (/ (float trailColor.x)
                                      :uint16-max))
                                "1.0"))
         (=float trailValue2 (- (* "2.0"
                                   (/ (float trailColor.y)
                                      :uint16-max))
                                "1.0"))
         (= fragColor
            (uvec4 (* (+ :blur-exp-1
                         (* :trail-opacity trailValue1))
                      :uint16-max)
                   (* (+ :blur-exp-2
                         (* :trail-opacity trailValue2))
                      :uint16-max)
                   0
                   0))))}})
   ["getValue1"
    "getValue2"
    "main"]))

(def trail-vert-source
  (iglu-wrapper
   {:version "300 es"
    :precision "highp float"
    :signatures '{main ([] void)}
    :outputs '{trailValue1 float
               trailValue2 float}
    :functions
    (postwalk-replace
     {:point-size (.toFixed point-size 8)}
     '{main
       ([]
        (= gl_Position (vec4 (* "0.1" (float gl_VertexID))
                             (* "0.1" (float gl_VertexID))
                             "0.0"
                             "1.0"))
        (= trailValue1 (max "0.5" (float gl_VertexID)))
        (= trailValue2 (max "0.5" (- "1.0" (float gl_VertexID))))
        (= gl_PointSize :point-size))})}))

(def trail-frag-source
  (iglu-wrapper
   {:version "300 es"
    :precision "highp float"
    :inputs '{trailValue1 float
              trailValue2 float}
    :outputs '{outColor uvec4}
    :signatures '{main ([] void)}
    :functions
    (postwalk-replace
     {:uint16-max uint16-max}
     '{main
       ([]
        (=vec2 pos (- (* "2.0" gl_PointCoord) "1.0"))
        (=float inCircle (step (dot pos pos)
                               "1.0"))
        "if (inCircle < 0.5){discard;}"
        (= outColor (uvec4 (* trailValue1 (float :uint16-max))
                           (* trailValue2 (float :uint16-max))
                           0
                           0)))})}))
