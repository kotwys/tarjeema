(ns tarjeema.views.join
  (:require [tarjeema.views.components :refer [alert-box]]
            [tarjeema.views.layout :as layout]))

(defn render-message [{:keys [alert]}]
  (layout/app
   [:main.row.d-flex.justify-content-center
    [:div.card.p-5.col-12.col-sm-8.col-md-6.col-lg-4
     (alert-box alert)]]))

(defn render-join [{:keys [alert]}]
  (layout/bare
   [:main.bg-landing.row.d-flex.justify-content-center.align-items-center
    [:div.card.p-5.col-12.col-sm-8.col-md-6.col-lg-4
     (when alert (alert-box alert))
     [:h1 "Join"]
     [:p "You were invited to join a translation team."]
     [:form {:method "post"}
      [:div.mb-2
       [:label.form-label {:for "name"} "Your Name"]
       [:input#name.form-control {:name "name"
                                  :required true}]]
      [:div.mb-2
       [:label.form-label {:for "email"} "E-mail"]
       [:input#email.form-control {:type "email"
                                   :name "email"
                                   :required true}]]
      [:div.mb-2
       [:label.form-label {:for "password"} "Password"]
       [:input#password.form-control {:type "password"
                                      :name "password"
                                      :required true}]]
      [:input.btn.btn-primary.btn-block.mt-2 {:type "submit"
                                              :value "Join"}]]]]))

