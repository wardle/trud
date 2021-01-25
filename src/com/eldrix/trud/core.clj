(ns com.eldrix.trud.core
  (:require [clj-http.client :as client]
            [clojure.java.io :as io])
  (:import [java.time LocalDate Instant]
           [java.time.format DateTimeFormatter DateTimeParseException]))

(def expected-api-version 1)

;; release date format = CCYY-MM-DD
(defn- ^LocalDate parse-date [^String s] (try (LocalDate/parse s (DateTimeFormatter/ISO_LOCAL_DATE)) (catch DateTimeParseException _)))
(defn- ^Instant parse-instant [^String s] (try (Instant/parse s) (catch DateTimeParseException _)))

(def metadata-parsers
  {:checksumFileLastModifiedTimestamp parse-instant
   :releaseDate                       parse-date
   :signatureFileLastModifiedTimestamp    parse-instant
   :archiveFileLastModifiedTimestamp  parse-instant})

(defn parse-metadata [m]
  (reduce-kv (fn [x k v]
               (assoc x k (if-let [parser (get metadata-parsers k)]
                            (parser v) v))) {} m))

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
  "Returns a sequence of structured metadata about each release of the distribution files.
  Data are returned as-is from the source API, except that dates are parsed.
  Parameters:
   - api-key            : your API key from TRUD
   - release-identifier : an identifier for the release, eg. 341 is the NHS ODS XML distribution."
  ([api-key release-identifier] (get-release-information api-key release-identifier false))
  ([api-key release-identifier only-latest?]
   (let [url (make-release-information-url api-key release-identifier only-latest?)
         response (client/get url {:as :json})]
     (->> (get-in response [:body :releases])
          (map parse-metadata)))))



(comment
  (def api-key "xx")
  (def data (get-release-information api-key 341 true))
  (first data)
  (client/get "http://example.com/foo.clj" {:as :clojure})
  )