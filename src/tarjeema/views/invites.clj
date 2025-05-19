(ns tarjeema.views.invites
  (:require [clojure.string :as str]
            [tarjeema.views.components :refer [action-btn render-date]]
            [tarjeema.views.layout :as layout]))

(defn render-single-invite [invite mk-url]
  (let [id (str "inviteContent" (:invite-id invite))]
    [:div.accordion-item
     [:h2.accordion-header
      [:button.accordion-button.collapsed
       {:type "button"
        :data-bs-toggle "collapse"
        :data-bs-target (str "#" id)
        :aria-expanded "false"
        :aria-controls id}
       "Invite " (str/upper-case (:invite-code invite))
       (cond
         (not (:is-active invite))
         [:span.ms-1.badge.bg-danger "Revoked"]

         (and (not (zero? (:max-usage-count invite)))
              (<= (:max-usage-count invite) (count (:usages invite))))
         [:span.ms-1.badge.bg-danger "Expired"])]]
     [:div.accordion-collapse.collapse {:id id}
      [:div.accordion-body
       [:div.row.mb-2
        [:div.col
         [:strong "URL"] "​: "
         (let [url (mk-url invite)]
           [:a {:href url} url])]
        [:div.col
         [:strong "Issuer"] "​: " (-> invite :issuer :user-name)]
        [:div.col
         [:strong "Max Usage Count"] ":​ "
         (if (zero? (:max-usage-count invite))
           "∞"
           (:max-usage-count invite))]
        [:div.col-1
         (action-btn {:action :revoke
                      :invite-id (:invite-id invite)}
                     {:class "btn btn-outline-danger"}
                     "Revoke")]]
       [:h3 "Usages"]
       [:table.table.table-stripped
        [:thead
         [:tr
          [:th "User ID"]
          [:th "Name"]
          [:th "E-mail"]
          [:th "Time"]]]
        [:tbody
         (for [{:keys [registered-at user]} (-> invite :usages)]
           [:tr
            [:td (-> user :user-id)]
            [:td (-> user :user-name)]
            [:td (-> user :user-email)]
            [:td (render-date registered-at)]])]]]]]))

(defn render-invites [{:keys [invites mk-url]}]
  (binding [layout/*page-title* "Invites"]
    (layout/app
     [:main.page-main
      (when (seq invites)
        [:div.accordion
         (for [invite invites]
           (render-single-invite invite mk-url))])
      [:section.mt-2
       [:h2 "Issue an Invite"]
       [:form {:method "post"}
        [:input {:type "hidden" :name :action :value :issue}]
        [:div.row.mb-2
         [:label.col-3.col-form-label {:for "usage"} "Max Usage Count"]
         [:div.col-4
          [:input#usage.form-control {:type  "number"
                                      :name  "usages"
                                      :value "0"
                                      :min   "0"
                                      :required true}]
          [:div.form-text "Leave it as zero to make it limitless."]]]
        [:button.btn.btn-primary "Issue"]]]])))
