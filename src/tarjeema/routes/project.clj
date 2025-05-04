(ns tarjeema.routes.project
  (:require [reitit.core :as r]
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
                                            :lang    (:bcp-47 %)})]
        (-> (render req
              (render-project {:project        project
                               :langs          langs
                               :translate-href translate-href}))
            res))
      (res (res/not-found "Not found.")))))

(def routes
  ["/project/:id"
   ["" {:get  #'project-view
        :name ::project-view}]])
