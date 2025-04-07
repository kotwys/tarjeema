(ns tarjeema.views.index
  (:require [tarjeema.views.layout :as layout]))

(defn render-login-form [{:keys [error]}]
  [:div.card.p-5
   (when error
     [:div.alert.alert-danger error])
   [:form {:method "post"}
    [:div.mb-2
     [:label.form-label {:for "email"} "E-mail"]
     [:input#email.form-control {:type     "email"
                                 :name     "email"
                                 :required true}]]
    [:div.mb-2
     [:label.form-label {:for "password"} "Password"]
     [:input#password.form-control {:type     "password"
                                    :name     "password"
                                    :required true}]]
    [:input.btn.btn-primary.mt-2 {:type "submit"
                                  :value "Sign in"}]]])

(defn render-index [opts]
  (layout/app
   [:main.container-lg
    [:div.row
     [:div.col-12.col-md.p-4
      [:h1 "Welcome to Tarjeema, the collaborative translation platform!"]
      [:p "To start working, you need to sign in."]]
     [:div.col-12.col-md-5
      (render-login-form opts)]]]))
