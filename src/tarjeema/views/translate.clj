(ns tarjeema.views.translate
  (:require [tarjeema.views.layout :as layout])
  (:import [java.time ZoneId]
           [java.time.format DateTimeFormatter FormatStyle]
           [java.util Locale]))

(defn render-date [date]
  (let [formatted (-> FormatStyle/MEDIUM
                      (DateTimeFormatter/ofLocalizedDateTime)
                      (.withLocale Locale/UK)
                      (.withZone (ZoneId/systemDefault))
                      (.format date))]
    [:time.date-relative {:datetime date} formatted]))

(defn render-translate
  [{:keys [project lang strings current-string translations mk-string-href]}]
  (layout/app
   [:main.container-lg
    [:h1 (:project-name project) " › " (:lang-name lang)]
    [:section
     [:h2 "Strings"]
     [:ul
      (for [string strings]
        [:li
         [:a {:href (mk-string-href string)}
          (:string-text string)]])]]
    (when current-string
      [:section
       [:h2 "Translation"]
       [:section
        [:h3 "Source string"]
        [:pre (:string-text current-string)]
        [:div.text-secondary (:string-name current-string)]]
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
             [:pre (:translation-text translation)]
             [:div.mt-2
              [:div.fw-bold (-> translation :user :user-name)]
              [:div.text-secondary
               (render-date (-> translation :suggested-at))]]]])]]])]))
