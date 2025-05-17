(ns tarjeema.views.layout
  (:require [hiccup.page :refer [html5]]))

(def site-name "Tarjeema")
(def ^:dynamic *title* nil)
(def ^:dynamic *uri* nil)

(def ^:dynamic *user-data* nil)

(defn app [& body]
  (html5
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (if *title*
                (str *title* " | " site-name)
                site-name)]
      [:link {:href "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css"
              :rel "stylesheet"
              :integrity "sha384-9ndCyUaIbzAi2FUVXJi0CjmCapSmO7SnpJef0486qhLnuZ2cdeRhO02iuK6FUUVM",
              :crossorigin "anonymous"}]]
     [:body
      [:header.navbar.bg-body-tertiary
       [:nav.container-fluid
        [:a.navbar-brand {:href "/"} "Tarjeema"]
        (when *user-data*
          [:ul.navbar-nav
           [:li.nav-item
            [:a.nav-link {:href "/logout"} "Log out"]]])]]
      body]]))
