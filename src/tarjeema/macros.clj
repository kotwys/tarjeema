(ns tarjeema.macros
  (:require [ring.util.response :as res]
            [tarjeema.views.layout :as layout]
            [toucan2.core :as t2]))

(defn provide-user-data [{:keys [user-data]}]
  (some-> user-data
          t2/current
          (select-keys [:user-name :user-email :roles])))

(defmacro with-request-data [req & body]
  `(binding [layout/*user-data* (provide-user-data ~req)]
     ~@body))

(defmacro render [req & body]
  `(-> (with-request-data ~req ~@body)
       str
       (res/response)
       (res/content-type "text/html")))
