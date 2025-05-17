(ns tarjeema.views.components)

(defn action-btn
  ([data content] (action-btn data {} content))
  ([data attrs content]
   [:form {:method "post"}
    (for [[name value] data]
      [:input {:type "hidden"
               :name name
               :value value}])
    [:button attrs content]]))
