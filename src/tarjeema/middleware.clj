(ns tarjeema.middleware
  (:require [reitit.core :as r]))

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
