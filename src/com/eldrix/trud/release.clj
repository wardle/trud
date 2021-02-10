(ns com.eldrix.trud.release
  "Support for the UK NHS Digital's TRUD.
  TRUD is the Technology Reference data Update Distribution."
  (:require [clojure.tools.logging.readable :as log]
            [clj-http.client :as client])
  (:import [java.time LocalDate Instant]
           [java.time.format DateTimeFormatter DateTimeParseException]))

(def ^:private expected-api-version "1")

;; release date format = CCYY-MM-DD
(defn- ^LocalDate parse-date [^String s] (try (LocalDate/parse s (DateTimeFormatter/ISO_LOCAL_DATE)) (catch DateTimeParseException _)))
(defn- ^Instant parse-instant [^String s] (try (Instant/parse s) (catch DateTimeParseException _)))

(def ^:private release-parsers
  {:checksumFileLastModifiedTimestamp  parse-instant
   :releaseDate                        parse-date
   :signatureFileLastModifiedTimestamp parse-instant
   :archiveFileLastModifiedTimestamp   parse-instant})

(defn- parse-release [m]
  (reduce-kv (fn [x k v]
               (assoc x k (if-let [parser (get release-parsers k)]
                            (parser v) v))) {} m))

(defn- make-item-releases-url
  "Generate the TRUD API endpoint URL to obtain release data about an item."
  [api-key item-identifier only-latest?]
  (when-not api-key
    (throw (IllegalArgumentException. "missing api key")))
  (when-not item-identifier
    (throw (IllegalArgumentException. "missing item identifier")))
  (str "https://isd.digital.nhs.uk/trud3/api/v1/keys/"
       api-key
       "/items/"
       item-identifier
       "/releases"
       (when only-latest? "?latest")))

(defn get-releases
  "Returns a sequence of structured metadata about each release of the
  distribution files. Data are returned as-is from the source API, except that
  dates are parsed and the item-identifier is included using the key
  'itemIdentifier'.
  Parameters:
   - api-key         : your API key from TRUD
   - item-identifier : item identifier

   For example, 341 is the NHS ODS XML distribution."
  ([api-key item-identifier] (get-releases api-key item-identifier {}))
  ([api-key item-identifier {:keys [only-latest?]}]
   (let [url (make-item-releases-url api-key item-identifier only-latest?)
         _ (println "Attempting to connect to " url)
         response (client/get url {:as :json})
         api-version (get-in response [:body :apiVersion])]
     (when-not (= api-version expected-api-version)
       (log/warn "Unexpected TRUD API version. expected:" expected-api-version "got:" api-version))
     (->> (get-in response [:body :releases])
          (map parse-release)
          (map #(assoc % :itemIdentifier item-identifier))))))

(defn get-latest
  "Returns information about the latest release of the item specified."
  [api-key item-identifier]
  (first (get-releases api-key item-identifier {:only-latest true})))

(comment
  (def api-key (slurp "api-key.txt"))
  api-key
  (get-latest api-key 341)
  )
