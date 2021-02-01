(ns com.eldrix.trud.core
  "Support for the NHS Digital TRUD (Technology Reference data Update
  Distribution)."
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.logging.readable :as log]
            [clj-http.client :as client])
  (:import [java.time LocalDate Instant]
           [java.time.format DateTimeFormatter DateTimeParseException]
           (java.nio.file.attribute FileAttribute)
           (java.nio.file Files Path)
           (java.util.zip ZipInputStream ZipFile)
           (java.io File)))

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

(defn- download-url
  "Download a file from the URL to the target file, or to a temporary file.
   Parameters:
    - url : a string representation of a URL.
    - target : anything that can be coerced into an output-stream.
    See clojure.java.io/output-stream."
  ([^String url]
   (let [temp-file (Files/createTempFile "trud" ".zip" (make-array FileAttribute 0))]
     (download-url url (.toFile temp-file))))
  ([^String url target]
   (let [request (client/get url {:as :stream})
         buffer-size (* 1024 10)]
     (with-open [input (:body request)
                 output (io/output-stream target)]
       (let [buffer (make-array Byte/TYPE buffer-size)]
         (loop []
           (let [size (.read input buffer)]
             (if-not (pos? size)
               target
               (do
                 (.write output buffer 0 size)
                 (recur))))))))))

(defn unzip
  "Unzip a zip archive to the directory specified.
  Parameters:
  - in  : anything coercible to an input stream (e.g. File, URL or InputStream).
  - out : path of the directory to which files will be extracted.

  The out directory will be created if it doesn't exist."
  [in ^Path out]
  (Files/createDirectories out (make-array FileAttribute 0)) ;; this will be a NOP if directory already exists
  (with-open [input (ZipInputStream. (io/input-stream in))]
    (loop [entry (.getNextEntry input)]
      (when entry
        (if (.isDirectory entry)
          (Files/createDirectories (.resolve out (.getName entry)) (make-array FileAttribute 0))
          (let [new-file (.toFile (.resolve out (.getName entry)))
                parent (.getParentFile new-file)]
            (when-not (.isDirectory parent) (.mkdirs parent)) ;; if parent directory doesn't exist, create
            (io/copy input (.toFile (.resolve out (.getName entry))))))
        (recur (.getNextEntry input))))))

(defn file-from-zip
  "Reads a single file from a zip file, returning an InputStream to fn f."
  ([^File zipfile f]
   (with-open [zipfile (new ZipFile zipfile)]
     (let [entries (iterator-seq (.entries zipfile))
           entry (first entries)]
       (f (.getInputStream zipfile entry)))))
  ([^File zipfile f filename]
   (with-open [zipfile (new ZipFile zipfile)]
     (when-let [entry (.getEntry zipfile filename)]
       (f (.getInputStream zipfile entry))))))

(defn- make-release-information-url
  "Generate the TRUD API endpoint URL to obtain release information."
  [api-key release-identifier only-latest?]
  (str "https://isd.digital.nhs.uk/trud3/api/v1/keys/"
       api-key
       "/items/"
       release-identifier
       "/releases"
       (when only-latest? "?latest")))

(defn- get-releases-by-identifier
  "Returns a sequence of structured metadata about each release of the
  distribution files. Data are returned as-is from the source API, except that
  dates are parsed and  the release-identifier is included using the key
  'releaseIdentifier'.
  Parameters:
   - api-key            : your API key from TRUD
   - release-identifier : an identifier for the release

   For example, 341 is the NHS ODS XML distribution.
   By default, only information about the latest release is included."
  ([api-key release-identifier] (get-releases-by-identifier api-key release-identifier true))
  ([api-key release-identifier only-latest?]
   (let [url (make-release-information-url api-key release-identifier only-latest?)
         response (client/get url {:as :json})
         api-version (get-in response [:body :apiVersion])]
     (when-not (= api-version expected-api-version)
       (log/warn "Unexpected TRUD API version. expected:" expected-api-version "got:" api-version))
     (->> (get-in response [:body :releases])
          (map parse-release)
          (map #(assoc % :releaseIdentifier release-identifier))))))

(s/def ::release-identifier int?)
(s/def ::existing-date #(instance? LocalDate %))
(s/def ::subscription (s/keys :req-un [::release-identifier] :opt-un [::existing-date]))
(s/def ::subscriptions (s/coll-of ::subscription))

(defn- get-subscription
  "Augment information about a subscription with data from the TRUD API.
  A subscription is supplemented by the following keys:
   - :latest-release : metadata about the latest release
   - :needs-update?  : a boolean indicating whether an update is necessary."
  [api-key subscription]
  (when-not (s/valid? ::subscription subscription)
    (throw (ex-info "Invalid subscription" (s/explain-data ::subscription subscription))))
  (let [latest-release (first (get-releases-by-identifier api-key (:release-identifier subscription)))
        existing-date (:existing-date subscription)
        latest-date (:releaseDate latest-release)
        needs-update? (cond
                        (and (nil? existing-date) (nil? latest-date))
                        true

                        (and (nil? existing-date) latest-date)
                        true

                        (and existing-date (nil? latest-date))
                        (throw (ex-info "Release no longer available" {:subscription subscription :latest-release latest-release}))

                        (.isBefore existing-date latest-date)
                        true

                        :else false)]
    (assoc subscription :latest-release latest-release
                        :latest-date latest-date
                        :needs-update? needs-update?)))

(defn- get-subscriptions
  "Returns release information for all of the subscriptions.
  Each subscription is supplemented by the following keys:
   - :latest-release : metadata about the latest release
   - :needs-update?  : a boolean indicating whether an update is necessary."
  [api-key subscriptions]
  (when-not (s/valid? ::subscriptions subscriptions)
    (throw (ex-info "Invalid subscriptions" (s/explain-data ::subscriptions subscriptions))))
  (doall (map (partial get-subscription api-key) subscriptions)))

(defn- do-download-release
  [release]
  (if-not (:needs-update? release)
    release
    (let [f (download-url (get-in release [:latest-release :archiveFileUrl]))
          out-dir (Files/createTempDirectory "trud" (make-array FileAttribute 0))]
      (unzip f out-dir)
      (assoc release :download-path out-dir))))


(defn download-subscriptions
  "Downloads the latest reference data releases with the identifiers specified.
  Parameters:
   - api-key       : TRUD API key
   - subscriptions : Release subscriptions.

  A subscription will be downloaded if the existing-date is before the latest
  version of that distribution, or if no existing-date is provided.

  This is a blocking operation; use in a thread if required.
  Result:
   - a sequence of subscriptions, augmented with the following keys:
     - :latest-release  : metadata on the latest release
     - :needs-update?   : if you have an outdated release
     - :downloaded-path : the unzipped release will be found here, if needed

   A subscription is a map containing :
   - :release-identifier : identifier of the release, eg. 56
   - :existing-date      : date of currently installed version (optional)

  Many distributions contain nested zip files which will not be unzipped
  recursively; their processing is the responsibility of the caller."
  [api-key subscriptions]
  (when-not (s/valid? ::subscriptions subscriptions)
    (throw (ex-info "Invalid subscriptions" (s/explain-data ::subscriptions subscriptions))))
  (let [releases (get-subscriptions api-key subscriptions)
        outdated (filter :needs-update? releases)
        total-bytes (reduce + (map #(get-in % [:latest-release :archiveFileSizeBytes]) outdated))]
    (if (seq outdated)
      (do
        (log/info (str (count outdated) "/" (count releases) " subscriptions to be downloaded (" total-bytes " bytes)."))
        (map do-download-release releases))
      (log/info "All subscriptions up-to-date." (map :release-identifier subscriptions)))))

(comment
  (def api-key (slurp "api-key.txt"))
  (def subscriptions
    [{:release-identifier 246}
     {:release-identifier 341 :existing-date (LocalDate/now)}])
  (get-subscriptions api-key subscriptions)

  (def updated (download-subscriptions api-key subscriptions))
  updated
  (get-subscription api-key {:release-identifier 246})
  )
