(ns pulumi-cljs.aws
  "Utilities for working with AWS resources"
  (:require ["@pulumi/pulumi" :as pulumi]
            ["@pulumi/aws" :as aws]
            ["@pulumi/random" :as random]
            [pulumi-cljs.core :as p]))


(defn ssm-password
  "Create a new random password in SSM, and return the SSM Parameter resource.

   The Parameter key format is is /{project}/{stack}/{name}"
  [parent name]
  (let [pw (p/resource random/RandomPassword name parent
             {:length 32
              :special false}
             {:additionalSecretOutputs ["result"]})]
    (p/resource aws/ssm.Parameter name parent
                {:name (str "/" (pulumi/getProject)
                            "/" (pulumi/getStack)
                            "/" name)
                 :value (:result pw)
                 :type "SecureString"
                 })))
