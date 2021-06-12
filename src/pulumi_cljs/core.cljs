(ns pulumi-cljs.core
  "Tools for working with Pulumi from CLJS"
  (:require ["@pulumi/pulumi" :as p])
  (:require-macros [pulumi-cljs.core]))

(defn resource
  "Create a Pulumi resource"
  ([type name parent args]
   (resource type name parent args {}))
  ([type name parent args options]
   (let [opts (if parent
                (assoc options :parent parent)
               options)]
     (new type name
       (clj->js args)
       (clj->js opts)))))

(defn load-cfg*
  "Load the Pulumi configuration for a project."
  [project]
  (p/Config. project))

(def load-cfg (memoize load-cfg*))

(defn cfg
  "Retrieve a value from the Pulumi configuration for the current
  project, converting data structures to Clojure data."
  [key]
  (let [c (load-cfg (p/getProject))]
    (js->clj
      (.requireObject ^p/Config c key)
      :keywordize-keys true)))

(extend-protocol ILookup
  p/Resource
  (-lookup
    ([o k]
     (let [k (clj->js k)]
       (if-let [v (aget o k)]
         v
         (throw (ex-info (str "Resource does not have property or output with key " k)
                  {:key k})))))
    ([o k not-found]
     (if-let [v (aget o (clj->js k))]
       v
       not-found))))

(defn all*
  "Alias for pulumi.all since JS modules can't be imported into the Clojure macro namespace"
  [args]
  (apply p/all args))
