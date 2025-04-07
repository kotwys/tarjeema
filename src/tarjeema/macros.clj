(ns tarjeema.macros
  (:require [ring.util.response :as res]
            [tarjeema.views.layout :as layout]))

(defn provide-user-data [{:keys [user-data]}]
  (some-> user-data
          (select-keys [:user_name :user_email :roles])))

(defmacro with-request-data [req & body]
  `(binding [layout/*user-data* (provide-user-data ~req)]
     ~@body))

(defmacro render [req & body]
  `(-> (with-request-data ~req ~@body)
       str
       (res/response)
       (res/content-type "text/html")))
