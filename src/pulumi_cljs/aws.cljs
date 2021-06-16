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
  (let [pw (p/resource random/RandomPassword (p/id name) parent
             {:length 32
              :special false}
             {:additionalSecretOutputs ["result"]})]
    (p/resource aws/ssm.Parameter (p/id name) parent
                {:name (str "/" (pulumi/getProject)
                            "/" (pulumi/getStack)
                            "/" name)
                 :value (:result pw)
                 :type "SecureString"})))

(defn- assume-role-policy
  "Generate an AWS Assume Role policy granting access to the given services"
  [services]
  (p/json {:Version "2008-10-17"
           :Statement [{:Effect "Allow"
                        :Principal {:Service services}
                        :Action "sts:AssumeRole"}]}))

(defn- policy-stmt
  "Generate an AWS IAM Policy Statement that grants 'Allow' to the
   specified resource, for the given list of actions."
  [resource actions]
  {:Resource resource
   :Effect "allow"
   :Action actions})

(defn allow-stmt
  "Generate a JSON fragment for an AWS IAM policy, granting access to
  the given actions for the given resource ARN."
  [resources actions]
  {:Resource resources
   :Effect "Allow"
   :Action actions})

(defn role
  "Convenicne function for creating an IAM role. Takes the following options:

   services - List of AWS services (e.g, s3.amazonaws.com) that can assume this role
   policies - List of Policy resources to attach to the rule
   statements - List of Statements (json fragments) to add to the Rule (as an inline policy.)
   pass-role? - Add statements for iam:PassRole and iam:GetRole
   opts - options map for the Role resource"
  [name parent {:keys [services policies statements pass-role? opts]}]
  (let [role (p/resource aws/iam.Role name parent
               {:name name
                :assumeRolePolicy (assume-role-policy services)}
               (or opts {}))
        policies (or policies [])
        statements (or statements [])
        statements (if pass-role?
                     (conj statements (policy-stmt (:arn role) ["iam:PassRole" "iam:GetRole"]))
                     statements)
        policies (if (empty? statements)
                   policies
                   (let [policy-json (p/json {:Version "2012-10-17"
                                              :Statement statements})
                         policy (p/resource aws/iam.Policy name role
                                  {:policy policy-json})]
                     (conj policies policy)))]
    (doall (map-indexed (fn [idx policy]
                          (let [arn (if (instance? pulumi/Resource policy)
                                      (:arn policy)
                                      policy)]
                            (p/resource aws/iam.RolePolicyAttachment (str name "-" idx) role
                              {:role (:name role)
                               :policyArn arn}))) policies))
    role))

