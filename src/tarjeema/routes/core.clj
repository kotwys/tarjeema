(ns tarjeema.routes.core
  (:require [reitit.core :as r]))

(defn get-route-url
  "Returns the route URL for a given named route and optional path and query
  parameters."
  [router name & {:keys [path-params query-params]}]
  (-> router
      (r/match-by-name name path-params)
      (r/match->path query-params)))
