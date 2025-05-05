(ns tarjeema.views.project
  (:require [tarjeema.views.layout :as layout]))

(defn render-project [{:keys [project langs translate-href build-href]}]
  (layout/app
   [:main.container-lg
    [:h1 (:project-name project)]
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
    [:section
     [:h2 "Build Translations"]
     [:form {:action build-href :method "get"}
      [:select {:name "lang"}
       [:option "-- select a language --"]
       (for [lang langs]
         [:option {:value (:bcp-47 lang)} (:lang-name lang)])]
      [:input.btn.btn-primary {:type "submit" :value "Build"}]]]]))
