(ns tarjeema.routes.invites
  (:require [better-cond.core :as b]
            [clojure.string :as str]
            [methodical.core :as m]
            [nano-id.core :refer [custom]]
            [reitit.core :as r]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [tarjeema.db :as db]
            [tarjeema.macros :refer [render]]
            [tarjeema.middleware :refer [wrap-roles]]
            [tarjeema.routes.core :refer [get-route-url]]
            [tarjeema.routes.join :as-alias join]
            [tarjeema.views.invites :refer [render-invites]]
            [toucan2.core :as t2]))

(def generate-code (custom "abdefghknqrtwxy347" 6))

(m/defmulti handle-action (fn [{:keys [action]} _] action))

(m/defmethod handle-action ::issue
  [{:keys [user]} {:strs [usages]}]
  (let [usages (some-> usages parse-long)]
    (when (nil? usages)
      (throw (ex-info "Usage count should be specified." {})))
    (when (neg? usages)
      (throw (ex-info "Usage count should not be negative." {})))
    (t2/insert! ::db/invite
                :issuer-id (:user-id user)
                :invite-code (generate-code)
                :max-usage-count usages)))

(m/defmethod handle-action ::revoke
  [_ {:strs [invite-id]}]
  (b/cond
    :let [invite-id (some-> invite-id parse-long)]
    (nil? invite-id)
    (throw (ex-info "Invite ID should be provided." {}))

    :let [invite (t2/select-one ::db/invite invite-id)]
    (nil? invite)
    (throw (ex-info "Invite not found." {}))

    (-> invite (assoc :is-active false) (t2/save!))))

(defn invites-action
  [{:keys [uri user-data], {:as params :strs [action]} :params} res _raise]
  (when (str/blank? action)
    (throw (ex-info "Action should be specified." {})))
  (let [ctx {:user   user-data
             :action (keyword (namespace ::here) action)}]
    (handle-action ctx params)
    (res (res/redirect uri :see-other))))

(defn invites [{:as req ::r/keys [router]} res _raise]
  (let [base    (str (name (:scheme req)) "://" (get (:headers req) "host"))
        invites (-> (t2/select ::db/invite)
                    (t2/hydrate :issuer [:usages :user]))
        mk-url  #(str base
                      (get-route-url router ::join/join
                                     :path-params {:code (:invite-code %)}))]
    (-> (render req
          (render-invites {:invites invites
                           :mk-url  mk-url}))
        res)))

(def routes
  ["/invites" {:get        #'invites
               :post       {:handler    #'invites-action
                            :middleware [[wrap-params]]}
               :middleware [[wrap-roles #{:admin}]]}])
