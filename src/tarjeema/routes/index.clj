(ns tarjeema.routes.index
  (:require [reitit.core :as r]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [render]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.routes.dashboard :as-alias dashboard]
            [tarjeema.views.index :refer [render-index]]))

(defn index
  [{:as req
    ::r/keys [router]
    :keys [user-data request-method flash params]} res _raise]
  (case request-method
    :get
    (if user-data
      (let [url (get-route-url router ::dashboard/dashboard)]
        (res (res/redirect url :see-other)))
      (let [status (or (:http-status flash) 200)
            error  (:message flash)]
        (-> (render req (render-index {:error error}))
            (res/status status)
            res)))

    :post
    (try
      (let [{:strs [email password redirect]} params]
        (when (some empty? (list email password))
          (throw (ex-info "You need to enter an email and a password." {})))
        (if-let [user (db/auth-user email password)]
          (let [url (or redirect
                        (get-route-url router ::dashboard/dashboard))]
            (-> (res/redirect url :see-other)
                (assoc :user-data user)
                res))
          (throw (ex-info "Invalid email or password." {}))))
      (catch Exception ex
        (-> (render req (render-index {:error (ex-message ex)}))
            (res/bad-request)
            res)))

    (-> (res/response "Method not allowed.")
        (res/status 405)
        res)))

(def routes
  ["/" {:handler    #'index
        :name       ::index
        :middleware [[wrap-params]]
        :auth       false}])
