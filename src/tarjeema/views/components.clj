(ns tarjeema.views.components
  (:require [tarjeema.views.layout :as layout])
  (:import [java.time ZoneId]
           [java.time.format DateTimeFormatter FormatStyle]
           [java.util Locale]))

(defn icon [name]
  (let [href (str "icons.svg#" name)]
    [:svg.icon
     [:use {:xlink:href  href}]]))

(defn alert-box [{:keys [message kind]}]
  [:div {:class (str "alert alert-" (name kind))} message])

(defn render-date [date & {:keys [relative?]}]
  (let [formatted (-> FormatStyle/MEDIUM
                      (DateTimeFormatter/ofLocalizedDateTime)
                      (.withLocale Locale/UK)
                      (.withZone (ZoneId/systemDefault))
                      (.format date))]
    [:time {:class (when relative? "relative")
            :datetime date} formatted]))

(defn action-btn
  ([data content] (action-btn data {} content))
  ([data attrs content]
   [:form {:method "post"}
    (for [[name value] data]
      [:input {:type "hidden"
               :name name
               :value value}])
    [:button attrs content]]))

(defn nav-tabs [tabs]
  [:ul.nav.nav-underline.mb-2
   (for [{:keys [name href]} tabs]
     (let [current? (= href layout/*uri*)]
       [:li.nav-item
        [:a {:class (str "nav-link" (when current? " active"))
             :aria-current (when current? "page")
             :href href}
         name]]))])
