(ns com.eldrix.trud.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [com.eldrix.trud.release :as release]))


(deftest get-releases
  (if-let [api-key (str/trim-newline  (slurp "api-key.txt"))]
    (let [r58 (release/get-latest api-key 58)
          r341 (release/get-latest api-key 341)]
      (is r58)
      (is r341))
    (println "Missing api-key : skipping live tests")))


(comment
  (release/get-latest (slurp "api-key.txt") 58)
  (run-tests))