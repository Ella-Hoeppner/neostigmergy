(ns ants.glsl-util
  (:require [ants.util :as u]
            [clojure.set :refer [map-invert]]
            [clojure.string :refer [split
                                    index-of]]))

(defn create-shader [gl shader-type source]
  (let [shader (.createShader gl (or ({:frag gl.FRAGMENT_SHADER
                                       :vert gl.VERTEX_SHADER}
                                      shader-type)
                                     shader-type))]
    shader
    (.shaderSource gl shader source)
    (.compileShader gl shader)
    (if (.getShaderParameter gl shader gl.COMPILE_STATUS)
      shader
      (throw (js/Error. (str (.getShaderInfoLog gl shader)))))))

(defn create-program [gl vert-shader frag-shader]
  (let [program (.createProgram gl)]
    (.attachShader gl program vert-shader)
    (.attachShader gl program frag-shader)
    (.linkProgram gl program)
    (if (.getProgramParameter gl program gl.LINK_STATUS)
      program
      (throw (js/Error. (str (.getProgramInfoLog gl program)))))))

(defn reorder-functions [source function-order]
  (let [lines (split source "\n")
        line-count (count lines)
        open-function-indeces (filter #(= (lines %) "{")
                                      (range line-count))
        close-function-indeces (filter #(= (lines %) "}")
                                       (range line-count))
        functions (map (fn [open close]
                         (drop (dec open)
                               (take (inc close) lines)))
                       open-function-indeces
                       close-function-indeces)
        function-names (u/fn->map #(let [first-line (first %)]
                                     (subs first-line
                                           (inc (index-of first-line " "))
                                           (index-of first-line "(")))
                                  functions)]
    (apply str
           (interleave
            (concat (take (dec (first open-function-indeces)) lines)
                    (mapcat (map-invert function-names)
                            function-order))
            (repeat "\n")))))
