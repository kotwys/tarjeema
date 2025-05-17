(ns tarjeema.form.project
  (:require [clojure.string :as str]
            [tarjeema.db :as db]))

(defn project->form-params [project]
  {"project-id"   (:project-id project)
   "project-name" (:project-name project)
   "source-lang"  (:bcp-47 (:source-lang project))
   "description"  (:project-description project)})

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

(defn create-or-update-project
  [{:keys [user]} {:as params, :strs [project-id strings]}]
  (when-not (str/blank? (:filename strings))
    (let [ct (:content-type strings)]
      (when-not (= "application/json" ct)
        (throw (ex-info (str "Source strings should be supplied as JSON, got: "
                             ct ".")
                        {})))))
  (let [project-id  (some-> project-id parse-long)
        row         (-> (parse-project-data params)
                        (assoc :owner-id (:user-id user))
                        (cond-> #_a
                         project-id (assoc :project-id project-id)))
        project     (db/create-or-update-project row user)
        string-data (-> strings :bytes)
        _           (when (seq string-data)
                      (db/upload-strings project string-data))]
    project))
