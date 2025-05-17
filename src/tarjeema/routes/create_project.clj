(ns tarjeema.routes.create-project
  (:require [methodical.core :as m]
            [reitit.core :as r]
            [ring.middleware.multipart-params :refer [parse-multipart-params]]
            [ring.middleware.multipart-params.byte-array
             :refer [byte-array-store]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.form.project :refer [create-or-update-project]]
            [tarjeema.macros :refer [render]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.routes.project :as-alias project]
            [tarjeema.views.project :refer [render-create-project]]))

(m/defmulti create-project
  (fn [{:keys [request-method]} _ _] request-method)
  :combo (m/thread-first-method-combination))

(m/defmethod create-project :before :default [req _ _]
  (assoc req ::directory {:languages (vals (db/get-languages))}))

(m/defmethod create-project :get
  [{:as req ::keys [directory]} res _raise]
  (res (render req (render-create-project {:directory directory}))))

(m/defmethod create-project :post
  [{:as req
    ::keys [directory]
    ::r/keys [router]
    :keys [user-data]} res _raise]
  (let [params (parse-multipart-params req {:store (byte-array-store)})]
    (try
      (let [ctx         {:user user-data}
            project     (create-or-update-project ctx params)
            {:keys [project-id]} project
            project-url (get-route-url router
                                       ::project/project-view
                                       :path-params {:id project-id})]
        (res (res/redirect project-url :see-other)))
      (catch Exception ex
        (let [opts {:alert     {:message (ex-message ex)
                                :kind    :danger}
                    :params    params
                    :directory directory}]
          (-> (render req (render-create-project opts))
              (res/status 500)
              res))))))

(def routes
  ["/create-project" {:get  #'create-project
                      :post #'create-project
                      :name ::create-project}])
