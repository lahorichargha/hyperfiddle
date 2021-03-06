(ns hypercrud.api.http
  (:require [cuerdas.core :as str]
            [hypercrud.api.util :as api-util]
            [hypercrud.http.core :refer [request!]]
            [hypercrud.util.base-64-url-safe :as base-64-url-safe]
            [promesa.core :as p]
            [taoensso.timbre :as timbre]))


(defn stateful-req [service-uri path state-val path-params]
  ; todo this list needs to be overridable
  ; todo use bide for building the url
  (merge {:url (->> (select-keys state-val [:encoded-route
                                            :global-basis
                                            :local-basis
                                            :popovers])
                    pr-str
                    base-64-url-safe/encode
                    (str #?(:clj service-uri :cljs (.-uri-str service-uri)) path "/" (if path-params (str path-params "/"))))
          :accept :application/transit+json :as :auto}
         (if (empty? (:stage state-val))
           {:method :get}                                   ; Try to hit CDN
           {:method :post
            :form (:stage state-val)
            :content-type :application/transit+json})))

(defn global-basis! [service-uri]
  (-> (request! {:url (str #?(:clj service-uri :cljs (.-uri-str service-uri)) "global-basis")
                 :accept :application/transit+json :as :auto
                 :method :get})
      (p/then :body)))

(defn local-basis! [service-uri state-val & [path-params]]
  (-> (stateful-req service-uri "local-basis" state-val path-params)
      (request!)
      (p/then :body)))

(defn hydrate-route! [service-uri state-val & [path-params]]
  (-> (stateful-req service-uri "hydrate-route" state-val path-params)
      (request!)
      (p/then :body)))

(defn hydrate-requests! [service-uri local-basis stage-val requests]
  (let [staged-branches (api-util/stage-val->staged-branches stage-val)
        ; Note the UI-facing interface contains stage-val; the API accepts staged-branches
        req {:url (str #?(:clj service-uri :cljs (.-uri-str service-uri)) "hydrate-requests/" ((comp base-64-url-safe/encode pr-str) local-basis)) ; serialize kvseq
             :accept :application/transit+json :as :auto
             :method :post                                  ; hydrate-requests always has a POST body, though it has a basis and is cachable
             :form {:staged-branches staged-branches :request requests}
             :content-type :application/transit+json}]
    (timbre/debugf "hydrate-requests! request count= %s basis= %s form= %s" (count requests) (pr-str local-basis) (str/prune (pr-str (:form req)) 100))
    (-> (request! req)
        (p/then (fn [{:keys [body]}]
                  (assert (= (count requests) (count (:pulled-trees body))) "Server contract violation; mismatched counts")
                  body)))))

(defn transact!! [service-uri tx-groups]
  (-> (request!
        {:url (str #?(:clj service-uri :cljs (.-uri-str service-uri)) "transact")
         :accept :application/transit+json :as :auto
         :method :post :form tx-groups
         :content-type :application/transit+json})
      (p/then (fn [resp]
                (if (= 200 (:status resp))
                  ; clear master stage
                  ; but that has to be transactional with a redirect???
                  (p/resolved (:body resp))
                  (p/rejected resp))))))

(defn sync! [service-uri dbs]
  (-> (request! {:url (str #?(:clj service-uri :cljs (.-uri-str service-uri)) "sync")
                 :accept :application/transit+json :as :auto
                 :method :post :form dbs
                 :content-type :application/transit+json})
      (p/then (fn [{:keys [body]}]
                (->> body
                     (apply concat)
                     (apply sorted-map))))))
