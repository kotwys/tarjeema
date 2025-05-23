(ns tarjeema.routes.translate
  (:require [better-cond.core :as b]
            [clojure.string :as str]
            [methodical.core :as m]
            [reitit.core :as r]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [with-request-data]]
            [tarjeema.middleware :refer [wrap-project]]
            [tarjeema.model :as model]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.views.translate :refer [render-translate]]
            [tarjeema.util :refer [this-uri]]
            [toucan2.core :as t2]))

(defn- extract-project
  [{{:strs [project lang]} :params}]
  (b/cond
    :let [project-id (some-> project parse-long)]
    (nil? project-id)
    (throw (ex-info "Project ID should be provided." {}))

    (str/blank? lang)
    (throw (ex-info "Language should be provided." {}))

    {:project-id project-id
     :bcp-47     lang}))

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
(derive ::vote ::operates-on-translation)

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

(m/defmethod handle-action ::vote [{:keys [user translation]} {:strs [value]}]
  (let [value (parse-boolean value)]
    (t2/delete! ::db/vote
                :translation-id (:translation-id translation)
                :user-id        (:user-id user))
    (when-not (nil? value)
      (t2/insert! ::db/vote
                  :translation-id (:translation-id translation)
                  :user-id        (:user-id user)
                  :is-in-favor    value))))

(m/defmethod handle-action ::comment
  [{:keys [string user]} {:strs [comment]}]
  (when (str/blank? comment)
    (throw (ex-info "Comment text should be provided." {:type :input-error})))
  (t2/insert-returning-instance! ::db/comment
                                 {:user-id      (:user-id user)
                                  :string-id    (:string-id string)
                                  :comment-text comment}))

(m/defmethod handle-action ::delete-comment
  [{:keys [user]} {:strs [comment-id]}]
  (b/cond
    :let [comment-id (some-> comment-id parse-long)]
    (nil? comment-id)
    (throw (ex-info "Comment ID should be provided." {}))

    :let [comment (t2/select-one ::db/comment comment-id)]
    (nil? comment)
    (throw (ex-info "Comment not found." {}))

    (not (model/can-delete-comment? user comment))
    (throw (ex-info "Not authorised to delete comment." {}))

    (t2/delete! ::db/comment comment-id)))

(defn translate
  [{:as req
    :keys [project lang user-data form-params]
    ::r/keys [router]
    {:strs [action string]} :params} res _raise]
  (let [strings        (db/get-strings project lang)
        mk-string-href #(get-route-url router ::translate
                                       :query-params
                                       {:project (:project-id project)
                                        :lang    (:bcp-47 lang)
                                        :string  (:string-id %)})]
    (b/cond
      (str/blank? string)
      (res (res/redirect (mk-string-href (first strings))))

      :let [string-id (parse-long string)]

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
                             (t2/hydrate :user [:approval :user] :rating)
                             (db/get-votes user-data))
            comments     (-> (t2/select ::db/comment
                                        :string-id string-id
                                        {:order-by [[:posted-at :asc]]})
                             (t2/hydrate :user))]
      (-> (with-request-data req
            (render-translate {:project        project
                               :lang           lang
                               :strings        strings
                               :mk-string-href mk-string-href
                               :current-string string
                               :translations   translations
                               :comments       comments}))
          str
          (res/response)
          (res/content-type "text/html")
          res))))

(def routes
  ["/translate" {:handler    #'translate
                 :name       ::translate
                 :middleware [[wrap-params]
                              [wrap-project #'extract-project]]}])
