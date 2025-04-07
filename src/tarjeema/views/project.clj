(ns tarjeema.views.project
  (:require [tarjeema.views.layout :as layout]))

(defn render-project [{:keys [project strings]}]
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
    [:table.table.table-bordered
     (for [{:keys [string_name string_text]} strings]
       [:tr
        [:td string_name]
        [:td string_text]])]]))
