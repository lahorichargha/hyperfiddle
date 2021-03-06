(ns hypercrud.browser.routing
  (:require [cats.core :as cats :refer [mlet]]
            [cats.monad.either :as either #?(:clj :refer :cljs :refer-macros) [try-either]]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [hypercrud.browser.dbname :as dbname]
            [hypercrud.compile.eval :as eval]
            [hypercrud.compile.reader :as reader]
            [hypercrud.types.Entity :refer [->Entity #?(:cljs Entity)]]
            [hypercrud.types.ThinEntity :refer [->ThinEntity #?(:cljs ThinEntity)]]
            [hypercrud.util.base-64-url-safe :as base64]
            [hypercrud.util.reactive :as reactive])
  #?(:clj
     (:import (hypercrud.types.Entity Entity)
              (hypercrud.types.ThinEntity ThinEntity))))


(defn invert-ids [route invert-id ctx]
  (-> (walk/postwalk (fn [v]
                       (cond
                         (instance? Entity v) (assert false "hyperfiddle/hyperfiddle.net#150")

                         (instance? ThinEntity v)
                         (let [uri (get-in ctx [:repository :repository/environment (.-dbname v)])
                               id (invert-id (.-id v) uri)]
                           (->ThinEntity (.-dbname v) id))

                         :else v))
                     route)
      (update :link-id (let [uri (get-in ctx [:repository :dbhole/uri])]
                         #(invert-id % uri)))))

(defn ctx->id-lookup [uri ctx]
  ; todo what about if the tempid is on a higher branch in the uri?
  @(reactive/cursor (.-state-atom (:peer ctx)) [:tempid-lookups uri (:branch ctx)]))

(defn id->tempid [route ctx]
  (let [invert-id (fn [id uri]
                    (let [id->tempid (ctx->id-lookup uri ctx)]
                      (get id->tempid id id)))]
    (invert-ids route invert-id ctx)))

(defn tempid->id [route ctx]
  (let [invert-id (fn [temp-id uri]
                    (let [tempid->id (-> (ctx->id-lookup uri ctx)
                                         (set/map-invert))]
                      (get tempid->id temp-id temp-id)))]
    (invert-ids route invert-id ctx)))

(defn ^:export build-route' [link ctx]
  (mlet [fiddle-id (if-let [page (:link/fiddle link)]
                     (either/right (:db/id page))
                     (either/left {:message "link has no fiddle" :data {:link link}}))
         user-route-params (if (eval/validate-user-code-str (:link/formula link))
                             (mlet [user-fn (eval/eval-str (:link/formula link))]
                               (if user-fn
                                 (try-either (user-fn ctx))
                                 (cats/return nil)))
                             (either/right nil))
         :let [route-params (->> user-route-params
                                 (walk/postwalk (fn [v]
                                                  (if (instance? Entity v)
                                                    (let [dbname (some-> v .-uri (dbname/uri->dbname ctx))]
                                                      (->ThinEntity dbname (:db/id v)))
                                                    v))))]]
    (cats/return
      (id->tempid
        (merge (into {} route-params)
               {
                ;:code-database (:link/code-database link) todo when cross db references are working on links, don't need to inherit code-db-uri
                :code-database (get-in ctx [:repository :dbhole/name])
                :link-id fiddle-id})
        ctx))))

(defn encode
  ([route]
   (str "/" (some-> route pr-str base64/encode)))
  ([route external-hostname]
   (str (some->> external-hostname (str "http://"))
        (encode route))))

(defn decode [route-str]
  (assert (string/starts-with? route-str "/"))

  ; Urls in the wild get query params added because tweetdeck tools think its safe e.g.:
  ; http://localhost/hyperfiddle-blog/ezpkb21haW4gbmlsLCA6cHJvamVjdCAiaHlwZXJmaWRkbGUtYmxvZyIsIDpsaW5rLWRiaWQgI0RiSWRbMTc1OTIxODYwNDU4OTQgMTc1OTIxODYwNDU0MjJdLCA6cXVlcnktcGFyYW1zIHs6ZW50aXR5ICNEYklkWzE3NTkyMTg2MDQ2MTMyIDE3NTkyMTg2MDQ1ODgyXX19?utm_content=buffer9a24a&utm_medium=social&utm_source=twitter.com&utm_campaign=buffer
  (let [[_ route-encoded-and-query-params] (string/split route-str #"/")]
    (cond
      (not (nil? route-encoded-and-query-params))
      (let [[route-encoded url-param-garbage] (string/split route-encoded-and-query-params #"\?")]
        (reader/read-string (base64/decode route-encoded)))

      ; The only way to get to / is if the user types it in. We never ever link to /, and nginx & node should redirect to the canonical.
      :else nil)))
