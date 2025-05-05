(ns tarjeema.db
  (:require [buddy.hashers :as hashers]
            [camel-snake-kebab.core :as csk]
            [methodical.core :as m]
            [mount.core :refer [defstate]]
            [next.jdbc :as jdbc]
            [tarjeema.config :refer [config]]
            [toucan2.core :as t2]
            [toucan2.execute :as t2.exec]
            [toucan2.honeysql2]
            [toucan2.jdbc.options]
            [toucan2.realize :refer [realize]]))

(defstate conn
  :start (jdbc/get-datasource (:db-uri config)))

(m/defmethod t2/do-with-connection :default [_ f]
  (t2/do-with-connection conn f))

;; Enable kebab-case columns
(swap! toucan2.honeysql2/global-options assoc :quoted-snake true)
(swap! toucan2.jdbc.options/global-options assoc :label-fn csk/->kebab-case)

(def ^:private hash-opts
  {:alg        :bcrypt+sha512
   :iterations 10})

(defn- sql [query & [{into* :into, :keys [model]}]]
  (let [jdbc-fn   (if-let [[tx coll] into*]
                    #(into coll tx (t2.exec/reducible-query % model query))
                    #(t2.exec/query % model query))]
    (t2/do-with-connection nil jdbc-fn)))

;;;; Users

(m/defmethod t2/table-name ::user [_] "users")
(m/defmethod t2/primary-keys ::user [_] [:user-id])
(t2/define-default-fields ::user [:user-id :user-email :user-name])

(m/defmethod t2/model-for-automagic-hydration
  [:default :user] [_ _]
  ::user)

(m/defmethod t2/simple-hydrate [::user :roles]
  [_ _ {:keys [user-id] :as instance}]
  (let [roles (sql {:select [:role-name]
                    :from   [:user-roles]
                    :join   [:roles [:using :role-id]]
                    :where  [:= :user-id user-id]}
                   {:into [(map (comp keyword :role_name)) #{}]})]
    (assoc instance :roles roles)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn register-user
  "Registers a user by given information."
  [data]
  (let [{:keys [password]} data
        password-hash (hashers/derive password hash-opts)
        row (-> data
                (select-keys [:user-email :user-name])
                (assoc :password-hash password-hash))]
    (t2/insert-returning-instance! ::user row)))

(defn auth-user
  "Authorises a user. When given credentials are correct, returns the user
  data. Otherwise returns nil."
  [email password]
  (when-let [{:as user :keys [password-hash]}
             (t2/select-one [::user :*] :user-email email)]
    (when (-> password (hashers/verify password-hash) :valid)
      (t2/hydrate user :roles))))

;;;; Languages

(m/defmethod t2/table-name ::language [_] "languages")
(m/defmethod t2/primary-keys ::language [_] [:lang-id])

(def ^:private languages (atom {}))

(defn get-languages []
  (if (seq @languages)
    @languages
    (reset! languages
            (into {}
                  (map (juxt :bcp-47 identity))
                  (t2/select-fn-reducible #'realize "languages")))))

(m/defmethod t2/model-for-automagic-hydration
  [:default :lang] [_ _]
  ::language)

;;;; Projects

(m/defmethod t2/table-name ::project [_] "projects")
(m/defmethod t2/primary-keys ::project [_] [:project-id])

(m/defmethod t2/model-for-automagic-hydration
  [::project :owner] [_ _]
  ::user)

(m/defmethod t2/model-for-automagic-hydration
  [::project :source-lang] [_ _]
  ::language)

(defn create-or-update-project
  [{:as project-data, :keys [project-id]} {:keys [user-id]}]
  (if project-id
    (let [project (t2/select ::project project-id)]
      (when (nil? project)
        (throw (ex-info "No such project" {:type ::input-error})))
      (when-not (= (:owner-id project) user-id)
        (throw (ex-info "Not a project owner." {:type ::access-error})))
      (-> project
          (merge project-data)
          (t2/save!)))

    (t2/insert-returning-instance! ::project project-data)))

(defn upload-strings
  "Uploads source strings for a project ID from a UTF-8-encoded JSON object."
  [{:keys [project-id]} json]
  (jdbc/execute! conn
                 ["CALL upload_strings
                     ( ?::int
                     , convert_from ( ?::bytea , 'UTF8' )::json
                     )"
                  project-id json]))

;;;; Strings

(m/defmethod t2/table-name ::string [_] "strings")
(m/defmethod t2/primary-keys ::string [_] [:string-id])

(defn get-strings [{:keys [project-id]}]
  (t2/select :strings
             :project-id project-id
             {:order-by :string-name}))

;;;; Translations

(m/defmethod t2/table-name ::translation [_] "translations")
(m/defmethod t2/primary-keys ::translation [_] [:translation-id])

(defn suggest-translation [translation-data]
  (t2/insert-returning-instance! ::translation translation-data))

(m/defmethod t2/table-name ::approval [_] "translation_approvals")
(m/defmethod t2/primary-keys ::approval [_] [:translation-id])

(m/defmethod t2/batched-hydrate [::translation :approval]
  [_ _ instances]
  (let [ids     (into #{} (map :translation-id) instances)
        results (sql {:select [:*]
                      :from   [:translation-approvals]
                      :where  [:in :translation-id ids]}
                     {:model ::approval
                      :into  [(map (juxt :translation-id realize)) {}]})]
    (for [instance instances]
      (assoc instance :approval (get results (:translation-id instance))))))

(defn approve-translation [translation user]
  (let [data {:translation-id (:translation-id translation)
              :user-id        (:user-id user)}]
    (t2/insert-returning-instance! ::approval data)))

(defn disapprove-translation [translation]
  (t2/delete! ::approval :translation-id (:translation-id translation)))
