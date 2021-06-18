(ns hello-world.main-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]))

(deftest math-works
  (is (= (+ 1 1) 2)))
