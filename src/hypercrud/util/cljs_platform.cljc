(ns hypercrud.util.cljs-platform
  #?(:cljs (:require-macros [hypercrud.util.cljs-platform :refer [code-for-nodejs]])))


(defn- nodejs-target? []
  (= :nodejs (get-in @cljs.env/*compiler* [:options :target])))

; a compile time check for environment
; most of the time a runtime check will do (= "nodejs" *target*)
; which is also public api, unlike these macros
; https://stackoverflow.com/a/47499855
(defmacro code-for-nodejs
  [& body]
  (when (nodejs-target?)
    `(do ~@body)))

(defmacro code-for-browser
  [& body]
  (when-not (nodejs-target?)
    `(do ~@body)))
