(ns tarjeema.app
  (:require [mount.core :as mount :refer [defstate]]
            [org.httpkit.server :as hk-server]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.middleware.defaults :refer [wrap-defaults]]
            [ring.util.response :as res]
            [tarjeema.config :refer [config]]
            [tarjeema.middleware :refer [wrap-user-data wrap-auth]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.routes.create-project]
            [tarjeema.routes.dashboard]
            [tarjeema.routes.index]
            [tarjeema.routes.project]
            [tarjeema.routes.translate]
            [tarjeema.util :refer [this-uri]]))

(defn on-unauthenticated
  [{:as req, ::r/keys [router]} res _raise]
  (let [uri  (this-uri req)
        path (get-route-url router
                            :tarjeema.routes.index/index
                            :query-params {:redirect uri})]
    (-> (res/redirect path :see-other)
        (assoc :flash
               {:http-status 400
                :message     "You need to sign in to continue."})
        res)))

(defn logout [_req res _raise]
  (-> (res/redirect "/" :see-other)
      (assoc :user-data nil)
      res))

(def app
  (ring/ring-handler
   (ring/router
    [tarjeema.routes.create-project/routes
     tarjeema.routes.dashboard/routes
     tarjeema.routes.index/routes
     tarjeema.routes.project/routes
     tarjeema.routes.translate/routes
     ["/logout" {:get #'logout}]]
    {:data
     {:middleware [[wrap-auth {:on-unauthenticated on-unauthenticated}]]
      :auth       true}})
   (ring/create-default-handler)
   {:middleware [[wrap-defaults
                  {:responses {:content-types   true
                               :default-charset "utf-8"}
                   :session   {:flash        true
                               :cookie-attrs {:http-only true}}
                   :cookies   true
                   :static    {:resources "public"}}]
                 [wrap-user-data]]}))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defstate server
  :start (let [{:keys [port]} config
               opts   {:port        port
                       :ring-async? true}
               server (hk-server/run-server #'app opts)]
           (println "Started server at port" port)
           server)
  :stop  (do
           (println "Gracefully shutting down...")
           (server :timeout 100)))

(defn -main [& _args]
  (mount/start))
