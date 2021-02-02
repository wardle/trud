(ns com.eldrix.trud.core-test
  (:require [clojure.test :refer :all]
            [com.eldrix.trud.release :as release])
  (:import [java.time LocalDate]))


(deftest get-releases
  (let [api-key (slurp "api-key.txt")
        r58 (release/get-latest api-key 58)
        r341 (release/get-latest api-key 341)]
    (is r58)
    (is r341)))


(comment
  (release/get-latest (slurp "api-key.txt") 58)
  (run-tests))