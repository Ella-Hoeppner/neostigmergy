(ns stigmergy.shaders
  (:require [stigmergy.util :as u]
            [stigmergy.glsl-util :refer [reorder-functions]]
            [iglu.core :refer [iglu->glsl]]
            [clojure.walk :refer [postwalk-replace]]
            [stigmergy.config :refer [substrate-resolution
                                      gaussian-radius
                                      gaussian-sigma
                                      uint16-max
                                      point-size
                                      trail-opacity
                                      agent-count-sqrt
                                      substrate-fade-factor
                                      sensor-distance
                                      sensor-spread
                                      angle-change-factor
                                      agent-speed-factor]]
            [clojure.string :as string]))

(defn generate-gaussian-expression [value-fn radius sigma]
  (let [coords (for [x (range (- radius) (inc radius))
                     y (range (- radius) (inc radius))]
                 [x y])
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
        fixed-text (-> text
                       (string/replace
                        "uniform usampler2D"
                        "uniform highp usampler2D")
                       (string/replace
                        "precision highp all;"
                        "precision highp float; precision highp int;"))]
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
    {:uint16-max-f (.toFixed uint16-max 1)}
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
           (vec4 (/ (vec2 substrateValue.xy) :uint16-max-f)
                 0
                 1)))}})))

(def substrate-frag-source
  (iglu-wrapper
   (postwalk-replace
    {:substrate-resolution (.toFixed substrate-resolution 8)
     :uint16-max-f (.toFixed uint16-max 1)}
    {:version "300 es"
     :precision "highp all"
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
           :uint16-max-f))
      'getValue2
      '([x y]
        (/ (float
            (.y
             (texture oldSubstrate
                      (/ (+ gl_FragCoord.xy
                            (vec2 x y))
                         :substrate-resolution))))
           :uint16-max-f))
      'main
      (postwalk-replace
       {:blur-exp-1
        (generate-gaussian-expression 'getValue1
                                      gaussian-radius
                                      gaussian-sigma)
        :blur-exp-2
        (generate-gaussian-expression 'getValue2
                                      gaussian-radius
                                      gaussian-sigma)
        :trail-opacity (.toFixed trail-opacity 8)
        :substrate-fade-factor (.toFixed substrate-fade-factor 8)}
       '([]
         (=uvec4 trailColor (texture trail
                                     (/ gl_FragCoord.xy
                                        :substrate-resolution)))
         (=float trailValue1 (- (* "2.0"
                                   (/ (float trailColor.x)
                                      :uint16-max-f))
                                "1.0"))
         (=float trailValue2 (- (* "2.0"
                                   (/ (float trailColor.y)
                                      :uint16-max-f))
                                "1.0"))
         (= fragColor
            (uvec4 (* (+ :blur-exp-1
                         (* :trail-opacity trailValue1))
                      :uint16-max-f
                      :substrate-fade-factor)
                   (* (+ :blur-exp-2
                         (* :trail-opacity trailValue2))
                      :uint16-max-f
                      :substrate-fade-factor)
                   0
                   0))))}})
   ["getValue1"
    "getValue2"
    "main"]))

(def trail-vert-source
  (iglu-wrapper
   {:version "300 es"
    :precision "highp all"
    :signatures '{main ([] void)}
    :uniforms '{agentTex usampler2D}
    :outputs '{trailValue1 float
               trailValue2 float}
    :functions
    (postwalk-replace
     {:agent-count-sqrt (.toFixed agent-count-sqrt 1)
      :uint16-max-f (.toFixed uint16-max 1)
      :point-size (.toFixed (* substrate-resolution
                               point-size)
                            8)}
     '{main
       ([]
        (=float agentTexX
                (/ (+ (mod (float gl_VertexID) :agent-count-sqrt)
                      "0.5")
                   :agent-count-sqrt))
        (=float agentTexY
                (/ (+ (floor (/ (float gl_VertexID) :agent-count-sqrt))
                      "0.5")
                   :agent-count-sqrt))
        (=uvec4 agentColor
                (texture agentTex
                         (vec2 agentTexX agentTexY)))
        (=vec2 agentPos
               (vec2 (/ (float "agentColor.x % 65536u") :uint16-max-f)
                     (/ (float "agentColor.x / 65536u") :uint16-max-f)))
        (= trailValue1
           (/ (float "agentColor.w % 65536u") :uint16-max-f))
        (= trailValue2
           (/ (float "agentColor.w / 65536u") :uint16-max-f))
        (= gl_Position (vec4 (- (* agentPos "2.0") "1.0")
                             "0.0"
                             "1.0"))
        (= gl_PointSize :point-size))})}))

(def trail-frag-source
  (iglu-wrapper
   {:version "300 es"
    :precision "highp all"
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

(defn get-agents-frag-source [chunk]
  (iglu-wrapper
   (merge-with
    merge
    {:version "300 es"
     :precision "highp all"
     :uniforms '{randomize int
                 substrate usampler2D
                 oldAgentTex usampler2D}
     :inputs '{trailValue1 float
               trailValue2 float}
     :outputs '{newAgentColor uvec4}
     :signatures '{rand ([vec2] float)
                   behavior ([float float float float] vec4)
                   getSensorValue1 ([vec2] float)
                   getSensorValue2 ([vec2] float)
                   main ([] void)}
     :functions
     {'rand
      '([p]
        (=vec3 p3 (fract (* (vec3 p.xyx) "0.1031")))
        (+= p3 (dot p3 (+ p3.yzx "33.33")))
        (fract (* (+ p3.x p3.y) p3.z)))
      'getSensorValue1
      (postwalk-replace
       {:uint16-max-f (.toFixed uint16-max 1)}
       '([pos]
         (/ (float
             (.x (texture substrate (fract pos))))
            :uint16-max-f)))
      'getSensorValue2
      (postwalk-replace
       {:uint16-max-f (.toFixed uint16-max 1)}
       '([pos]
         (/ (float
             (.y (texture substrate (fract pos))))
            :uint16-max-f)))
      'main
      (postwalk-replace
       {:agent-count-sqrt (.toFixed agent-count-sqrt 1)
        :uint16-max-f (.toFixed uint16-max 1)
        :sensor-distance (.toFixed sensor-distance 8)
        :agent-speed-factor (.toFixed agent-speed-factor 8)
        :sensor-spread (.toFixed sensor-spread 8)
        :angle-change-factor (.toFixed angle-change-factor 8)
        :TAU (.toFixed u/TAU 8)}
       '([]
         (=uvec4 oldAgentColor
                 (texture oldAgentTex
                          (/ gl_FragCoord.xy :agent-count-sqrt)))
         (=vec2 pos
                (vec2 (/ (float "oldAgentColor.x % 65536u") :uint16-max-f)
                      (/ (float "oldAgentColor.x / 65536u") :uint16-max-f)))
         (=float angleProp (/ (float oldAgentColor.y) :uint16-max-f))
         (=float angle (* angleProp :TAU))
         (=vec2 dir (vec2 (cos angle) (sin angle)))

         (=vec3 behaviorResult
                (behavior (getSensorValue1 (+ pos (* :sensor-distance dir)))
                          (getSensorValue2 (+ pos (* :sensor-distance dir)))
                          (- (getSensorValue1
                              (+ pos
                                 (* :sensor-distance
                                    (vec2 (cos (+ angle :sensor-spread))
                                          (sin (+ angle :sensor-spread))))))
                             (getSensorValue1
                              (+ pos
                                 (* :sensor-distance
                                    (vec2 (cos (- angle :sensor-spread))
                                          (sin (- angle :sensor-spread)))))))
                          (- (getSensorValue2
                              (+ pos
                                 (* :sensor-distance
                                    (vec2 (cos (+ angle :sensor-spread))
                                          (sin (+ angle :sensor-spread))))))
                             (getSensorValue2
                              (+ pos
                                 (* :sensor-distance
                                    (vec2 (cos (- angle :sensor-spread))
                                          (sin (- angle :sensor-spread)))))))))

         (=vec2 randSeed
                (+ "-70.65" (* "344.8" pos)
                   (* "271.1" (/ gl_FragCoord.xy :agent-count-sqrt))))
         (=vec2 randPos
                (vec2 (rand randSeed)
                      (rand (+ randSeed (vec2 "0.1" "-0.5")))))

         (=vec2 newPos
                (if (== randomize 0)
                  (+ pos (* :agent-speed-factor dir))
                  randPos))
         (=float newAngleProp (+ angleProp
                                 (* :angle-change-factor
                                    (- (* "2.0" behaviorResult.x) "1.0"))))
         (=float substrateValue1 (if (== randomize 0)
                                   (sigmoid behaviorResult.y)
                                   "0.5"))
         (=float substrateValue2 (if (== randomize 0)
                                   (sigmoid behaviorResult.z)
                                   "0.5"))
         (= newAgentColor
            (uvec4 (+ (uint (* :uint16-max-f
                               newPos.x))
                      (* "65536u"
                         (uint (* :uint16-max-f
                                  newPos.y))))
                   (* newAngleProp :uint16-max-f)
                   0
                   (+ (uint (* :uint16-max-f
                               substrateValue1))
                      (* "65536u"
                         (uint (* :uint16-max-f
                                  substrateValue2))))))))}}
    chunk)
   ["safeDiv"
    "sigmoid"
    "behavior"
    "getSensorValue1"
    "getSensorValue2"
    "rand"
    "main"]))
