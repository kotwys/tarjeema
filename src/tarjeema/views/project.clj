(ns tarjeema.views.project
  (:require [tarjeema.views.layout :as layout]))

(defn render-project [{:keys [project langs translate-href]}]
  (layout/app
   [:main.container-lg
    [:h1 (:project_name project)]
    [:p (:project_description project)]
    [:dl
     [:dt "Owner"]
     (let [{:keys [user_name user_email]} (-> project :owner)]
       [:dd
        user_name
        " (" [:a {:href (str "mailto:" user_email)} "mail"] ")"])
     [:dt "Source Language"]
     [:dd (-> project :source_lang :lang_name)]]
    [:ul
     (for [lang langs]
       [:li [:a {:href (translate-href lang)} (:lang_name lang)]])]]))
