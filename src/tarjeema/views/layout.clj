(ns tarjeema.views.layout
  (:require [hiccup.page :refer [html5]]))

(def site-name "Tarjeema")
(def ^:dynamic *title* nil)
(def ^:dynamic *uri* nil)

(def ^:dynamic *page-title* nil)
(def ^:dynamic *page-subtitle* nil)

(def ^:dynamic *user-data* nil)

(defn bare [& body]
  (html5
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (if *title*
                (str *title* " | " site-name)
                site-name)]
      [:link {:href "/index.css" :rel "stylesheet"}]]
     [:body body]]))

(defn app [& body]
  (bare
   [:header.header
    [:a.header__logo {:href "/" :title "Tarjeema"}
     [:img {:src "/logo.svg" :height 24}]]
    [:div.header__content
     [:h1.header__heading
      [:div *page-title*]
      (when *page-subtitle* [:div *page-subtitle*])]
     (when *user-data*
       [:a.ms-auto {:href "/logout"} "Log out"])]]
   body
   [:script
    {:src "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"
     :integrity "sha384-geWF76RCwLtnZ8qwWowPQNguL3RmwHVBC9FhGdlKrxdiJJigb/j/68SIy3Te4Bkz"
     :crossorigin "anonymous"}]))
