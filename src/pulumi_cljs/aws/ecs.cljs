(ns pulumi-cljs.aws.ecs
  "Utilities for building ECS Fargate tasks"
  (:require ["@pulumi/pulumi" :as pulumi]
            ["@pulumi/aws" :as aws]
            [pulumi-cljs.core :as p]
            [pulumi-cljs.aws :as aws-utils]))

(def elb-account-id
  {"us-east-1""127311923021"
   "us-east-2" "033677994240"
   "us-west-1" "027434742980"
   "us-west-2" "797873946194"
   "af-south-1" "098369216593"
   "ca-central-1" "985666609251"
   "eu-central-1" "054676820928"
   "eu-west-1" "156460612806"
   "eu-west-2" "652711504416"
   "eu-south-1" "635631232127"
   "eu-west-3" "009996457667"
   "eu-north-1" "897822967062"
   "ap-east-1" "754344448648"
   "ap-northeast-1"	"582318560864"
   "ap-northeast-2"	"600734575887"
   "ap-northeast-3"	"383597477331"
   "ap-southeast-1"	"114774131450"
   "ap-southeast-2" "783225319266"
   "ap-south-1"	"718504428378"
   "me-south-1" "076674570225"
   "sa-east-1" "507241528517"
   "us-gov-west-1" "048591011584"
   "us-gov-east-1" "190560391635"
   "cn-north-1"	"638102146993"
   "cn-northwest-1" "037604701340"})

(defn- access-log-bucket-policy
  "Generate a JSON bucket policy for access logs.

See https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html#access-logging-bucket-permissions"
  [provider bucket]
  (let [bucket-contents-arn (str "arn:aws:s3:::" bucket "/*")]
    (p/json {:Version "2012-10-17"
             :Statement [{:Effect "Allow"
                          :Principal {:AWS (p/all [region (:region provider)]
                                             (str "arn:aws:iam::" (get elb-account-id region) ":root"))}
                          :Action "s3:PutObject"
                          :Resource bucket-contents-arn}
                         {:Effect "Allow"
                          :Principal {:Service "delivery.logs.amazonaws.com"}
                          :Action "s3:PutObject"
                          :Resource bucket-contents-arn}
                         {:Effect "Allow"
                          :Principal {:Service "delivery.logs.amazonaws.com"}
                          :Action "s3:GetBucketAcl"
                          :Resource (str "arn:aws:s3:::" bucket)}]})))

(defn service
  "Build a multi-az AWS Fargate Service with load balancer.

Config properties:

  :vpc-id - The VPC to use

  :container-port - The port on the Container that listens for inbound traffic

  :cluster-id - ECS cluster to use

  :zone - The hosted zone in which to create a DNS entry.

  :subdomain - The subdomain to use for the DNS entry. May be null or an empty string if no subdomain is desired.

  :lb - Map of properties for the load balancer

        :ingress-cidrs - IP ranges that can access the service via the load balancer

        :subnets - Collection of Subnet ids in which to run the listener(s)


  :task - Map of properties for the task

        :container-definitions - A collection of container definition maps.

        :cpu - CPU units for the task

        :memory - Memory units for the task

        :volumes - Collection of ECS Volume configurations

        :iam-statements - A collection of AWS IAM policy statements (maps with :Resource, :Effect & :Action keys) to add to the task's role.

        :subnets - Collection of subnet IDs in which to run the service


Documentation for these values is available at (see https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html).

  By default, runs one instance of the service in each subnet.

  If multiple container definitions are provided, the load balancer will be configured to connect to the first one."
  [parent provider name {:keys [zone
                                subdomain
                                vpc-id
                                container-port
                                cluster-id
                                lb
                                task]}]
  (let [group (p/group name {:providers [provider] :parent parent})
        lb-sg (p/resource aws/ec2.SecurityGroup (str name "-lb") group
                {:vpcId vpc-id
                 :egress [{:protocol "-1"
                           :fromPort 0
                           :toPort 0
                           :cidrBlocks ["0.0.0.0/0"]}]
                 :ingress [{:protocol "tcp"
                            :fromPort 80
                            :toPort 80
                            :cidrBlocks (:ingress-cidrs lb)}]})
        log-bucket-name (str "access-logs." (if (empty? subdomain)
                                              zone
                                              (str subdomain "." zone)))
        log-bucket (p/resource aws/s3.Bucket (str name "-access-logs") group
                     {:bucket log-bucket-name
                      :policy (access-log-bucket-policy provider log-bucket-name)})
        alb (p/resource aws/lb.LoadBalancer name group
              {:loadBalancerType "application"
               :securityGroups [(:id lb-sg)]
               :subnets (:subnets lb)
               :accessLogs {:bucket (:bucket log-bucket)
                            :enabled true}})
        target-group (p/resource aws/lb.TargetGroup name alb
                       {:port container-port
                        :healthCheckPort container-port
                        :protocol "HTTP"
                        :targetType "ip"
                        :vpcId vpc-id})
        listener (p/resource aws/lb.Listener name alb
                   {:loadBalancerArn (:arn alb)
                    :port 80
                    :defaultActions [{:type "forward"
                                      :targetGroupArn (:arn target-group)}]})
        log-group (p/resource aws/cloudwatch.LogGroup name group
                    {:name (str "/" name "/" (pulumi/getStack) "/ecs-logs")})
        role (aws-utils/role (str name "-task") group
               {:services ["ecs-tasks.amazonaws.com"]
                :policies ["arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"]
                :statements (:iam-statements task)})
        container-definitions (for [d (:container-definitions task)]
                                (assoc d :logConfiguration
                                  {:logDriver "awslogs"
                                   :options {"awslogs-group" (:name log-group)
                                             "awslogs-region" (:region provider)
                                             "awslogs-stream-prefix" (str (:name d) "-")}}))
        task-definition (p/resource aws/ecs.TaskDefinition name group
                          {:family (str name "-" (pulumi/getStack))
                           :executionRoleArn (:arn role)
                           :taskRoleArn (:arn role)
                           :networkMode "awsvpc"
                           :requiresCompatibilities ["FARGATE"]
                           :cpu (:cpu task)
                           :memory (:memory task)
                           :containerDefinitions (p/json container-definitions)
                           :volumes (:volumes task)})
        sg (p/resource aws/ec2.SecurityGroup (str name "-task") group
             {:vpcId vpc-id
              :ingress [{:protocol "tcp"
                         :fromPort container-port
                         :toPort container-port
                         :securityGroups [(:id lb-sg)]}]
              :egress [{:protocol "-1"
                        :fromPort 0
                        :toPort 0
                        :cidrBlocks ["0.0.0.0/0"]}]})
        service (p/resource aws/ecs.Service name group
                  {:cluster cluster-id
                   :launchType "FARGATE"
                   :taskDefinition (:arn task-definition)
                   :desiredCount (count (:subnets task))
                   :loadBalancers [{:targetGroupArn (:arn target-group)
                                    :containerName (:name (first container-definitions))
                                    :containerPort container-port}]
                   :deploymentMaximumPercent 200
                   :deploymentMinimumHealthyPercent 50
                   :networkConfiguration {:subnets (:subnets task)
                                          :securityGroups [(:id sg)]}})]
    {:dns (:dnsName alb)
     :security-group sg}))
