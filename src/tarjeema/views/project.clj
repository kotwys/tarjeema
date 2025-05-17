(ns tarjeema.views.project
  (:require [tarjeema.views.components :refer [action-btn]]
            [tarjeema.views.layout :as layout]))

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
      [:ul.nav.nav-underline.mb-2
       (for [{:keys [name href]} tabs]
         (let [current? (= href layout/*uri*)]
           [:li.nav-item
            [:a {:class (str "nav-link" (when current? " active"))
                 :aria-current (when current? "page")
                 :href href}
             name]]))]
      body]]]))

(defn render-project-settings [opts]
  (settings-page
   opts
   (render-settings-form (assoc opts :btn-text "Apply"))))

(defn render-proofreaders
  [{:as opts :keys [proofreaders], {:keys [languages]} :directory}]
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
       (for [{:keys [bcp-47 lang-name]} languages]
         [:option {:value bcp-47} lang-name])]]]
    [:div.d-flex.mt-2
     [:button.btn.btn-primary.ms-auto "Promote"]]]))

(defn render-project
  [{:keys [project langs translate-href build-href settings-href]}]
  (layout/app
   [:main.container-lg
    [:h1 (:project-name project)]
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
    [:ul
     (for [lang langs]
       [:li [:a {:href (translate-href lang)} (:lang-name lang)]])]

    (when (some #{:owner} (:roles layout/*user-data*))
      [:section
       [:h2 "Build Translations"]
       [:form {:action build-href :method "get"}
        [:select {:name "lang", :required true}
         [:option {:selected true, :disabled true} "Select a language"]
         (for [lang langs]
           [:option {:value (:bcp-47 lang)} (:lang-name lang)])]
        [:input.btn.btn-primary {:type "submit" :value "Build"}]]])]))
