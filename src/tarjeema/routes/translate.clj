(ns tarjeema.routes.translate
  (:require [clojure.string :as str]
            [reitit.core :as r]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [with-request-data]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.views.translate :refer [render-translate]]))

(defmulti ^:private handle-action :action)
(defmethod handle-action :suggest [{:keys [string lang user text]}]
  (db/suggest-translation {:string_id        (:string_id string)
                           :lang_id          (:lang_id lang)
                           :user_id          (:user_id user)
                           :translation_text text}))

(defn translate
  [{:as req
    :keys [user-data]
    ::r/keys [router]
    {:strs [project action lang string] :as params} :params} res _raise]
  ;; TODO: validate params
  (let [project-id     (parse-long project)
        lang           (get (db/get-languages) (keyword lang))
        project        (db/find-project-by-id project-id)
        strings        (db/get-strings project-id)
        mk-string-href #(get-route-url router ::translate
                                       :query-params
                                       {:project project-id
                                        :lang    (:bcp47 lang)
                                        :string  (:string_id %)})]
    (cond
      (str/blank? string)
      (res (res/redirect (mk-string-href (first strings))))

      :else
      (let [string-id (parse-long string)]
        (when-not (str/blank? action)
          (let [opts {:action  (keyword action)
                      :string  {:string_id string-id}
                      :lang    lang
                      :user    user-data}
                opts (case (:action opts)
                       :suggest (assoc opts :text (get params "text")))]
            (handle-action opts)))
        (let [string-info  (db/get-string-info string-id)
              translations (db/get-translations string-id (:lang_id lang))]
          (-> (with-request-data req
                (render-translate {:project        project
                                   :lang           lang
                                   :strings        strings
                                   :mk-string-href mk-string-href
                                   :current-string string-info
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
