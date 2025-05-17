(ns tarjeema.middleware
  (:require [ring.util.response :as res]
            [reitit.core :as r]
            [tarjeema.db :as-alias db]
            [tarjeema.model :as model]
            [toucan2.core :as t2]))

(defn wrap-user-data
  "Middleware that retrieves and writes user data based on session data."
  [handler]
  (fn [{:keys [session] :as req} res raise]
    (let [{:keys [user]} session
          req  (if user
                 (assoc req :user-data user)
                 req)
          res' (fn [response-map]
                 (if (contains? response-map :user-data)
                   (if-let [user-data (:user-data response-map)]
                     (res (-> response-map
                            (assoc :session (assoc session :user user-data))
                            (dissoc :user-data)))
                     (res (-> response-map
                              (assoc :session nil)
                              (dissoc :user-data))))
                   (res response-map)))]
      (handler req res' raise))))

(defn wrap-auth
  "Middleware that allows a user to a specific route based on whether they are
  authenticated. Should come after `wrap-user-data`."
  [handler {:keys [on-unauthenticated]}]
  (fn [{:as req
        :keys [user-data]
        {{:keys [auth]} :data} ::r/match} res raise]
    (if auth
      (if user-data
        (handler req res raise)
        (on-unauthenticated req res raise))
      (handler req res raise))))

(defn wrap-project
  [handler f]
  (fn [{:as req :keys [user-data]} res raise]
    (let [{:keys [project-id]} (f req)
          project   (t2/select-one ::db/project project-id)]
      (when (nil? project)
        (throw (ex-info "Project not found." {:type        :input-error
                                              :http-status 404})))
      (handler (assoc req
                      :project   project
                      :user-data (some-> user-data
                                         (model/user-in-project project)))
               res raise))))

(defn wrap-roles
  [handler accept-role?]
  (fn [{:as req :keys [user-data]} res raise]
    (if (some accept-role? (:roles user-data))
      (handler req res raise)
      (-> (res/response "Forbidden.")
          (res/status 403)
          res))))
