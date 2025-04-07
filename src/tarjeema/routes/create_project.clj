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
            [tarjeema.views.create-project :refer [render-create-project]])
  (:import [org.pg.error PGErrorResponse]))

(defn- parse-project-data
  [{:strs [project-name source-lang description] :as params}]
  (let [source-lang-id (-> (db/get-languages)
                           (get (keyword source-lang))
                           :lang_id)]
    (when (str/blank? project-name)
      (throw (ex-info "Project name should be specified."
                      {:type   ::input-error
                       :params params})))
    (when-not source-lang-id
      (throw (ex-info "The source language of the project should be one of the known languages."
                      {:type   ::input-error
                       :params params})))
    {:project_name        project-name
     :source_lang_id      source-lang-id
     :project_description description}))

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
        (let [row         (-> (parse-project-data params)
                              (assoc :owner_id (:user_id user-data)))
              project-id  (when-let [id (get params "project-id")]
                            (parse-long id))
              project-id  (if project-id
                            (if (db/is-project-owner? user-data project-id)
                              (db/update-project project-id row)
                              (throw (ex-info "Not a project owner."
                                              {:type ::acess-error})))
                            (db/create-project row))
              string-data (-> params (get "strings") :bytes)
              _           (when string-data
                            (db/upload-strings project-id string-data))
              project-url (get-route-url router
                                         ::project/project-view
                                         :path-params {:id project-id})]
          (res (res/redirect project-url :see-other)))
        (catch Exception ex
          (let [opts {:error  (cond
                                ;; TODO: error reporting -M 07.04.2025
                                (instance? PGErrorResponse ex)
                                (-> ex ex-data :message)

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
