(ns tarjeema.routes.project
  (:require [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [render]]
            [tarjeema.views.project :refer [render-project]]))

(defn project-view
  [{:as req, {:keys [id]} :path-params} res _raise]
  (let [project-id (parse-long id)]
    (if-let [project (db/find-project-by-id project-id)]
      (let [strings (db/get-strings project-id)]
        (-> (render req
              (render-project {:project project
                               :strings strings}))
            res))
      (res (res/not-found "Not found.")))))

(def routes
  ["/project/:id"
   ["" {:get  #'project-view
        :name ::project-view}]])
