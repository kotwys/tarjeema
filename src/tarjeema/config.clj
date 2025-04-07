(ns tarjeema.config
  (:require [clojure.spec.alpha :as s]
            [aero.core :refer [read-config]]
            [mount.core :refer [defstate]]))

(s/def ::db-uri (s/and string? seq))
(s/def ::port   (s/and integer? pos?))
(s/def ::config
  (s/keys :req-un [::db-uri
                   ::port]))

;; TODO: error reporting -M 06.04.2025
(defn get-config []
  (->> (read-config "config.edn")
       (s/conform ::config)))

(defstate config :start (get-config))
