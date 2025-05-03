(ns tarjeema.db
  (:require [buddy.hashers :as hashers]
            [mount.core :refer [defstate]]
            [pg.core :as pg]
            [pg.fold :as fold]
            [pg.honey :as pgh]
            [tarjeema.config :refer [config]]))

(defstate conn
  :start (pg/connect (:db-uri config))
  :stop  (.close conn))

;;;; Users

(def ^:private hash-opts
  {:alg        :bcrypt+sha512
   :iterations 10})

(defn find-user-by-email [email]
  (pgh/find-first conn :users {:user_email email}))

(defn get-user-roles [id]
  (pgh/execute conn
               {:select [:role_name]
                :from   :user_roles
                :join   [:roles [:using :role_id]]
                :where  [:= :user_id id]}
               {:as (fold/into (map (comp keyword :role_name)) #{})}))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn register-user
  "Registers a user by given information."
  [data]
  (let [{:keys [password]} data
        password-hash (hashers/derive password hash-opts)
        row (-> data
                (select-keys [:user_email :user_name])
                (assoc :password_hash password-hash))]
    (pgh/insert-one conn :users row)))

(defn auth-user
  "Authorises a user. When given credentials are correct, returns the user
  data. Otherwise returns nil."
  [email password]
  (when-let [{:as user :keys [user_id password_hash]}
             (find-user-by-email email)]
    (when (-> password (hashers/verify password_hash) :valid)
      (let [roles (get-user-roles user_id)]
        (assoc user :roles roles)))))

;;;; Languages

(def ^:private languages (atom {}))

(defn get-languages []
  (if (seq @languages)
    @languages
    (let [result (pgh/find conn
                           :languages {}
                           {:order-by :bcp47
                            :as (fold/index-by (comp #'keyword :bcp47))})]
      (reset! languages result))))

;;;; Projects

(defn fetch-projects []
  (pgh/find conn :projects))

(defn find-project-by-id [id]
  (when-let [proj (pgh/execute conn
                               {:select [:project_id
                                         :project_name
                                         :project_description
                                         :l/bcp47
                                         :o/user_id :o/user_name :o/user_email]
                                :from   :projects
                                :join   [[:languages :l]
                                         [:= :source_lang_id :l/lang_id]

                                         [:users :o]
                                         [:= :owner_id :o/user_id]]
                                :where  [:= :project_id id]}
                               {:as fold/first})]
    {:project_id          (:project_id proj)
     :project_name        (:project_name proj)
     :project_description (:project_description proj)
     :source_lang         ((keyword (:bcp47 proj)) (get-languages))
     :owner               {:user_id    (:user_id proj)
                           :user_name  (:user_name proj)
                           :user_email (:user_email proj)}}))

(defn create-project [fields]
  (-> (pgh/insert-one conn :projects fields {:returning [:project_id]})
      :project_id))

(defn update-project [id fields]
  (let [result (pgh/update conn :projects
                           fields
                           {:where [:= :project_id id]
                            :returning [:project_id]})]
    (if (empty? result)
      (throw (ex-info (str "No project #" id " was found.")
                      {:type ::db-error
                       :id   id}))
      (-> result first :project_id))))

(defn is-project-owner? [{:keys [user_id]} id]
  (let [data (pgh/get-by-id conn :projects id
                            {:pk :project_id
                             :fields [:owner_id]})]
    (= (:owner_id data) user_id)))

(defn upload-strings
  "Uploads source strings for a project ID from a UTF-8-encoded JSON object."
  [id json]
  (pg/execute conn
              "MERGE INTO strings AS s
               USING ( SELECT *
                         FROM json_each_text
                                ( convert_from ( $2::bytea , 'UTF8' )::json )
                     ) AS j
                  ON ( s.project_id , s.string_name ) = ( $1 , j.key )
                WHEN MATCHED THEN UPDATE SET string_text = j.value
                WHEN NOT MATCHED BY SOURCE AND s.project_id = $1 THEN DELETE
                WHEN NOT MATCHED BY TARGET THEN
                     INSERT ( project_id , string_name , string_text )
                     VALUES ( $1 , j.key , j.value )"
              {:params [id json]}))

;;;; Strings

(defn get-strings [project-id]
  (pgh/find conn :strings
            {:project_id project-id}
            {:order-by :string_name}))

(defn get-string-info [string-id]
  (pgh/get-by-id conn :strings string-id
                 {:fields [:string_name :string_text]
                  :pk :string_id}))

;;;; Translations

(defn suggest-translation [fields]
  (-> (pgh/insert-one conn :translations
                      fields
                      {:returning [:translation_id]})
      :translation_id))

(defn- translation->clj [row]
  {:translation_id   (:translation_id row)
   :translation_text (:translation_text row)
   :user             {:user_id    (:user_id row)
                      :user_name  (:user_name row)
                      :user_email (:user_email row)}
   :suggested_at     (:suggested_at row)})

(defn get-translations [string-id lang-id]
  (pgh/execute conn
               {:select   [:translation_id
                           :translation_text
                           :u/user_id :u/user_name :u/user_email
                           :suggested_at]
                :from     :translations
                :join     [[:users :u] [:using :user_id]]
                :where    [:and
                           [:= :lang_id lang-id]
                           [:= :string_id string-id]]
                :order-by [[:suggested_at :desc]]}
               {:as (fold/map #'translation->clj)}))
