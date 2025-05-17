(ns tarjeema.routes.project
  (:require [reitit.core :as r]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [render]]
            [tarjeema.middleware :refer [wrap-project wrap-roles]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.routes.translate :as-alias translate]
            [tarjeema.views.project :refer [render-project]]
            [toucan2.core :as t2]))

(defn- extract-project [{{:keys [id]} :path-params}]
  (let [project-id (some-> id parse-long)]
    (when (nil? project-id)
      (throw (ex-info "Project ID should be provided." {:type :input-error
                                                        :http-status 400})))
    {:project-id project-id}))

(defn project-view
  [{:as req, :keys [project], ::r/keys [router]} res _raise]
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
                                  :path-params {:id project-id})]
    (-> (render req
                (render-project {:project        project
                                 :langs          langs
                                 :build-href     build-href
                                 :translate-href translate-href}))
        res)))

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
   ["/build" {:get        #'build-project
              :name       ::build-project
              :middleware [[wrap-params]
                           [wrap-roles #{:owner}]]}]])
