(ns pulumi-cljs.subnet-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [pulumi-cljs.subnet :as subnet]))

;; Example based tests built using https://www.subnetcalculator.org

(deftest allocate-test
  (is (= ["10.0.0.0/16"] (subnet/allocate "10.0.0.0/16" [16])))
  (is (= ["10.0.0.0/17"
          "10.0.128.0/17"] (subnet/allocate "10.0.0.0/16" [17 17])))
  (is (thrown-with-msg? ExceptionInfo #"Could not allocate"
        (subnet/allocate "10.0.0.0/16" [17 17 17])))
  (is (= ["10.0.0.0/20"
          "10.0.16.0/20"
          "10.0.32.0/19"] (subnet/allocate "10.0.0.0/16" [20 20 19])))
  (is (= ["10.0.0.0/20"
          "10.0.16.0/20"
          "10.0.64.0/18"] (subnet/allocate "10.0.0.0/16" [20 20 18])))
  (is (= ["10.0.0.0/20"
          "10.0.16.0/20"
          "10.0.64.0/18"
          "10.0.32.0/20"
          "10.0.128.0/17"
          "10.0.48.0/20"]
        (subnet/allocate "10.0.0.0/16" [20 20 18 20 17 20])))
  (is (thrown-with-msg? ExceptionInfo #"Could not allocate"
        (subnet/allocate "10.0.0.0/16" [20 20 18 20 17 20 20])))
)
