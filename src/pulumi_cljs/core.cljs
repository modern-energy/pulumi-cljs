(ns pulumi-cljs.core
  "Tools for working with Pulumi from CLJS"
  (:require ["@pulumi/pulumi" :as p]
            [clojure.walk :as walk]
            [clojure.string :as str])
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
  parsing.

  If the key is missing, throws an error unless a default value is supplied"
  ([key] (cfg key ::required))
  ([key default]
   (let [config (load-cfg (p/getProject))
         val (if (= default ::required)
               (.require ^p/Config config key)
               (.get ^p/Config config key))
         val (or val default)]
     (cond
       (= "false" (.toLowerCase val)) false
       (= "nil" (.toLowerCase val)) nil
       (= "null" (.toLowerCase val)) nil
       :else val))))

(defn cfg-obj
  "Retrieve a data structure value from the Pulumi configuration for the
  current project, converting data structures to Clojure data.

  If the key is missing, throws an error unless a default value is supplied."
  ([key] (cfg-obj key ::required))
  ([key default]
   (let [config (load-cfg (p/getProject))
         val (if (= default ::required)
               (.requireObject ^p/Config config key)
               (.getObject ^p/Config config key))]
     (if val
       (js->clj val :keywordize-keys true)
       default))))

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
  [& args]
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

(defn json
  "Convert a Clojure data structure to a JSON string, handling nested
  Pulumi Output values"
  [data]
  (.apply (p/output (clj->js data)) #(js/JSON.stringify %)))

(defn group
  "Create a Component Resource to group together other sets of resources"
  [name opts]
  (new p/ComponentResource (str "group:" name) name (js-obj) (clj->js opts) false))

(defn id
  "Convenience function making it easier to generate standardized resource IDs
   based on the Pulumi project name."
  [& suffixes]
  (str/join "-" (cons (p/getProject) suffixes)))

(defn invoke
  "Invoke a Pulumi function. Converts args map and opts from
  ClojureScript to JavaScript. Returns a Pulumi Output, not a Promise,
  to avoid special handling. Does not convert the result to a CLJS
  object (CLJS objects behave oddly when wrapped in an Output)"
  [f & args]
  (p/output (apply f (map clj->js args))))
