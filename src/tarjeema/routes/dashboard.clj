(ns tarjeema.routes.dashboard
  (:require [reitit.core :as r]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [render]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.routes.project :as-alias project]
            [tarjeema.routes.create-project :as-alias crp]
            [tarjeema.views.dashboard :refer [render-dashboard]]
            [toucan2.core :as t2]))

(defn dashboard
  [{:as req, ::r/keys [router]} res _raise]
  (let [projects           (t2/select ::db/project
                                      {:order-by [:project-id]})
        mk-project-url     #(get-route-url router
                                           ::project/project-view
                                           :path-params {:id (:project-id %)})
        create-project-url (get-route-url router ::crp/create-project)]
    (-> (render req
          (render-dashboard {:projects           projects
                             :mk-project-url     mk-project-url
                             :create-project-url create-project-url}))
        res)))

(def routes
  ["/dashboard" {:get  #'dashboard
                 :name ::dashboard}])
