(ns tarjeema.views.project
  (:require [tarjeema.views.layout :as layout]))

(defn render-project [{:keys [project langs translate-href]}]
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
       [:li [:a {:href (translate-href lang)} (:lang-name lang)]])]]))
