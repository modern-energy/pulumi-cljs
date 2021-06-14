(ns pulumi-cljs.core
  "Tools for working with Pulumi from CLJS"
  (:require ["@pulumi/pulumi" :as p]
            [clojure.walk :as walk])
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
  "Retrieve a single value from the Pulumi configuration for the current
  project. If the result is the string value 'false', returns boolean
  'false' instead (this avoids a lot of unexpected issues with YAML
  parsing."
  [key]
  (let [val (.require ^p/Config (load-cfg (p/getProject)) key)]
    (if (= "false" (.toLowerCase val))
      false
      val)))

(defn cfg-obj
  "Retrieve a data structure value from the Pulumi configuration for the
  current project, converting data structures to Clojure data."
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

(defn prepare-output
  "Walk a data structure and replace all Resource objects with a map
  containing their Pulumi URN and provider ID, then convert to a JS
  object."
  [output]
  (clj->js
    (walk/prewalk (fn [form]
                    (if (instance? p/Resource form)
                      {:urn (:urn form)
                       :id (:id form)}
                      form))
      output)))

