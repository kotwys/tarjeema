(ns tarjeema.routes.join
  (:require [clojure.string :as str]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [render]]
            [tarjeema.views.join :refer [render-join
                                         render-message]]
            [toucan2.core :as t2]))

(defn with-invite [handler]
  (fn [{:as req, {:keys [code]} :path-params} res raise]
    (let [invite (t2/select-one ::db/invite
                                :invite-code (str/lower-case code))]
      (when (or (nil? invite) (not (db/valid-invite? invite)))
        (let [opts {:alert {:message "Invalid invite."
                            :kind    :danger}}]
          (-> (render req (render-message opts))
              (res/status 400)
              res)))
      (handler (assoc req :invite invite) res raise))))

(defn parse-join-data [{:strs [name email password]}]
  (when (str/blank? name)
    (throw (ex-info "You need to provide your name." {})))
  (when (str/blank? email)
    (throw (ex-info "You need to provide your email." {})))
  (when (str/blank? password)
    (throw (ex-info "You need to create a password." {})))
  {:user-name  name
   :user-email email
   :password   password})

(defn join
  [{:as req :keys [invite params request-method]} res _raise]
  (case request-method
    :get
    (-> (render req
          (render-join {}))
        res)

    :post
    (try
      (let [data (parse-join-data params)
            user (db/register-user data :invite invite)]
        (-> (res/redirect "/" :see-other)
            (assoc :user-data user)
            res))
      (catch Exception ex
        (let [opts {:alert {:kind    :danger
                            :message (ex-message ex)}}]
          (-> (render req (render-join opts))
              (res/status 400)
              res))))))

(def routes
  ["/join/:code" {:get  #'join
                  :post {:handler    #'join
                         :middleware [[wrap-params]]}
                  :name ::join
                  :middleware [[with-invite]]
                  :auth false}])
