(ns hypercrud-client.hacks)


(defn entity? [e] (boolean (:rel e)))


(defn entity-coll? [os] (and (set? os) (every? :rel os)))


(defn hc-node->datoms [{:keys [rel data] :as hc-node}]
  (->> data
       (map (fn [[attr val]]
              [attr (cond
                      (entity-coll? val) (map :rel val)
                      (entity? val) (:rel val)
                      :else-primative val)]))
       (into {})
       (mapcat (fn [[attr val]]
                 (cond
                   (coll? val) (map (fn [v] [:db/add rel attr val]) val)
                   :else [[:db/add rel attr val]])))
       (into [])))


(comment
  (def hc-data
    {:rel 1
     :data
     {:community/name "At Large in Ballard",
      :community/url "http://blog.seattlepi.com/ballard/",
      :community/neighborhood {:rel :17592186045456},
      :community/category #{"news" "human interest"},
      :community/orgtype {:rel :17592186045418},
      :community/type #{{:rel :17592186045424}}}})

  (= (hc-node->datoms hc-data)
     [[1 :community/neighborhood :17592186045456]
      [1 :community/type :17592186045424]
      [1 :community/orgtype :17592186045418]
      [1 :community/name "At Large in Ballard"]
      [1 :community/category "news"]
      [1 :community/category "human interest"]
      [1 :community/url "http://blog.seattlepi.com/ballard/"]])

  (hc-node->typetag hc-data)
  )


(defn hc-node->typetag [{:keys [data] :as hc-node}]
  (namespace (first (keys data))))


(defn hc-template->typetag [{:keys [data] :as template}]
  (-> data first :name namespace))