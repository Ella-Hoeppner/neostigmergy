(ns stigmergy.config)

(def agent-count-sqrt 500)

(def substrate-resolution 1024)
(def point-size 0.003)
(def gaussian-radius 3)
(def gaussian-sigma 0.4)
(def trail-opacity 0.025)
(def substrate-fade-factor 0.999)
(def sensor-distance 0.05)
(def agent-speed-factor 0.001)

(def background-color
  [0 0 0])

(def uint16-max 65535)

(def saved-population
  '[(:c-cosh :c-exp :dup2-2 :tan :c-* :sinh :c-- :dup1-3 0.9301410742508656 :dup2-2 :dup2-4 :- :mod :exp 1.266421641057302 :c-- :dup1-2 0.5945562267664747 :dup2-3 :pow :dup1-3 :c-- :swap :+ :c-sinh :swap3 -0.05066713343781146 :c-tanh -0.8183881331409008 :dup1-3 :drop3 :c-cos :tan -1.7183493226008348 :sinh :* :ln :swap3 :tan -1.9117247732343436 -2.635263536243375 :mod :swap2)]
  )
