(ns tarjeema.routes.project
  (:require [methodical.core :as m]
            [reitit.core :as r]
            [ring.middleware.multipart-params :refer [parse-multipart-params]]
            [ring.middleware.multipart-params.byte-array
             :refer [byte-array-store]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.form.project :refer [project->form-params
                                           create-or-update-project]]
            [tarjeema.macros :refer [render]]
            [tarjeema.middleware :refer [wrap-project wrap-roles]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.routes.translate :as-alias translate]
            [tarjeema.views.project :refer [render-project
                                            render-project-settings]]
            [toucan2.core :as t2]))

(defn- extract-project [{{:keys [id]} :path-params}]
  (let [project-id (some-> id parse-long)]
    (when (nil? project-id)
      (throw (ex-info "Project ID should be provided." {:type :input-error
                                                        :http-status 400})))
    {:project-id project-id}))

(defn project-view
  [{:as req, :keys [project path-params], ::r/keys [router]} res _raise]
  (let [project (t2/hydrate project :source-lang :owner)
        {:keys [project-id]} project
        langs   (->> (db/get-languages)
                     vals
                     (filter #(not (= (:lang-id %)
                                      (:source-lang-id project)))))
        translate-href #(get-route-url router ::translate/translate
                                       :query-params
                                       {:project project-id
                                        :lang    (:bcp-47 %)})
        build-href (get-route-url router ::build-project
                                  :path-params path-params)
        settings-href (get-route-url router ::project-settings
                                     :path-params path-params)]
    (-> (render req
                (render-project {:project        project
                                 :langs          langs
                                 :build-href     build-href
                                 :settings-href  settings-href
                                 :translate-href translate-href}))
        res)))

(m/defmulti project-settings
  (fn [{:keys [request-method]} _ _] request-method)
  :combo (m/thread-first-method-combination))

(m/defmethod project-settings :before :default [req _ _]
  (assoc req ::directory {:languages (vals (db/get-languages))}))

(m/defmethod project-settings :get
  [{:as req, ::keys [directory], :keys [flash project]} res _raise]
  (let [project (t2/hydrate project :source-lang)
        params  (project->form-params project)]
    (-> (render req
          (render-project-settings
           {:params    params
            :directory directory
            :alert     (:alert flash)}))
        res)))

(m/defmethod project-settings :post
  [{:as req
    ::keys [directory]
    :keys [user-data uri]} res _raise]
  (let [params (parse-multipart-params req {:store (byte-array-store)})]
    (try
      (let [ctx {:user user-data}
            _   (create-or-update-project ctx params)]
        (-> (res/redirect uri :see-other)
            (assoc-in [:flash :alert]
                      {:message "Project updated successfully."
                       :kind    :success})
            res))
      (catch Exception ex
        (let [opts {:alert     {:message (ex-message ex)
                                :kind    :danger}
                    :params    params
                    :directory directory}]
          (-> (render req (render-project-settings opts))
              (res/status 500)
              res))))))

(defn build-project
  [{:keys [project], {:strs [lang]} :params} res _raise]
  (let [lang (get (db/get-languages) lang)]
    (when (nil? lang)
      (throw (ex-info "You need to select from the existing languages."
                      {:type        :input-error
                       :http-status 400})))
    (-> (db/build-translation project lang)
        (res/response)
        (res/content-type "application/json")
        res)))

(def routes
  ["/project/:id" {:middleware [[wrap-project #'extract-project]]}
   ["" {:get  #'project-view
        :name ::project-view}]
   ["/settings" {:get        #'project-settings
                 :post       #'project-settings
                 :name       ::project-settings
                 :middleware [[wrap-roles #{:owner}]]}]
   ["/build" {:get        #'build-project
              :name       ::build-project
              :middleware [[wrap-params]
                           [wrap-roles #{:owner}]]}]])
