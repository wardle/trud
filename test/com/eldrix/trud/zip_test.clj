(ns com.eldrix.trud.zip-test
  (:require [clojure.test :refer :all]
            [com.eldrix.trud.zip :as zip]
            [clojure.string :as str])
  (:import (java.nio.file Path)))

(def test-query
  ["test/resources/test.zip"
   ["Z1.ZIP" "z1f1"]
   ["z3.zip"]
   ["z3.zip" "z3f3"]
   ["z3.zip" #"z3/z3f\d"]])

(deftest simple
  (let [paths (zip/unzip2 test-query)]
    (is (= 10 (count (flatten paths))))
    (doseq [path paths]
      (is instance? Path))
    (let [z1f1 (get-in paths [1 1])]        ;; use coordinates to get what we need
      (is (str/ends-with? (.toString z1f1) "z1f1")))
    (let [z3-files (get-in paths [4 1])]
      (is (= 3 (count z3-files))))
    (zip/delete-paths paths)))


(comment
  (run-tests))