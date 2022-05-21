(ns stigmergy.core
  (:require [stigmergy.util :as u]
            [stigmergy.glsl-util :refer [create-shader
                                         create-program
                                         create-ui16-tex]]
            [stigmergy.keyboard :refer [add-key-callback]]
            [stigmergy.config :refer [substrate-resolution
                                      background-color
                                      uint16-max
                                      agent-count-sqrt]]
            [stigmergy.shaders :refer [trivial-vert-source
                                       draw-frag-source
                                       substrate-frag-source
                                       trail-vert-source
                                       trail-frag-source
                                       agents-frag-source]]))

(defonce auto-update-atom (atom true))

(defonce gl-atom (atom nil))
(defonce draw-program-atom (atom nil))
(defonce draw-size-u-atom (atom nil))
(defonce draw-substrate-u-atom (atom nil))

(defonce agents-program-atom (atom nil))
(defonce agents-old-agent-tex-u-atom (atom nil))
(defonce agents-randomize-u-atom (atom nil))

(defonce substrate-program-atom (atom nil))
(defonce substrate-old-substrate-u-atom (atom nil))
(defonce substrate-trail-u-atom (atom nil))

(defonce trail-program-atom (atom nil))
(defonce trail-agent-tex-u-atom (atom nil))

(defonce agent-tex-front-atom (atom nil))
(defonce agent-tex-back-atom (atom nil))
(defonce substrate-tex-front-atom (atom nil))
(defonce substrate-tex-back-atom (atom nil))
(defonce trail-tex-atom (atom nil))

(defonce framebuffer-atom (atom nil))

(defn resize-canvas [canvas]
  (let [w js/window.innerWidth
        h js/window.innerHeight
        s (min w h)
        style canvas.style]
    (set! (.-left style) (* 0.5 (- w s)))
    (set! (.-top style) (* 0.5 (- h s)))
    (set! (.-width canvas) s)
    (set! (.-height canvas) s)))

(defn flip-textures! [front-tex-atom back-tex-atom]
  (let [old-front @front-tex-atom]
    (reset! front-tex-atom @back-tex-atom)
    (reset! back-tex-atom old-front)))

(defn randomize-agents! []
  (let [gl @gl-atom]
    (.bindFramebuffer gl gl.FRAMEBUFFER @framebuffer-atom)
    (.viewport gl 0 0 agent-count-sqrt agent-count-sqrt)
    (.framebufferTexture2D gl
                           gl.FRAMEBUFFER
                           gl.COLOR_ATTACHMENT0
                           gl.TEXTURE_2D
                           @agent-tex-back-atom
                           0)
    (.useProgram gl @agents-program-atom)
    (.activeTexture gl gl.TEXTURE0)
    (.bindTexture gl gl.TEXTURE_2D @agent-tex-front-atom)
    (.uniform1i gl @agents-old-agent-tex-u-atom 0)
    (.uniform1i gl @agents-randomize-u-atom 1)
    (.drawArrays gl gl.TRIANGLES 0 3)
    (flip-textures! agent-tex-front-atom agent-tex-back-atom)))

(defn logic-step! []
  (let [gl @gl-atom]
    (.bindFramebuffer gl gl.FRAMEBUFFER @framebuffer-atom)

    ; update agents
    (.viewport gl 0 0 agent-count-sqrt agent-count-sqrt)
    (.framebufferTexture2D gl
                           gl.FRAMEBUFFER
                           gl.COLOR_ATTACHMENT0
                           gl.TEXTURE_2D
                           @agent-tex-back-atom
                           0)
    (.useProgram gl @agents-program-atom)
    (.activeTexture gl gl.TEXTURE0)
    (.bindTexture gl gl.TEXTURE_2D @agent-tex-front-atom)
    (.uniform1i gl @agents-old-agent-tex-u-atom 0)
    (.uniform1i gl @agents-randomize-u-atom 0)
    (.drawArrays gl gl.TRIANGLES 0 3)
    (flip-textures! agent-tex-front-atom agent-tex-back-atom)

    ; draw points
    (.viewport gl 0 0 substrate-resolution substrate-resolution)
    (.framebufferTexture2D gl
                           gl.FRAMEBUFFER
                           gl.COLOR_ATTACHMENT0
                           gl.TEXTURE_2D
                           @trail-tex-atom
                           0)
    (.clearBufferuiv gl
                     gl.COLOR
                     0
                     (js/Int16Array.
                      (clj->js (repeat 4 (/ uint16-max 2)))))
    (.useProgram gl @trail-program-atom)
    (.activeTexture gl gl.TEXTURE0)
    (.bindTexture gl gl.TEXTURE_2D @agent-tex-front-atom)
    (.uniform1i gl @trail-agent-tex-u-atom 0)
    (.drawArrays gl gl.POINTS 0 (* agent-count-sqrt agent-count-sqrt))

    ; update substrate
    (.viewport gl 0 0 substrate-resolution substrate-resolution)
    (.framebufferTexture2D gl
                           gl.FRAMEBUFFER
                           gl.COLOR_ATTACHMENT0
                           gl.TEXTURE_2D
                           @substrate-tex-back-atom
                           0)
    (.useProgram gl @substrate-program-atom)
    (.activeTexture gl gl.TEXTURE0)
    (.bindTexture gl gl.TEXTURE_2D @substrate-tex-front-atom)
    (.activeTexture gl gl.TEXTURE1)
    (.bindTexture gl gl.TEXTURE_2D @trail-tex-atom)
    (.uniform1i gl @substrate-old-substrate-u-atom 0)
    (.uniform1i gl @substrate-trail-u-atom 1)
    (.drawArrays gl gl.TRIANGLES 0 3)
    (flip-textures! substrate-tex-front-atom substrate-tex-back-atom)))

(defn update-page []
  (resize-canvas (.-canvas @gl-atom))
  (let [gl @gl-atom
        canvas (.-canvas gl)
        draw-program @draw-program-atom]
    (.viewport gl 0 0 canvas.width canvas.width)
    (.bindFramebuffer gl gl.FRAMEBUFFER nil)
    (.clearColor gl 0 0 0 1)
    (.clear gl gl.COLOR_BUFFER_BIT)
    (.useProgram gl draw-program)
    (.uniform1f gl @draw-size-u-atom canvas.width)
    (.activeTexture gl gl.TEXTURE0)
    (.bindTexture gl gl.TEXTURE_2D @substrate-tex-front-atom)
    (.uniform1i gl @draw-substrate-u-atom 0)
    (.drawArrays gl gl.TRIANGLES 0 3))
  (js/requestAnimationFrame update-page)
  (when @auto-update-atom
    (logic-step!)))

(defn ^:dev/after-load restart! []
  (when @gl-atom
    (.remove (.-canvas @gl-atom)))
  (let [canvas (js/document.createElement "canvas")
        gl (.getContext canvas "webgl2")]
    (resize-canvas canvas)
    (set! (.-position canvas.style) "absolute")
    (.appendChild js/document.body canvas)
    (reset! gl-atom gl)

    (let [pos-buffer (.createBuffer gl)]
      (.bindBuffer gl
                   gl.ARRAY_BUFFER
                   pos-buffer)
      (.bufferData gl
                   gl.ARRAY_BUFFER
                   (js/Float32Array.
                    (clj->js [-1 -1
                              -1 3
                              3 -1]))
                   gl.STATIC_DRAW))
    (let [draw-program
          (create-program gl
                          (create-shader gl :vert trivial-vert-source)
                          (create-shader gl :frag draw-frag-source))]
      (reset! draw-program-atom draw-program)
      (reset! draw-size-u-atom
              (.getUniformLocation gl draw-program "size"))
      (reset! draw-substrate-u-atom
              (.getUniformLocation gl draw-program "substrate"))
      (let [attrib (.getAttribLocation gl draw-program "vertPos")]
        (.enableVertexAttribArray gl attrib)
        (.vertexAttribPointer gl
                              attrib
                              2
                              gl.FLOAT
                              false
                              0
                              0)))
    (let [substrate-program
          (create-program gl
                          (create-shader gl :vert trivial-vert-source)
                          (create-shader gl :frag substrate-frag-source))]
      (reset! substrate-program-atom substrate-program)
      (reset! substrate-old-substrate-u-atom
              (.getUniformLocation gl substrate-program "oldSubstrate"))
      (reset! substrate-trail-u-atom
              (.getUniformLocation gl substrate-program "trail"))
      (let [attrib (.getAttribLocation gl substrate-program "vertPos")]
        (.enableVertexAttribArray gl attrib)
        (.vertexAttribPointer gl
                              attrib
                              2
                              gl.FLOAT
                              false
                              0
                              0)))

    (let [agents-program
          (create-program gl
                          (create-shader gl :vert trivial-vert-source)
                          (create-shader gl :frag agents-frag-source))]
      (reset! agents-program-atom agents-program)
      (reset! agents-old-agent-tex-u-atom
              (.getUniformLocation gl agents-program "oldAgentTex"))
      (reset! agents-randomize-u-atom
              (.getUniformLocation gl agents-program "randomize"))
      (let [attrib (.getAttribLocation gl agents-program "vertPos")]
        (.enableVertexAttribArray gl attrib)
        (.vertexAttribPointer gl
                              attrib
                              2
                              gl.FLOAT
                              false
                              0
                              0)))

    (let [trail-program
          (create-program gl
                          (create-shader gl :vert trail-vert-source)
                          (create-shader gl :frag trail-frag-source))]
      (reset! trail-program-atom trail-program)
      (reset! trail-agent-tex-u-atom
              (.getUniformLocation gl trail-program "agentTex")))
    (reset! agent-tex-front-atom
            (create-ui16-tex gl agent-count-sqrt))
    (reset! agent-tex-back-atom
            (create-ui16-tex gl agent-count-sqrt))
    (reset! substrate-tex-front-atom
            (create-ui16-tex gl substrate-resolution))
    (reset! substrate-tex-back-atom
            (create-ui16-tex gl substrate-resolution))
    (reset! trail-tex-atom
            (create-ui16-tex gl substrate-resolution))
    (reset! framebuffer-atom (.createFramebuffer gl)))
  (randomize-agents!))

(defn init []
  (add-key-callback "l" logic-step!)
  (add-key-callback " " #(swap! auto-update-atom not))
  (add-key-callback "r" restart!)
  (set! (.-backgroundColor js/document.body.style)
        (let [[r g b] (map #(min 255
                                 (int (* 256 %)))
                           background-color)]
          (str "rgb("
               r
               ","
               g
               ","
               b
               ")")))
  (restart!)
  (update-page))

(defn pre-init []
  (.addEventListener js/window
                     "load"
                     (fn [_]
                       (init))))
