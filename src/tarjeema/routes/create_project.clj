(ns tarjeema.routes.create-project
  (:require [clojure.string :as str]
            [reitit.core :as r]
            [ring.middleware.multipart-params :refer [parse-multipart-params]]
            [ring.middleware.multipart-params.byte-array
             :refer [byte-array-store]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [render]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.routes.project :as-alias project]
            [tarjeema.views.create-project :refer [render-create-project]]))

(defn- parse-project-data
  [{:strs [project-name source-lang description] :as params}]
  (let [source-lang-id (-> (db/get-languages)
                           (get source-lang)
                           :lang-id)]
    (when (str/blank? project-name)
      (throw (ex-info "Project name should be specified."
                      {:type   ::input-error
                       :params params})))
    (when-not source-lang-id
      (throw (ex-info "The source language of the project should be one of the known languages."
                      {:type   ::input-error
                       :params params})))
    {:project-name        project-name
     :source-lang-id      source-lang-id
     :project-description description}))

(defn create-project
  [{:as req
    ::r/keys [router]
    :keys [user-data request-method]} res _raise]
  (case request-method
    :get
    (-> (render req
          (render-create-project
           {:directory {:languages (vals (db/get-languages))}}))
        res)

    :post
    (let [params (parse-multipart-params req {:store (byte-array-store)})]
      (try
        (when-let [content-type (-> params (get "strings") :content-type)]
          (when-not (= "application/json" content-type)
            (throw (ex-info (str "Source strings should be supplied as JSON, got: "
                                 content-type ".")
                            {}))))
        (let [project-id  (some-> params (get "project-id") parse-long)
              row         (-> (parse-project-data params)
                              (assoc :owner-id (:user-id user-data))
                              (cond-> #_a
                                project-id
                                (assoc :project-id project-id)))
              project     (db/create-or-update-project row user-data)
              string-data (-> params (get "strings") :bytes)
              _           (when string-data
                            (db/upload-strings project string-data))
              {:keys [project-id]} project
              project-url (get-route-url router
                                         ::project/project-view
                                         :path-params {:id project-id})]
          (res (res/redirect project-url :see-other)))
        (catch Exception ex
          (let [opts {:error  (cond
                                ;; TODO: error reporting -M 07.04.2025
                                :else (ex-message ex))
                      :params params
                      :directory
                      {:languages (vals (db/get-languages))}}]
            (-> (render req (render-create-project opts))
                (res/status 500)
                res)))))

    (-> (res/response "Method not allowed.")
        (res/status 405)
        res)))

(def routes
  ["/create-project" {:handler #'create-project
                      :name    ::create-project}])
