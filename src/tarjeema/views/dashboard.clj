(ns tarjeema.views.dashboard
  (:require [tarjeema.views.layout :as layout]))

(defn render-dashboard
  [{:keys [projects mk-project-url create-project-url]}]
  (binding [layout/*title* "Dashboard"]
    (layout/app
     [:main.container-lg
      [:h1 "Dashboard"]
      [:p "Hello, " (:user_name layout/*user-data*) "!"]
      [:section
       [:div.d-flex.align-items-center
        [:h2.me-4 "Projects"]
        [:a.btn.btn-primary {:href create-project-url} "Create new"]]
       (if (seq projects)
         [:ul.list-unstyled.row
          (for [o projects]
            [:li.card.col-12.col-sm-6.col-lg-4
             [:a.d-block.card-body
              {:href (mk-project-url (:project_id o))}
              [:div.fs-5.fw-bold.card-title (:project_name o)]
              (when-let [desc (:project_description o)]
                [:p.card-text desc])]])]
         ;; if-not (seq projects)
         [:div.p-4.text-secondary.text-center "There are currently no projects."])]])))
