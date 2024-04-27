(ns com.eldrix.trud.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [com.eldrix.trud.impl.cache :as cache]
            [com.eldrix.trud.impl.release :as release]
            [com.eldrix.trud.impl.zip :as zip])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(deftest get-releases
  (if-let [api-key (str/trim-newline  (slurp "api-key.txt"))]
    (let [r58 (release/get-latest api-key 58)
          r341 (release/get-latest api-key 341)]
      (is r58)
      (is r341))
    (println "Missing api-key : skipping live tests")))

(deftest cache
  (let [dir (Files/createTempDirectory "trud" (make-array FileAttribute 0))
        cache (cache/make-cache (.toFile dir))
        url "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
        job {:url url :filename "dummy.pdf"}
        r1 (cache job)
        r2 (cache job)]
    (is (not (:from-cache r1)))
    (is (:from-cache r2))
    (is (= (:f r1) (:f r2)))
    (zip/delete-paths [dir])))

(comment
  (def api-key (str/trim-newline (slurp "api-key.txt")))
  (release/get-latest api-key 101)
  (run-tests))
