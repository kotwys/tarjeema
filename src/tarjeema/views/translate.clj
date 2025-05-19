(ns tarjeema.views.translate
  (:require [tarjeema.model :as model]
            [tarjeema.views.components :refer [render-date action-btn]]
            [tarjeema.views.layout :as layout]))

(def ^:private vote-btns
  [{:value true
    :text  "+"}
   {:value false
    :text  "-"}])

(defn render-suggestion [translation]
  [:li
   [:div
    [:pre (:translation-text translation)]
    [:div.mt-2.d-flex.gap-2
     [:div.fw-bold (-> translation :user :user-name)]
     [:div.text-secondary
      (render-date (-> translation :suggested-at) :relative? true)]
     [:div.text-secondary
      "Rating: " (-> translation :rating)]]
    (when (-> translation :approval :translation-id)
      (let [{:keys [approval]} translation]
        [:div.mt-2.d-flex.gap-2
         [:div.text-success "✅ Approved"]
         [:div.fw-bold (-> approval :user :user-name)]
         [:div.text-secondary
          (render-date (-> approval :approved-at) :relative? true)]]))]
   [:div.d-flex
    (when (model/can-delete-translation? layout/*user-data* translation)
      (action-btn {:action         :delete-translation
                   :translation-id (:translation-id translation)}
                  {:class "btn btn-primary"}
                  "Delete"))
    (when (model/can-approve? layout/*user-data*)
      (let [approved? (-> translation :approval :translation-id)
            action    (if approved? :disapprove :approve)
            text      (if approved? "Disapprove" "Approve")]
        (action-btn {:action         action
                     :translation-id (:translation-id translation)}
                    {:class "btn btn-primary"}
                    text)))
    (when (model/can-vote? layout/*user-data* translation)
      (for [{:keys [value text]} vote-btns]
        (let [active? (= value (-> translation :vote))]
          (action-btn {:action         :vote
                       :translation-id (:translation-id translation)
                       :value          (if active? nil (str value))}
                      {:class (str "btn" (when active? " btn-secondary"))}
                      text))))]])

(defn render-comment [comment]
  [:div
   [:div
    [:div.fw-bold.mb-1 (-> comment :user :user-name)]
    [:pre (-> comment :comment-text)]
    (when (model/can-delete-comment? layout/*user-data* comment)
      [:div
       (action-btn {:action     :delete-comment
                    :comment-id (:comment-id comment)}
                   {:class "btn btn-danger"}
                   "Delete")])]
   [:div.text-secondary
    (render-date (-> comment :posted-at) :relative? true)]])

(defn render-translate
  [{:keys [project lang strings current-string comments translations mk-string-href]}]
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
      (list
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
           :required true
           :name "text"
           :rows 3}]
         [:div.translation-actions
          #_[:div.char-count "0 • " (count (:content active-string))]
          [:button.btn.btn-primary "Save"]]]
        [:section
         [:h3 "Suggestions"]
         [:ul
          (for [translation translations]
            (render-suggestion translation))]]]
       [:section
        [:div
         [:h2 "Comments"]
         (for [comment comments] (render-comment comment))]
        [:form {:method "post"}
         [:input {:type "hidden" :name :action :value :comment}]
         [:textarea.form-control
          {:placeholder "Leave a comment"
           :name "comment"
           :required true
           :rows 3}]
         [:button.btn.btn-primary "Comment"]]]))]))
