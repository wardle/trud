(ns com.eldrix.trud.core
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]))

(def expected-api-version 1)

(defn- download
  "Download a file from the URL to the target file.
   Parameters:
    - url : a string representation of a URL.
    - target : anything that can be coerced into an output-stream.
    See clojure.java.io/output-stream."
  [^String url target]
  (let [request (client/get url {:as :stream})
        buffer-size (* 1024 10)]
    (with-open [input (:body request)
                output (io/output-stream target)]
      (let [buffer (make-array Byte/TYPE buffer-size)]
        (loop []
          (let [size (.read input buffer)]
            (when (pos? size)
              (.write output buffer 0 size)
              (recur))))))))

(defn make-release-information-url [api-key release-identifier only-latest?]
  (str "https://isd.digital.nhs.uk/trud3/api/v1/keys/"
       api-key
       "/items/"
       release-identifier
       "/releases"
       (when only-latest? "?latest")))

(defn get-release-information
  ([api-key release-identifier] (get-release-information api-key release-identifier false))
  ([api-key release-identifier only-latest?]
   (let [url (make-release-information-url api-key release-identifier only-latest?)
         response (client/get url {:as :json})]
     (get-in response [:body :releases]))))

(comment
  (def api-key "xxx")
  (get-release-information api-key 341 true)
  (client/get "http://example.com/foo.clj" {:as :clojure})
  )