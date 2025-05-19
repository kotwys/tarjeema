(ns tarjeema.views.project
  (:require [clojure.string :as str]
            [tarjeema.views.components :refer [action-btn nav-tabs]]
            [tarjeema.views.layout :as layout])
  (:import [java.text DecimalFormat]))

(defn pc [n] (-> "0%" (DecimalFormat.) (.format n)))

(defn render-settings-form
  [{:keys [alert btn-text stage]
    {:keys [languages]} :directory
    {:strs [project-id project-name source-lang description]} :params}]
  [:form {:method "post" :enctype "multipart/form-data"}
   (when-let [{:keys [message kind]} alert]
     [:div {:class (str "alert alert-" (name kind))} message])
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
               :disabled true
               :value    ""}
      "Select a language"]
     (for [{:keys [bcp-47 lang-name]} languages]
       [:option {:selected (= bcp-47 source-lang)
                 :value bcp-47}
        lang-name])]]
   [:div.mb-2
    [:label.form-label {:for "description"}
     "Project Description "
     [:span.text-secondary "(optional)"]]
    [:textarea#description.form-control {:name "description", :rows 3}
     description]]
   [:div.mb-2
    [:label.form-label {:for "strings"} "Source Strings File (JSON)"]
    [:input#strings.form-control {:type   "file"
                                  :name   "strings"
                                  :accept ".json,application/json"}]
    (when (= :create stage)
      [:div.form-text "You may skip this for now."])]
   [:div.d-flex
    [:input.btn.btn-primary.mt-2.ms-auto {:type  "submit"
                                          :value btn-text}]]])

(defn render-create-project [opts]
  (binding [layout/*title* "Create New Project"]
    (layout/app
     [:main.container-sm
      [:div.row.justify-content-center
       [:div.col-12.col-md-6
        [:h1 "Create New Project"]
        (render-settings-form (assoc opts
                                     :stage :create
                                     :btn-text "Create"))]]])))

(defn settings-page [{:keys [tabs]} & body]
  (layout/app
   [:main.container-sm
    [:div.row.justify-content-center
     [:div.col-12.col-md-6
      [:h1 "Project Settings"]
      (nav-tabs tabs)
      body]]]))

(defn render-project-settings [opts]
  (settings-page
   opts
   (render-settings-form (assoc opts :btn-text "Apply"))))

(defn render-proofreaders
  [{:as opts :keys [proofreaders project], {:keys [languages]} :directory}]
  (settings-page
   opts
   [:table.table.table-stripped
    [:thead
     [:tr
      [:th "User"]
      [:th "Language"]
      [:th "Actions"]]]
    [:tbody
     (for [{:keys [user lang]} proofreaders]
       [:tr
        [:td (-> user :user-name)]
        [:td (-> lang :lang-name)]
        [:td (action-btn {:action  :demote
                          :user-id (:user-id user)
                          :lang-id (:lang-id lang)}
                         {:class "btn btn-outline-danger"}
                         "Demote")]])]]
   [:h2 "Promote to a Proofreader"]
   [:form {:method "post"}
    [:input {:type "hidden" :name :action :value :promote}]
    [:div.mb-2.row
     [:label.col-3.col-form-label {:for "email"} "E-mail"]
     [:div.col-9
      [:input#email.form-control {:type "email"
                                  :name "email"
                                  :required true}]]]
    [:div.mb-2.row
     [:label.col-3.col-form-label {:for "lang"} "Language"]
     [:div.col-9
      [:select#lang.form-select {:name     "lang"
                                 :required true}
       [:option {:selected true
                 :disabled true
                 :value    ""}
        "Select a language"]
       (for [{:keys [lang-id bcp-47 lang-name]} languages]
         (when-not (= lang-id (:source-lang-id project))
           [:option {:value bcp-47} lang-name]))]]]
    [:div.d-flex.mt-2
     [:button.btn.btn-primary.ms-auto "Promote"]]]))

(defn render-language-completeness [{:keys [langs translate-href]}]
  [:table
   [:thead
    [:tr
     [:th {:style "width:50%"} "Language"]
     [:td]
     [:th "Translated"]
     [:th [:span.ms-2 "Approved"]]]]
   [:tbody
    (for [{:as lang :keys [translated approved]} langs]
      [:tr
       [:td [:a {:href (translate-href lang)} (:lang-name lang)]]
       [:td
        [:div.progress-stacked.mt-2.mb-2 {:style "width:100px"}
         [:div.progress {:style (str "width:" (pc approved))}
          [:div.progress-bar.bg-success]]
         [:div.progress {:style (str "width:" (pc (- translated approved)))}
          [:div.progress-bar]]]]
       [:td {:align "right"} (pc translated)]
       [:td {:align "right"} (pc approved)]])]])

(defn render-project
  [{:as opts :keys [project tabs langs build-href settings-href]}]
  (layout/app
   [:main.container-lg
    [:h1 (:project-name project)]
    (nav-tabs tabs)
    (when (some #{:owner} (:roles layout/*user-data*))
      [:a {:href settings-href} "Settings"])
    [:p (:project-description project)]
    [:dl
     [:dt "Owner"]
     (let [{:keys [user-name user-email]} (-> project :owner)]
       [:dd
        user-name
        " (" [:a {:href (str "mailto:" user-email)} "mail"] ")"])
     [:dt "Source Language"]
     [:dd (-> project :source-lang :lang-name)]]
    (render-language-completeness opts)

    (when (some #{:owner} (:roles layout/*user-data*))
      [:section
       [:h2 "Build Translations"]
       [:form {:action build-href :method "get"}
        [:select {:name "lang", :required true}
         [:option {:selected true, :disabled true} "Select a language"]
         (for [lang langs]
           [:option {:value (:bcp-47 lang)} (:lang-name lang)])]
        [:input.btn.btn-primary {:type "submit" :value "Build"}]]])]))

(defn render-top-members [top-members]
  [:section
   [:h2 "Top Members"]
   [:table.table.table-bordered.table-striped
    [:thead
     [:tr
      [:th "Rank"]
      [:th "Name"]
      [:th "Languages"]
      [:th "Translated"]
      [:th "Winning"]]]
    [:tbody
     (for [[rank row] (map-indexed #'list top-members)]
       [:tr
        [:td {:align "right"} (inc rank)]
        [:td (-> row :user-name)]
        [:td (-> row :languages)]
        [:td {:align "right"} (-> row :translated)]
        [:td {:align "right"} (-> row :winning)]])]]])

(def activity-points
  [{:field :translated
    :text "Strings Translated"}
   {:field :approved
    :text "Strings Approved"}])

(defn render-reports
  [{:keys [project tabs overall-activity top-members],
    {:strs [lang since until]} :params
    {:keys [languages]} :directory}]
  (layout/app
   [:main.container-lg
    [:h1 (:project-name project)]
    (nav-tabs tabs)
    [:form {:method "get"}
     [:div.row
      [:div.col.row
       [:label.col-3.col-form-label {:for "lang"} "Language"]
       [:div.col-9
        [:select#lang.col.form-select {:name "lang"}
         [:option {:value ""
                   :selected (str/blank? lang)} "All"]
         (for [{:keys [bcp-47 lang-name]} languages]
           [:option {:value bcp-47
                     :selected (= bcp-47 lang)} lang-name])]]]
      [:div.col.row
       [:label.col-3.col-form-label {:for "since"} "Since"]
       [:div.col-9
        [:input#since.form-control {:name "since"
                                    :type "date"
                                    :value since}]]]
      [:div.col.row
       [:label.col-3.col-form-label {:for "until"} "Until"]
       [:div.col-9
        [:input#until.form-control {:name "until"
                                    :type "date"
                                    :value until}]]]]
     [:div.d-flex
      [:button.btn.btn-primary.ms-auto "Generate"]]]
    [:div.d-flex.justify-content-center
     (for [{:keys [field text]} activity-points]
       [:div.col.text-center
        [:div.fs-3 (get overall-activity field)]
        [:div text]])]
    (render-top-members top-members)]))
