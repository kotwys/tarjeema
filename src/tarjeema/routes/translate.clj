(ns tarjeema.routes.translate
  (:require [better-cond.core :as b]
            [clojure.string :as str]
            [methodical.core :as m]
            [reitit.core :as r]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [with-request-data]]
            [tarjeema.model :as model]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.views.translate :refer [render-translate]]
            [tarjeema.util :refer [this-uri]]
            [toucan2.core :as t2]))

(m/defmulti ^:private handle-action
  (fn [{:keys [action]} _params] action)
  :combo (m/thread-first-method-combination))

(m/defmethod handle-action ::suggest
  [{:keys [string lang user]} form-params]
  (let [text (get form-params "text")]
    (when (str/blank? text)
      (throw (ex-info "Translation text should be provided."
                      {:type :input-error})))
    (db/suggest-translation {:string-id        (:string-id string)
                             :lang-id          (:lang-id lang)
                             :user-id          (:user-id user)
                             :translation-text text})))

(derive ::delete-translation ::operates-on-translation)
(derive ::approve ::operates-on-translation)
(derive ::disapprove ::operates-on-translation)

(m/defmethod handle-action :before ::operates-on-translation [ctx form-params]
  (b/cond
    :let [translation-id (some-> form-params
                                 (get "translation-id")
                                 parse-long)]
    (nil? translation-id)
    (throw (ex-info "Translation ID (integer) should be provided."
                    {:type :input-error, :http-status 400}))

    :let [translation (t2/select-one ::db/translation translation-id)]
    (nil? translation)
    (throw (ex-info "Translation not found."
                    {:type :input-error, :http-status 404}))

    (assoc ctx :translation translation)))

(m/defmethod handle-action ::delete-translation [{:keys [user translation]} _]
  (if (model/can-delete-translation? user translation)
    (t2/delete! ::db/translation (:translation-id translation))
    (throw (ex-info "Unauthorised to delete translation."
                    {:type :access-error, :http-status 403}))))

(m/defmethod handle-action ::approve [{:keys [user translation]} _]
  (if (model/can-approve? user)
    (db/approve-translation translation user)
    (throw (ex-info "Unauthorised to approve translation."
                    {:type :access-error, :http-status 403}))))

(m/defmethod handle-action ::disapprove [{:keys [user translation]} _]
  (if (model/can-approve? user)
    (db/disapprove-translation translation)
    (throw (ex-info "Unauthorised to approve translation."
                    {:type :access-error, :http-status 403}))))

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
    (b/cond
      (str/blank? string)
      (res (res/redirect (mk-string-href (first strings))))

      :let [string-id (parse-long string)
            project   (t2/select-one ::db/project project-id)
            user-data (model/user-in-project user-data project)
            req       (assoc req :user-data user-data)]

      (not (str/blank? action))
      (let [ctx {:action  (keyword (namespace ::here) action)
                 :string  {:string-id string-id}
                 :lang    lang
                 :user    user-data}]
        (handle-action ctx form-params)
        (res (res/redirect (this-uri req) :see-other)))

      :let [string       (t2/select-one ::db/string string-id)
            translations (-> (t2/select ::db/translation
                                        :string-id string-id
                                        :lang-id   (:lang-id lang)
                                        {:order-by [[:suggested-at :desc]]})
                             (t2/hydrate :user [:approval :user]))]
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
          res))))

(def routes
  ["/translate" {:handler    #'translate
                 :name       ::translate
                 :middleware [[wrap-params]]
                 :auth       true}])
