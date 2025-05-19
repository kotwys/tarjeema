(ns tarjeema.routes.project
  (:require [better-cond.core :as b]
            [clojure.string :as str]
            [methodical.core :as m]
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
                                            render-project-settings
                                            render-proofreaders
                                            render-reports]]
            [toucan2.core :as t2]))

(defn- extract-project [{{:keys [id]} :path-params}]
  (let [project-id (some-> id parse-long)]
    (when (nil? project-id)
      (throw (ex-info "Project ID should be provided." {:type :input-error
                                                        :http-status 400})))
    {:project-id project-id}))

(defn calc-tabs [router tabs path-params]
  (mapv (fn [{:keys [key name]}]
          {:href (get-route-url router key :path-params path-params)
           :name name})
        tabs))

(def ^:private project-tabs
  [{:key  ::project-view
    :name "Overview"}
   {:key  ::project-reports
    :name "Reports"}])

(defn project-view
  [{:as req, :keys [project path-params], ::r/keys [router]} res _raise]
  (let [project (t2/hydrate project :source-lang :owner)
        {:keys [project-id]} project
        tabs  (calc-tabs router project-tabs path-params)
        langs (db/language-completeness project)
        translate-href #(get-route-url router ::translate/translate
                                       :query-params
                                       {:project project-id
                                        :lang    (:bcp-47 %)})
        build-href (get-route-url router ::build-project
                                  :path-params path-params)
        settings-href (get-route-url router ::general-settings
                                     :path-params path-params)]
    (-> (render req
                (render-project {:project        project
                                 :tabs           tabs
                                 :langs          langs
                                 :build-href     build-href
                                 :settings-href  settings-href
                                 :translate-href translate-href}))
        res)))

(defn reports
  [{:as req, :keys [project path-params], ::r/keys [router]} res _raise]
  (let [tabs (calc-tabs router project-tabs path-params)
        ctx  {:project project}
        overall-activity (db/overall-activity ctx)
        top-members      (db/top-members ctx)]
    (-> (render req
          (render-reports {:project project
                           :tabs    tabs
                           :overall-activity overall-activity
                           :top-members      top-members}))
        res)))

(m/defmulti project-settings
  (fn [{:keys [request-method], {{:keys [name]} :data} ::r/match} _ _]
    [request-method name])
  :combo (m/thread-first-method-combination))

(def ^:private settings-tabs
  [{:key  ::general-settings
    :name "General"}
   {:key  ::proofreaders
    :name "Proofreaders"}])

(m/defmethod project-settings :before :default
  [{:as req, ::r/keys [router], :keys [project path-params]} _ _]
  (let [tabs (calc-tabs router settings-tabs path-params)]
    (-> req
        (assoc-in [::view-opts :tabs] tabs)
        (assoc-in [::view-opts :project] project)
        (assoc-in [::view-opts :directory]
                  {:languages (vals (db/get-languages))}))))

(m/defmethod project-settings [:get ::general-settings]
  [{:as req, ::keys [view-opts], :keys [flash project]} res _raise]
  (let [project (t2/hydrate project :source-lang)
        params  (project->form-params project)]
    (-> (render req
          (render-project-settings
           (merge view-opts
                  {:params    params
                   :alert     (:alert flash)})))
        res)))

(m/defmethod project-settings [:post ::general-settings]
  [{:as req
    ::keys [view-opts]
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
        (let [opts (merge view-opts
                          {:alert  {:message (ex-message ex)
                                    :kind    :danger}
                           :params params})]
          (-> (render req (render-project-settings opts))
              (res/status 500)
              res))))))

(m/defmethod project-settings [:get ::proofreaders]
  [{:as req, ::keys [view-opts], :keys [project]} res _raise]
  (let [proofreaders (-> ::db/proofreader
                         (t2/select :project-id (:project-id project))
                         (t2/hydrate :user :lang))
        opts (assoc view-opts :proofreaders proofreaders)]
    (-> (render req (render-proofreaders opts))
        res)))

(m/defmulti handle-proofreader-action (fn [{:keys [action]} _] action))

(m/defmethod handle-proofreader-action ::promote
  [{:keys [project]} {:strs [email lang]}]
  (b/cond
    :let [lang (get (db/get-languages) lang)]
    (nil? lang)
    (throw (ex-info (str "Unknown language " lang ".") {}))

    (str/blank? email)
    (throw (ex-info "An email should be provided." {}))

    :let [user (t2/select-one ::db/user :user-email email)]
    (nil? user)
    (throw (ex-info (str "User with email " email " not found.") {}))

    (t2/insert! ::db/proofreader
                :project-id (:project-id project)
                :user-id    (:user-id user)
                :lang-id    (:lang-id lang))))

(m/defmethod handle-proofreader-action ::demote
  [{:keys [project]} {:strs [user-id lang-id]}]
  (let [user-id (some-> user-id parse-long)
        lang-id (some-> lang-id parse-long)]
    (when (nil? user-id)
      (throw (ex-info "User ID should be provided." {})))
    (when (nil? lang-id)
      (throw (ex-info "Language ID should be provided." {})))
    (t2/delete! ::db/proofreader
                :project-id (:project-id project)
                :user-id user-id
                :lang-id lang-id)))

(m/defmethod project-settings [:post ::proofreaders]
  [{:keys [uri project form-params]} res _raise]
  (let [{:strs [action]} form-params]
    (when (str/blank? action)
      (throw (ex-info "No action provided." {})))
    (let [ctx {:action  (keyword (namespace ::here) action)
               :project project}]
      (handle-proofreader-action ctx form-params)
      (res (res/redirect uri :see-other)))))

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
   ["/reports" {:get  #'reports
                :name ::project-reports}]
   ["/settings" {:middleware [[wrap-roles #{:owner}]]}
    ["/general" {:get        #'project-settings
                 :post       #'project-settings
                 :name       ::general-settings}]
    ["/proofreaders" {:get        #'project-settings
                      :post       #'project-settings
                      :name       ::proofreaders
                      :middleware [[wrap-params]]}]]
   ["/build" {:get        #'build-project
              :name       ::build-project
              :middleware [[wrap-params]
                           [wrap-roles #{:owner}]]}]])
