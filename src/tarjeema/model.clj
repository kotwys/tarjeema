(ns tarjeema.model
  (:require [tarjeema.db :as db]
            [toucan2.core :as t2]))

(defn target-languages [project]
  (->> (db/get-languages)
       vals
       (filter #(not (= (:lang-id %) (:source-lang-id project))))))

(defn- project-roles [{:keys [user-id]} project]
  (when (= (:owner-id project) user-id) #{:owner}))

(defn- language-roles [user project lang]
  (when (t2/select-one ::db/proofreader
                       :user-id    (:user-id user)
                       :project-id (:project-id project)
                       :lang-id    (:lang-id lang))
    #{:proofreader}))

(defn user-in-project [user {:keys [project lang]}]
  (let [roles (->> (:roles user)
                   (concat (project-roles user project))
                   (concat (when lang (language-roles user project lang)))
                   set)]
    (assoc user :roles roles)))

(def ^:private role-can-approve? #{:owner :proofreader})

(defn can-delete-translation? [user translation]
  (or (= (:user-id user) (:user-id translation))
      (some #'role-can-approve? (:roles user))))

(defn can-approve? [user]
  (some #'role-can-approve? (:roles user)))

(defn can-vote? [user translation]
  (not (or (= (:user-id user) (:user-id translation))
           (some #'role-can-approve? (:roles user)))))

(defn can-delete-comment? [user comment]
  (or (= (:user-id user) (:user-id comment))
      (some #{:owner} (:roles user))))
