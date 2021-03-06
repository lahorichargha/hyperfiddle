(ns hypercrud.ui.attribute.instant
  (:require [hypercrud.ui.control.link-controls :as link-controls]
            [hypercrud.ui.control.edn :as edn]
            [hypercrud.ui.control.instant :as instant]
            [hypercrud.browser.link :as link]
            [hypercrud.client.tx :as tx]))


(defn instant [maybe-field links props ctx]
  (let [my-links (link/links-lookup' links [(:fe-pos ctx) (-> ctx :attribute :db/ident)])]
    [:div.value
     [:div.anchors (link-controls/render-links (remove :link/render-inline? my-links) ctx)]
     (let [change! #((:user-with! ctx) (tx/update-entity-attr (:cell-data ctx) (:attribute ctx) %))
           widget (case (:layout ctx) :block instant/date*
                                      :inline-block edn/edn-inline-block*
                                      :table edn/edn-inline-block*)]
       [widget (:value ctx) change! props])
     (link-controls/render-inline-links (filter :link/render-inline? my-links) ctx)]))