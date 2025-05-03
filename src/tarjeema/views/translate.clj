(ns tarjeema.views.translate
  (:require [tarjeema.views.layout :as layout]))

(defn render-translate
  [{:keys [project lang strings current-string translations mk-string-href]}]
  (layout/app
   [:main.container-lg
    [:h1 (:project_name project) " › " (:lang_name lang)]
    [:section
     [:h2 "Strings"]
     [:ul
      (for [string strings]
        [:li
         [:a {:href (mk-string-href string)}
          (:string_text string)]])]]
    (when current-string
      [:section
       [:h2 "Translation"]
       [:section
        [:h3 "Source string"]
        [:pre (:string_text current-string)]
        [:div.text-secondary (:string_name current-string)]]
       [:form {:method "post"}
        [:input {:type "hidden" :name "action" :value "suggest"}]
        [:textarea.form-control
         {:placeholder "Enter translation here"
          :name "text"
          :rows 3}]
        [:div.translation-actions
         #_[:div.char-count "0 • " (count (:content active-string))]
         [:button.btn.btn-primary "Save"]]]
       [:section
        [:h3 "Suggestions"]
        [:ul
         (for [translation translations]
           [:li
            [:div
             [:div (:translation_text translation)]
             [:div.mt-2
              [:div.fw-bold (-> translation :user :user_name)]
              ;; TODO: use relative dates
              [:div.text-secondary (-> translation :suggested_at)]]]])]]])]))
