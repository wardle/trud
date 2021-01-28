(ns com.eldrix.trud.core
  (:require [clojure.core.async :as a]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.logging.readable :as log]
            [clj-http.client :as client])
  (:import [java.time LocalDate Instant]
           [java.time.format DateTimeFormatter DateTimeParseException]
           (java.nio.file.attribute FileAttribute)
           (java.nio.file Files Path)
           (java.util.zip ZipInputStream ZipFile)
           (java.io File)))

(def expected-api-version 1)

;; release date format = CCYY-MM-DD
(defn- ^LocalDate parse-date [^String s] (try (LocalDate/parse s (DateTimeFormatter/ISO_LOCAL_DATE)) (catch DateTimeParseException _)))
(defn- ^Instant parse-instant [^String s] (try (Instant/parse s) (catch DateTimeParseException _)))

(def metadata-parsers
  {:checksumFileLastModifiedTimestamp  parse-instant
   :releaseDate                        parse-date
   :signatureFileLastModifiedTimestamp parse-instant
   :archiveFileLastModifiedTimestamp   parse-instant})

(defn parse-metadata [m]
  (reduce-kv (fn [x k v]
               (assoc x k (if-let [parser (get metadata-parsers k)]
                            (parser v) v))) {} m))

(defn- download-url
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
            (when-not (.isDirectory parent) (.mkdirs parent))
            (io/copy input (.toFile (.resolve out (.getName entry))))))
        (recur (.getNextEntry input))))))

(defn file-from-zip
  "Reads a single file from a zip file, returning an InputStream."
  ([^File zipfile]
   (with-open [zipfile (new ZipFile zipfile)]
     (let [entries (iterator-seq (.entries zipfile))
           entry (first entries)]
       (.getInputStream zipfile entry))))
  ([^File zipfile filename]
   (with-open [zipfile (new ZipFile zipfile)]
     (when-let [entry (.getEntry zipfile filename)]
       (.getInputStream zipfile entry)))))

(defn make-release-information-url [api-key release-identifier only-latest?]
  (str "https://isd.digital.nhs.uk/trud3/api/v1/keys/"
       api-key
       "/items/"
       release-identifier
       "/releases"
       (when only-latest? "?latest")))

(defn get-release-information
  "Returns a sequence of structured metadata about each release of the
  distribution files. Data are returned as-is from the source API, except that
  dates are parsed and  the release-identifier is included using the key
  'releaseIdentifier'.
  Parameters:
   - api-key            : your API key from TRUD
   - release-identifier : an identifier for the release

   For example, 341 is the NHS ODS XML distribution.
   By default, only information about the latest release is included."
  ([api-key release-identifier] (get-release-information api-key release-identifier true))
  ([api-key release-identifier only-latest?]
   (let [url (make-release-information-url api-key release-identifier only-latest?)
         response (client/get url {:as :json})]
     (->> (get-in response [:body :releases])
          (map parse-metadata)
          (map #(assoc % :releaseIdentifier release-identifier))))))

(s/def ::release-identifier int?)
(s/def ::release-date #(instance? LocalDate %))
(s/def ::subscription (s/keys :req-un [::release-identifier] :opt-un [::release-date]))
(s/def ::subscriptions (s/coll-of ::subscription))

(defn- merge-release-information
  "Supplement information about a subscription with data from the TRUD API.
  Each subscription is supplemented by the following keys:
   - :latest-release : metadata about the latest release
   - :needs-update?  : a boolean indicating whether an update is necessary."
  [api-key subscription]
  (when-not (s/valid? ::subscription subscription)
    (throw (ex-info "Invalid subscription" (s/explain-data ::subscription subscription))))
  (let [latest-release (first (get-release-information api-key (:release-identifier subscription)))
        installed-date (:release-date subscription)
        latest-date (:releaseDate latest-release)
        needs-update? (cond
                        (and (nil? installed-date) (nil? latest-date))
                        true

                        (and (nil? installed-date) latest-date)
                        true

                        (and installed-date (nil? latest-date))
                        (throw (ex-info "Release no longer available" {:subscription subscription :latest-release latest-release}))

                        (.isBefore installed-date latest-date)
                        true

                        :else false)]
    (assoc subscription :latest-release latest-release :needs-update? needs-update?)))

(defn get-all-release-information
  "Returns release information for all of the subscriptions.
  Each subscription is supplemented by the following keys:
   - :latest-release : metadata about the latest release
   - :needs-update?  : a boolean indicating whether an update is necessary."
  [api-key subscriptions]
  (when-not (s/valid? ::subscriptions subscriptions)
    (throw (ex-info "Invalid subscriptions" (s/explain-data ::subscriptions subscriptions))))
  (doall (map (partial merge-release-information api-key) subscriptions)))

(defn summarise-release
  "Returns a simple summary of a release."
  [m]
  {:release-identifier (:release-identifier m)
   :existing           (:release-date m)
   :needs-update?      (:needs-update? m)
   :latest-release     (get-in m [:latest-release :releaseDate])})

(defn- download-latest-release
  "Downloads the latest release zip file and returns as a java.nio.file.Path."
  [m]
  (let [url (get-in m [:latest-release :archiveFileUrl])
        temp-file (Files/createTempFile "trud" ".zip" (make-array FileAttribute 0))]
    (download-url url (.toFile temp-file))
    temp-file))


(defn download-updated-releases
  "Downloads the latest reference data releases with the identifiers specified.
  The downloads will be returned on a clojure.async channel.
  Parameters:
   - api-key       : TRUD API key
   - subscriptions : Release subscriptions.
  Result:
   - release information, including the key :download-path with a
     java.nio.file.Path representing the path to the unzipped distribution
     files.

  Many distributions contain nested zip files which will not be unzipped
  recursively; their processing is the responsibility of the caller."
  [api-key subscriptions]
  (when-not (s/valid? ::subscriptions subscriptions)
    (throw (ex-info "Invalid subscriptions" (s/explain-data ::subscriptions subscriptions))))
  (let [releases (get-all-release-information api-key subscriptions)
        outdated (filter :needs-update? releases)
        ch (a/chan 1)]
    (log/debug "Current subscriptions" (map summarise-release releases))
    (a/thread
      (doseq [release outdated]
        (log/info "Downloading release " (summarise-release release))
        (let [path (download-latest-release release)
              out-dir (Files/createTempDirectory "trud" (make-array FileAttribute 0))]
          (log/debug "Unarchiving release files for" (:release-identifier release) "to" (.getCanonicalPath path))
          (unzip (.toFile path) out-dir)
          (a/>!! ch (assoc release :download-path out-dir)))
        (a/close! ch)))
    ch))

(defn download-releases
  "Downloads the latest reference data releases with the identifiers specified.
  The downloads will be returned on a clojure.async channel as per
  [[download-updated-releases]].
  Parameters:
   - `api-key`             : TRUD API key
   - `release-identifiers` : sequence of release identifiers.."
  [api-key release-identifiers]
  (download-updated-releases api-key (map #(hash-map :release-identifier %) release-identifiers)))

(comment
  ;;;; put in your API key here....
  (def api-key "xxxxxx")
  (def data (get-release-information api-key 341))
  (first data)
  (def archive-zip-file "/tmp/trud16840689948905597534/archive.zip")
  (file-from-zip (File. archive-zip-file) "HSCOrgRefData_Archive_20201214.xml" nil)
  (summarise-release (first data))
  (def subscriptions [{:release-identifier 58 :release-date (LocalDate/now)}
                      {:release-identifier 341 :release-date (LocalDate/of 2020 11 19)}])
  (def subscriptions [{:release-identifier 246}])
  (def ch (download-updated-releases api-key subscriptions))
  (a/<!! ch)
  )