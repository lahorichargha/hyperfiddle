(ns hypercrud.ui.form
  (:require [hypercrud.browser.links :as links]
            [hypercrud.compile.eval :refer [eval]]
            [hypercrud.form.option :as option]
            [hypercrud.types :refer [->DbVal]]
            [hypercrud.ui.auto-control :refer [auto-control]]))


(defn field [entity {:keys [graph links navigate-cmp stage-tx! param-ctx]
                     {:keys [:field/prompt :field/renderer] :as field} :field :as widget-args}]
  [:div.field
   [:label
    (let [docstring (-> field :field/attribute :attribute/doc)]
      (if-not (empty? docstring)
        [:span.help {:on-click #(js/alert docstring)} prompt])
      prompt)]
   (if (empty? renderer)
     [auto-control entity widget-args]
     (let [{renderer :value error :error} (eval renderer)
           repeating-links (->> links
                                (filter :link/repeating?)
                                (mapv (juxt :link/ident identity))
                                (into {}))
           link-fn (fn [ident label]
                     (let [link (get repeating-links ident)
                           props (links/query-link stage-tx! link param-ctx)]
                       [navigate-cmp props label]))]
       [:div.value
        (if error
          (pr-str error)
          (try
            (renderer graph link-fn entity)
            (catch :default e (pr-str e))))]))])


(defn form [graph entity form links stage-tx! navigate-cmp param-ctx]
  [:div.form
   (->> (:form/field form)
        (sort-by :field/order)
        (map (fn [fieldinfo]
               (let [ident (-> fieldinfo :field/attribute :attribute/ident)]
                 ^{:key ident}
                 [field entity {:field fieldinfo
                                :graph graph
                                :links links
                                :navigate-cmp navigate-cmp
                                :param-ctx param-ctx
                                :stage-tx! stage-tx!}]))))])


(defn form-pull-exp [form]
  (concat
    [:db/id]
    (remove nil? (mapv #(-> % :field/attribute :attribute/ident) (:form/field form)))))


(defn query-pull-exp [find-elements]
  (->> find-elements
       (mapv (juxt :find-element/name (fn [{:keys [:find-element/connection :find-element/form]}]
                                        [(->DbVal (-> connection :db/id :id) nil) (form-pull-exp form)])))
       (into {})))


(defn field-queries [p-filler param-ctx field]
  (let [{:keys [:attribute/valueType :attribute/isComponent]} (:field/attribute field)
        is-ref (= (:db/ident valueType) :db.type/ref)]
    ; if we are a ref we ALWAYS need the query from the field options
    ; EXCEPT when we are component, in which case no options are rendered, just a form, handled below
    (if (and is-ref (not isComponent))
      (option/get-query field p-filler param-ctx))))


(defn form-option-queries "get the form options recursively for all expanded forms"
  [form p-filler param-ctx]
  (apply merge
         (mapv #(field-queries p-filler param-ctx %) (:form/field form))))
