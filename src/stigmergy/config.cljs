(ns stigmergy.config)

(def logic-factor 10)

(def agent-count-sqrt 500)

(def substrate-resolution 2048)
(def point-size 0.001)
(def gaussian-radius 3)
(def gaussian-sigma 0.4)
(def trail-opacity 0.0075)
(def substrate-fade-factor 0.01)
(def sensor-distance 0.05)
(def sensor-spread 0.7)
(def agent-speed-factor 0.0002)
(def angle-change-factor 0.002)

(def background-color
  [0 0 0])

(def uint16-max 65535)

(def saved-population
  '[(2.014512823653974 :c-sinh :c-neg :c-cos :c-sinh -0.23104015612339507 :c-- :- :swap) 
    (-1.871302235145471 :tan :swap2 :cosh 1.4803581120268703 :drop :swap3 1.3680428588570013 :+ :tan :sqrt -0.6034219380774194 :abs :drop3 :mod :c--) 
    (2.014512823653974 :c-sinh :c-neg :c-cos :c-sinh -0.23104015612339507 :c-- :- 0.4246851738380309 :c-sinh :swap2 :min) 
    (:dup2-2 :c-inv :abs :tanh :drop3 :ln :swap2)])
