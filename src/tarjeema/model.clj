(ns tarjeema.model)

(defn- project-roles [{:keys [user-id roles]} project]
  (-> roles
      (concat (when (= (:owner-id project) user-id)
                #{:owner}))
      set))

(defn user-in-project [user project]
  (assoc user
         :roles (project-roles user project)))

(def ^:private role-can-approve? #{:owner})

(defn can-delete-translation? [user translation]
  (or (= (:user-id user) (:user-id translation))
      (some #'role-can-approve? (:roles user))))

