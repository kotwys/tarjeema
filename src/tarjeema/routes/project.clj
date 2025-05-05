(ns tarjeema.routes.project
  (:require [reitit.core :as r]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [render]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.routes.translate :as-alias translate]
            [tarjeema.views.project :refer [render-project]]
            [toucan2.core :as t2]))

(defn project-view
  [{:as req
    ::r/keys [router]
    {:keys [id]} :path-params} res _raise]
  ;; TODO: parse params
  (let [project-id (parse-long id)]
    (if-let [project (-> (t2/select-one ::db/project project-id)
                         (t2/hydrate :source-lang :owner))]
      (let [langs (->> (db/get-languages)
                       vals
                       (filter #(not (= (:lang-id %)
                                        (:source-lang-id project)))))
            translate-href #(get-route-url router ::translate/translate
                                           :query-params
                                           {:project project-id
                                            :lang    (:bcp-47 %)})
            build-href (get-route-url router ::build-project
                                      :path-params {:id id})]
        (-> (render req
              (render-project {:project        project
                               :langs          langs
                               :build-href     build-href
                               :translate-href translate-href}))
            res))
      (res (res/not-found "Not found.")))))

(defn build-project
  [{{:strs [lang]} :params, {:keys [id]} :path-params} res _raise]
  (let [project-id (parse-long id)
        lang       (get (db/get-languages) lang)
        project    (t2/select-one ::db/project project-id)]
    (when (nil? project)
      (throw (ex-info "Project not found." {:type        :input-error
                                            :http-status 404})))
    (when (nil? lang)
      (throw (ex-info "You need to select from the existing languages."
                      {:type        :input-error
                       :http-status 400})))
    (-> (db/build-translation project lang)
        (res/response)
        (res/content-type "application/json")
        res)))

(def routes
  ["/project/:id"
   ["" {:get  #'project-view
        :name ::project-view}]
   ["/build" {:get        #'build-project
              :name       ::build-project
              :middleware [[wrap-params]]}]])
