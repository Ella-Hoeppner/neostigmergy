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
                                      trail-opacity
                                      agent-count-sqrt
                                      substrate-fade-factor]]
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
        :trail-opacity (.toFixed trail-opacity 8)
        :substrate-fade-factor (.toFixed substrate-fade-factor 8)}
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
                      :uint16-max
                      :substrate-fade-factor)
                   (* (+ :blur-exp-2
                         (* :trail-opacity trailValue2))
                      :uint16-max
                      :substrate-fade-factor)
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
    :uniforms '{agentTex usampler2D}
    :outputs '{trailValue1 float
               trailValue2 float}
    :functions
    (postwalk-replace
     {:agent-count-sqrt (.toFixed agent-count-sqrt 1)
      :uint16-max (.toFixed uint16-max 1)
      :point-size (.toFixed point-size 8)}
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
        (=float agentX (/ (float agentColor.x) :uint16-max))
        (=float agentY (/ (float agentColor.y) :uint16-max))
        (= trailValue1 (/ (float agentColor.z) :uint16-max))
        (= trailValue2 (/ (float agentColor.w) :uint16-max))
        (= gl_Position (vec4 (- (* agentX "2.0") "1.0")
                             (- (* agentY "2.0") "1.0")
                             "0.0"
                             "1.0"))
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

(def agents-frag-source
  (iglu-wrapper
   {:version "300 es"
    :precision "highp float"
    :uniforms '{randomize int
                oldAgentTex usampler2D}
    :inputs '{trailValue1 float
              trailValue2 float}
    :outputs '{newAgentColor uvec4}
    :signatures '{rand ([vec2] float)
                  main ([] void)
                  behavior ([float float float float] vec4)}
    :functions
    {'behavior
     '([a b c d]
       (vec4 "0.00025"
             "0.0001"
             "1.0"
             "0.5"))
     'rand
     '([p]
       (=vec3 p3 (fract (* (vec3 p.xyx) "0.1031")))
       (+= p3 (dot p3 (+ p3.yzx "33.33")))
       (fract (* (+ p3.x p3.y) p3.z)))
     'main
     (postwalk-replace
      {:agent-count-sqrt (.toFixed agent-count-sqrt 1)
       :uint16-max (.toFixed uint16-max 1)}
      '([]
        (=uvec4 oldAgentColor
                (texture oldAgentTex
                         (/ gl_FragCoord.xy
                            (vec2 :agent-count-sqrt))))
        (=vec2 pos
               (/ (vec2 oldAgentColor.xy)
                  :uint16-max))
        
        (=vec4 behaviorResult (behavior "0.0" "0.0" "0.0" "0.0"))
        (=vec2 velocity behaviorResult.xy)
        (=float substrateValue1 behaviorResult.z)
        (=float substrateValue2 behaviorResult.w)

        (=vec2 newPos
               (fract
                (+ pos
                   velocity)))
        (=vec2 randSeed
               (+ (vec2 oldAgentColor.xy)
                  gl_FragCoord.xy))
        (=vec2 randPos
               (vec2 (rand randSeed)
                     (rand (+ randSeed (vec2 "0.1" "-0.5")))))
        (= newAgentColor
           (if (== randomize 0)
             (uvec4 (* :uint16-max newPos)
                    (* substrateValue1 :uint16-max)
                    (* substrateValue2 :uint16-max))
             (uvec4 (* :uint16-max randPos)
                    (* "0.5" :uint16-max)
                    (* "0.5" :uint16-max))))))}}
   ["behavior"
    "rand"
    "main"]))
