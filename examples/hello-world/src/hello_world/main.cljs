(ns hello-world.main
  (:require ["@pulumi/pulumi" :as pulumi]
            ["@pulumi/aws" :as aws]
            [pulumi-cljs.core :as p]))

(defn ^:export stack
  "Create the Pulumi stack, returning its outputs"
  []
  (let [bucket (p/resource aws/s3.Bucket "test-bucket" nil
                 {:bucket (p/cfg "bucket-name")})]

    (p/prepare-output {:bucket bucket})))
