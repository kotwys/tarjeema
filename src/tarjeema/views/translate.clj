(ns tarjeema.views.translate
  (:require [tarjeema.model :as model]
            [tarjeema.views.components :refer [icon render-date action-btn]]
            [tarjeema.views.layout :as layout]))

(def ^:private vote-btns
  [{:value true
    :icon-name "thumb-up-line"
    :text "Upvote"}
   {:value false
    :icon-name "thumb-down-line"
    :text "Downvote"}])

(defn render-suggestion [translation]
  [:li
   [:div.suggestion
    [:div.suggestion__body
     [:pre.pre-text (:translation-text translation)]
     [:div.mt-2.d-flex.gap-2.suggestion__info
      [:div.fw-bold (-> translation :user :user-name)]
      [:div.text-secondary
       (render-date (-> translation :suggested-at) :relative? true)]
      [:div.text-secondary "•"]
      [:div.text-secondary
       "Rating: " (-> translation :rating)]]
     (when (-> translation :approval :translation-id)
       (let [{:keys [approval]} translation]
         [:div.mt-2.d-flex.gap-2.suggestion__info.suggestion__approved
          [:div.text-success (icon "check-line") "Approved"]
          [:div.fw-bold (-> approval :user :user-name)]
          [:div.text-secondary
           (render-date (-> approval :approved-at) :relative? true)]]))]
    [:div.suggestion__actions
     (when (model/can-approve? layout/*user-data*)
       (let [approved? (-> translation :approval :translation-id)
             action    (if approved? :disapprove :approve)
             text      (if approved? "Disapprove" "Approve")]
         (action-btn {:action         action
                      :translation-id (:translation-id translation)}
                     {:class (str "btn" (when approved? " btn-secondary"))
                      :title text}
                     (icon "check-line"))))
     (when (model/can-vote? layout/*user-data* translation)
       (for [{:keys [value text icon-name]} vote-btns]
         (let [active? (= value (-> translation :vote))]
           (action-btn {:action         :vote
                        :translation-id (:translation-id translation)
                        :value          (if active? nil (str value))}
                       {:class (str "btn" (when active? " btn-secondary"))
                        :title text}
                       (icon icon-name)))))
     (when (model/can-delete-translation? layout/*user-data* translation)
       (action-btn {:action         :delete-translation
                    :translation-id (:translation-id translation)}
                   {:class "btn btn-outline-danger"}
                   (icon "delete-bin-line")))]]])

(defn render-comment [comment]
  [:div.comment
   [:div.comment-body
    [:div.fw-bold.mb-1 (-> comment :user :user-name)]
    [:pre.pre-text (-> comment :comment-text)]
    (when (model/can-delete-comment? layout/*user-data* comment)
      [:div
       (action-btn {:action     :delete-comment
                    :comment-id (:comment-id comment)}
                   {:class "btn btn-outline-danger"}
                   (icon "delete-bin-line"))])]
   [:div.text-secondary
    (render-date (-> comment :posted-at) :relative? true)]])

(defn render-translate
  [{:keys [project lang strings current-string comments translations mk-string-href]}]
  (binding [layout/*page-title* (:project-name project)
            layout/*page-subtitle* (:lang-name lang)]
    (layout/app
     [:main.translation-view
      [:section.strings
       [:h2.visually-hidden "Strings"]
       [:ul.list-unstyled
        (for [string strings]
          (let [href     (mk-string-href string)
                current? (= (:string-id string) (:string-id current-string))
                {:keys [status]} string
                icon-name        (case status
                                   "approved" "check-line"
                                   "checkbox-blank-fill")]
            [:li
             [:a.string {:href href
                         :aria-current (when current? "page")}
              [:div.string__content
               [:div.oneliner (:string-text string)]
               [:div.oneliner.fs-italic.text-secondary (:text string)]]
              [:div {:class (str "string__status "
                                 "string__status_" status)}
               (icon icon-name)]]]))]]
      (when current-string
        (list
         [:section.translation
          [:h2.visually-hidden "Translation"]
          [:section.string__display
           [:h3.text-secondary.mb-2.fs-6 "Source string"]
           [:pre.pre-text.mb-2 (:string-text current-string)]
           [:div.string__key
            (icon "key-2-line")
            (:string-name current-string)]]
          [:form {:method "post"}
           [:input {:type "hidden" :name "action" :value "suggest"}]
           [:textarea.translation-input.form-control
            {:placeholder "Enter translation here"
             :required true
             :name "text"
             :rows 3}]
           [:div.translation-actions
            #_[:div.char-count "0 • " (count (:content active-string))]
            [:button.btn.btn-primary "Save"]]]
          [:section
           [:h3.visually-hidden "Suggestions"]
           [:ul.list-unstyled
            (for [translation translations]
              (render-suggestion translation))]]]
         [:section.comments
          [:div.comments_history
           [:h2.fs-6 "Comments"]
           (for [comment comments] (render-comment comment))]
          [:form.comment-input {:method "post"}
           [:input {:type "hidden" :name :action :value :comment}]
           [:textarea.form-control
            {:placeholder "Leave a comment"
             :name "comment"
             :required true
             :rows 2}]
           [:button.btn (icon "send-plane-2-line")]]]))])))
