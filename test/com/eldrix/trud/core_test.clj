(ns com.eldrix.trud.core-test
  (:require [clojure.test :refer :all]
            [com.eldrix.trud.core :as trud])
  (:import [java.time LocalDate]))


(deftest outdated
  (let [api-key (slurp "api-key.txt")
        subs [{:release-identifier 58 :existing-date (LocalDate/now)}
              {:release-identifier 341 :existing-date (LocalDate/of 2020 11 19)}]
        results (#'trud/get-subscriptions api-key subs)
        r58 (first results)
        r341 (second results)]
    (is (not (:needs-update? r58)))
    (is (:needs-update? r341))))


(comment
  (run-tests))