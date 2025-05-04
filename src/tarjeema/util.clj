(ns tarjeema.util)

(defn this-uri [{:keys [uri query-string]}]
  (if query-string
    (str uri "?" query-string)
    uri))
