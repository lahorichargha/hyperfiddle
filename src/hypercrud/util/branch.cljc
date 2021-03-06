(ns hypercrud.util.branch
  (:require [clojure.string :as str]))


(defn decode-parent-branch [branch]
  (let [parent (second (re-find #"(.*)`.*" branch))]
    (if (str/blank? parent)
      nil
      parent)))

(defn encode-branch-child [parent-branch child-id-str]
  (if (str/blank? child-id-str)
    parent-branch
    (str parent-branch "`" child-id-str)))

(defn get-all-branches [branch]
  (if branch
    (conj (get-all-branches (decode-parent-branch branch)) branch)
    [branch]))

(defn branch-val [uri branch stage-val]
  (->> (get-all-branches branch)
       (reduce (fn [tx branch]
                 (concat tx (get-in stage-val [branch uri])))
               nil)
       hash))
