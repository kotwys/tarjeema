(ns tarjeema.views.create-project
  (:require [tarjeema.views.layout :as layout]))

(defn render-create-project
  [{:keys [error]
    {:keys [languages]} :directory
    {:strs [project-id project-name source-lang description]} :params}]
  (binding [layout/*title* "Create New Project"]
    (layout/app
     [:main.container-sm
      [:div.row.justify-content-center
       [:div.col-12.col-md-6
        [:h1 "Create New Project"]
        (when error
          [:div.alert.alert-danger error])
        [:form {:method "post" :enctype "multipart/form-data"}
         (when project-id
           [:input {:type "hidden" :name "project-id" :value project-id}])
         [:div.mb-2
          [:label.form-label {:for "name"} "Project Name"]
          [:input#name.form-control {:type     "text"
                                     :name     "project-name"
                                     :value    project-name
                                     :required true}]]
         [:div.mb-2
          [:label.form-label {:for "source-lang"} "Source Language"]
          [:select#source-lang.form-select {:name     "source-lang"
                                            :required true}
           [:option {:selected (nil? source-lang)
                     :value ""}
            "(select a language)"]
           (for [{:keys [bcp47 lang_name]} languages]
             [:option {:selected (= bcp47 source-lang)
                       :value bcp47}
              lang_name])]]
         [:div.mb-2
          [:label.form-label {:for "description"}
           "Project Description "
           [:span.text-secondary "(optional)"]]
          [:textarea#description.form-control {:name "description", :rows 3}
           description]]
         [:div.mb-2
          [:label.form-label {:for "name"} "Source Strings File (JSON)"]
          [:input#name.form-control {:type   "file"
                                     :name   "strings"
                                     :accept ".json,application/json"}]
          [:div.form-text "You may skip this for now."]]
         [:div.d-flex
          [:input.btn.btn-primary.mt-2.ms-auto {:type  "submit"
                                                :value "Create"}]]]]]])))
