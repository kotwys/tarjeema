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
            [toucan2.realize :refer [realize]]
            [java-time.api :as jt]))

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
  "Registers a user by given information and a given invite."
  [data & {:keys [invite]}]
  (let [{:keys [password]} data
        password-hash (hashers/derive password hash-opts)
        row (-> data
                (select-keys [:user-email :user-name])
                (assoc :password-hash password-hash))]
    (t2/with-transaction [c nil]
      (let [{:as user :keys [user-id]}
            (t2/insert-returning-instance! :conn c ::user row)]
        (when invite
          (t2/insert! ::invite-usage
                      :invite-id (:invite-id invite)
                      :user-id user-id))
        user))))

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
                  (t2/select-fn-reducible #'realize
                                          ::language
                                          {:order-by [:bcp47]})))))

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
    (let [project (t2/select-one ::project project-id)]
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

(m/defmethod t2/table-name ::proofreader [_] "proofreaders")

;;;; Strings

(m/defmethod t2/table-name ::string [_] "strings")
(m/defmethod t2/primary-keys ::string [_] [:string-id])

(defn get-strings [project lang]
  (sql ["WITH applicable_translations AS
              ( SELECT string_id , translation_id , translation_text
                     , RANK() OVER
                       ( PARTITION BY string_id
                         ORDER BY suggested_at DESC ) AS rnk
                  FROM strings
                  JOIN translations USING ( string_id )
                 WHERE ( project_id , lang_id ) = ( ? , ? ) )
            , translated AS
              ( SELECT string_id , 'translated' AS status
                     , translation_text
                  FROM applicable_translations
                 WHERE rnk = 1 )
            , approved AS
              ( SELECT string_id , 'approved' AS status
                     , translation_text
                  FROM applicable_translations
                  JOIN translation_approvals USING ( translation_id ) )
         SELECT strings.*
              , COALESCE
                ( approved.translation_text
                , translated.translation_text
                ) AS text
              , COALESCE
                ( approved.status
                , translated.status
                , 'untranslated'
                ) AS status
           FROM strings
                LEFT JOIN translated USING ( string_id )
                LEFT JOIN approved USING ( string_id )"
        (:project-id project) (:lang-id lang)]
       {:model ::string}))

;;;; Translations

(m/defmethod t2/table-name ::translation [_] "translations")
(m/defmethod t2/primary-keys ::translation [_] [:translation-id])

(defn suggest-translation [translation-data]
  (t2/insert-returning-instance! ::translation translation-data))

(m/defmethod t2/table-name ::vote [_] "translation_votes")
(m/defmethod t2/primary-keys ::vote [_] [:translation-id :user-id])

;; TODO: maybe refactor this similar logic some time
(m/defmethod t2/batched-hydrate [::translation :rating]
  [_ _ instances]
  (let [ids     (into #{} (map :translation-id) instances)
        results (sql {:select   [:translation-id
                                 [[:sum [:case :is-in-favor 1 :else -1]]
                                  :rating]]
                      :from     [:translation-votes]
                      :where    [:in :translation-id ids]
                      :group-by [:translation-id]}
                     {:into [(map (juxt :translation-id :rating)) {}]})]
    (for [instance instances]
      (assoc instance :rating (get results (:translation-id instance) 0)))))

(defn get-votes [instances user]
  (if (empty? instances)
    instances
    (let [ids  (into #{} (map :translation-id) instances)
          results (sql {:select [:translation-id :is-in-favor]
                        :from   [:translation-votes]
                        :where  [:and
                                 [:in :translation-id ids]
                                 [:= :user-id (:user-id user)]]}
                       {:into [(map (juxt :translation-id :is-in-favor)) {}]})]
      (for [instance instances]
        (assoc instance :vote (get results (:translation-id instance)))))))

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

(defn build-translation [{:keys [project-id]} {:keys [lang-id]}]
  (-> (t2.exec/query-one ["SELECT build_translation ( ?::int , ?::int )::text"
                          project-id lang-id])
      :build-translation))

;;;; Comments

(m/defmethod t2/table-name ::comment [_] "string_comments")
(m/defmethod t2/primary-keys ::comment [_] [:comment-id])

;;;; Reports

(defn language-completeness [{:keys [project-id source-lang-id]}]
  (sql ["SELECT lang_id , lang_name , bcp47
              , SUM
                ( EXISTS
                  ( SELECT *
                      FROM translations AS t
                     WHERE t.string_id = s.string_id
                       AND t.lang_id = l.lang_id
                  )::int
                )::float / COUNT ( string_id ) AS translated
              , SUM
                ( EXISTS
                  ( SELECT *
                      FROM translations AS t
                          JOIN translation_approvals USING ( translation_id )
                         WHERE t.string_id = s.string_id
                           AND t.lang_id = l.lang_id
                      )::int
                )::float / COUNT ( string_id ) AS approved
           FROM strings AS s , languages AS l
          WHERE project_id = ? AND lang_id <> ?
          GROUP BY lang_id
          ORDER BY bcp47"
        project-id source-lang-id]
       {:model ::language}))

(defn- mk-timestamptz [local-date]
  (let [t (-> local-date
              (jt/zoned-date-time (jt/local-time 0) (jt/zone-id)))]
    [:timestamptz (jt/format :iso-offset-date-time t)]))

(defn- report-filter [{:keys [project lang-id since until]}]
  [:and
   [:= :project-id (:project-id project)]
   (when lang-id [:= :lang-id lang-id])
   (when since [:< (mk-timestamptz since) :suggested-at])
   (when until
     [:> (mk-timestamptz (jt/+ until (jt/days 1))) :suggested-at])])

(defn overall-activity [ctx]
  (-> {:with   [[:applicable-translations
                 {:select [:*]
                  :from   [:translations]
                  :join   [:strings [:using :string-id]]
                  :where  (report-filter ctx)}]]
       :select [[{:select [[[:count :*]]]
                  :from   [:applicable-translations]}
                 :translated]
                [{:select [[[:count :*]]]
                  :from   [:translation-approvals]
                  :join   [:applicable-translations [:using :translation-id]]}
                 :approved]]}
      (sql)
      (first)))

(defn top-members [ctx]
  (sql {:select  [:user-name
                  [[:string_agg [:distinct :lang-name] [:inline "; "]]
                   :languages]
                  [[:count :*] :translated]
                  [[:count
                    [:nest
                     {:select [[[:inline 1]]]
                      :from   [:translation-approvals]
                      :where  [:= :translation-id :t.translation-id]}]]
                   :winning]]
        :from     [[:translations :t]]
        :join     [:languages [:using :lang-id]
                   :users [:using :user-id]
                   :strings [:using :string-id]]
        :where    (report-filter ctx)
        :group-by [:users.user-id]
        :order-by [[:translated :desc]]}))

;;;; Invites

(m/defmethod t2/table-name ::invite [_] "invites")
(m/defmethod t2/primary-keys ::invite [_] [:invite-id])
(m/defmethod t2/model-for-automagic-hydration
  [::invite :issuer] [_ _]
  ::user)

(m/defmethod t2/table-name ::invite-usage [_] "invite_usages")
(m/defmethod t2/primary-keys ::invite-usage [_] [:user-id])
(m/defmethod t2/simple-hydrate [::invite :usages]
  [_ _ {:keys [invite-id] :as instance}]
  (let [usages (sql {:select   [:*]
                     :from     [:invite-usages]
                     :where    [:= :invite-id invite-id]}
                    {:model ::invite-usage})]
    (assoc instance :usages usages)))

(defn valid-invite? [{:keys [invite-id is-active max-usage-count]}]
  (and is-active
       (or (zero? max-usage-count)
           (let [result (sql {:select   [[[:count :*] :count]]
                              :from     [:invite-usages]
                              :where    [:= :invite-id invite-id]})
                 usage-count (-> result first :count)]
             (< usage-count max-usage-count)))))
