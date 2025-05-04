(ns tarjeema.routes.translate
  (:require [clojure.string :as str]
            [reitit.core :as r]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [with-request-data]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.views.translate :refer [render-translate]]
            [toucan2.core :as t2]))

(defmulti ^:private handle-action
  (fn [{:keys [action]} _params] action))

(defmethod handle-action :suggest
  [{:keys [string lang user]} form-params]
  (let [text (get form-params "text")]
    (when (str/blank? text)
      (throw (ex-info "Translation text should be provided."
                      {:type :input-error})))
    (db/suggest-translation {:string-id        (:string-id string)
                             :lang-id          (:lang-id lang)
                             :user-id          (:user-id user)
                             :translation-text text})))

(defn translate
  [{:as req
    :keys [user-data form-params]
    ::r/keys [router]
    {:strs [project action lang string]} :params} res _raise]
  ;; TODO: validate params
  (let [project-id     (parse-long project)
        lang           (get (db/get-languages) lang)
        strings        (db/get-strings {:project-id project-id})
        mk-string-href #(get-route-url router ::translate
                                       :query-params
                                       {:project project-id
                                        :lang    (:bcp-47 lang)
                                        :string  (:string-id %)})]
    (cond
      (str/blank? string)
      (res (res/redirect (mk-string-href (first strings))))

      :else
      (let [string-id (parse-long string)]
        (when-not (str/blank? action)
          (let [ctx {:action  (keyword action)
                     :string  {:string-id string-id}
                     :lang    lang
                     :user    user-data}]
            (handle-action ctx form-params)))
        (let [project      (t2/select-one ::db/project project-id)
              string       (t2/select-one ::db/string string-id)
              translations (-> (t2/select ::db/translation
                                          :string-id string-id
                                          :lang-id   (:lang-id lang)
                                          {:order-by [[:suggested-at :desc]]})
                               (t2/hydrate :user))]
          (-> (with-request-data req
                (render-translate {:project        project
                                   :lang           lang
                                   :strings        strings
                                   :mk-string-href mk-string-href
                                   :current-string string
                                   :translations   translations}))
              str
              (res/response)
              (res/content-type "text/html")
              res))))))

(def routes
  ["/translate" {:handler    #'translate
                 :name       ::translate
                 :middleware [[wrap-params]]
                 :auth       true}])
