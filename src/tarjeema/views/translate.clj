(ns tarjeema.views.translate
  (:require [tarjeema.model :as model]
            [tarjeema.views.layout :as layout])
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

(defn action-btn [data content]
  [:form {:method "post"}
   (for [[name value] data]
     [:input {:type "hidden" :name name :value value}])
   [:button.btn.btn-primary content]])

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
             [:div.mt-2.d-flex.gap-2
              [:div.fw-bold (-> translation :user :user-name)]
              [:div.text-secondary
               (render-date (-> translation :suggested-at))]]
             (when (-> translation :approval :translation-id)
               (let [{:keys [approval]} translation]
                 [:div.mt-2.d-flex.gap-2
                  [:div.text-success "✅ Approved"]
                  [:div.fw-bold (-> approval :user :user-name)]
                  [:div.text-secondary
                   (render-date (-> approval :approved-at))]]))]
            [:div.d-flex
             (when (model/can-delete-translation? layout/*user-data*
                                                  translation)
               (action-btn {:action         :delete-translation
                            :translation-id (:translation-id translation)}
                           "Delete"))
             (when (model/can-approve? layout/*user-data*)
               (if (-> translation :approval :translation-id)
                 (action-btn {:action         :disapprove
                              :translation-id (:translation-id translation)}
                             "Disapprove")
                 (action-btn {:action         :approve
                            :translation-id (:translation-id translation)}
                             "Approve")))]])]]])]))
