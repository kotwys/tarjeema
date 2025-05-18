(ns tarjeema.views.components
  (:require [tarjeema.views.layout :as layout]))

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
