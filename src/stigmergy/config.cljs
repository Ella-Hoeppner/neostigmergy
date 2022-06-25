(ns stigmergy.config)

(def agent-count-sqrt 5)

(def substrate-resolution 1024)
(def point-size 0.03)
(def gaussian-radius 3)
(def gaussian-sigma 0.4)
(def trail-opacity 0.015)
(def substrate-fade-factor 0.999)
(def sensor-distance 0.05)
(def agent-speed-factor 0.001)

(def background-color
  [0 0 0])

(def uint16-max 65535)

(def saved-population
  '[(:c-cosh :c-exp :dup2-2 :tan :c-* :sinh :c-- :dup1-3 0.9301410742508656 :dup2-2 :dup2-4 :- :mod :exp 1.266421641057302 :c-- :dup1-2 0.5945562267664747 :dup2-3 :pow :dup1-3 :c-- :swap :+ :c-sinh :swap3 -0.05066713343781146 :c-tanh -0.8183881331409008 :dup1-3 :drop3 :c-cos :tan -1.7183493226008348 :sinh :* :ln :swap3 :tan -1.9117247732343436 -2.635263536243375 :mod :swap2)
    (:floor :c-tanh :c-cosh -0.6671458683109922 :c-tan :inv :c-cosh :exp :min :tanh :sqrt 1.0326194423545472 :cos :c-inv :exp :floor :drop2 :c-* :dup2-2 :c-cosh :dup2-4 :c-ln :tanh :swap2 :ceil :swap3 :exp :swap2)
    (:c-neg :floor :dup1-2 :exp :sin :max :+ :exp :cosh :dup1-4 :sinh -0.8897847977972198 :drop3 :tan 0.6966269728486465 :dup1-2 :- :exp :c-cosh :c-inv :div :if :c-- :dup2-3 :sigmoid)
    (:c-neg :c-tan :c-inv :sqrt :c-- 0.505599308314578 :max :dup2-3 :floor -0.9868219662968754 :exp :-)
    (:min :c-tan :c-inv :drop :c-tanh :- :c-exp :sqrt :c-- 5.225963925609793 :+ :dup1-4 :* :swap2 :dup2-3 :c-tan 0.5163738899581747 :c-inv :tan :swap :c-tanh -0.00449915622004487 1.6519547333256055 -1.023365682758296)
    (:c-neg :c-tan :c-inv :sqrt :c-- 0.505599308314578 :max :dup2-3 :floor :+ :c-sinh :swap3 -0.05066713343781146 :c-tanh -0.8183881331409008 :dup1-3 :drop3 :c-cos :tan -1.7183493226008348 :sinh :* :ln :swap3 :tan -1.9117247732343436 -2.635263536243375 :mod :swap2)
    (:c-neg :floor :dup1-2 :exp :sin :max :+ :exp :cosh :dup1-4 :sinh :drop3 :tan 0.6966269728486465 :dup1-2 :exp :c-cosh :if :c-- :dup2-3 :sigmoid)
    (:c-neg :floor :dup1-2 :exp :sin :max :+ :exp :cosh :dup1-4 :sinh -0.8897847977972198 :drop3 :tan 0.6966269728486465 :c-cosh -0.6671458683109922 :c-tan :inv :c-cosh :exp :min :tanh :sqrt 1.0326194423545472 :cos :c-inv :exp :floor :drop2 :c-* :dup2-2 :c-cosh :dup2-4 :c-ln :tanh :swap2 :ceil :swap3 :exp :swap2)])
