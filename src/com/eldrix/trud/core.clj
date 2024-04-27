(ns com.eldrix.trud.core
  (:require [com.eldrix.trud.impl.cache :as cache]
            [com.eldrix.trud.impl.release :as release]
            [com.eldrix.trud.impl.zip :as zip]
            [clojure.tools.logging.readable :as log]))

(defn get-releases
  "Returns a sequence of structured metadata about each release of the
  distribution files. Data are returned as-is from the source API, except that
  dates are parsed and the item-identifier is included using the key
  'itemIdentifier'."
  [api-key item-identifier]
  (release/get-releases api-key item-identifier))

(defn download-release
  "Get an archive file either from the cache or downloaded from TRUD.
  In most circumstances, using `get-latest` is more appropriate.
  Parameters:
  - cache-dir : cache directory
  - release   : release information from TRUD, as returned by `get-releases`.
  - config    : configuration, including:
                |- :progress  - boolean, to print download progress or not
  Returns result as a `java.nio.file.Path`"
  ([dir release] (download-release dir release nil))
  ([dir release config]
   (when-let [f (cache/get-release-file dir release config)]
     (.toPath f))))

(defn get-latest
  "Returns the latest release of the specified item, if existing is outdated.
  Currently only uses release date and does not use archive timestamp.

  Parameters:
  - config : a configuration map containing:
             - api-key        : your NHS Digital TRUD api key
             - cache-dir      : where to download and cache distributions
             - progress       : whether to print progress or not
  - item-identifier : the TRUD API item identifier you want.
  - existing-date   : (optional) existing date of this item you have (java.time.LocalDate)

  Result is the data from the source [TRUD API](https://isd.digital.nhs.uk/trud3/user/guest/group/0/api)
  except that dates are parsed into java LocalDates to simplify sorting and
  comparison and the following keys are added:

  - :needsUpdate?     : indicates if your current version (`existing-date`) is
                        outdated.
  - :archiveFile      : a `java.io.File` to the downloaded distribution, if an
                        update is required
  - :archiveFilePath  : a `java.nio.files.Path` to the downloaded
                        distribution, if an update is required."
  ([config item-identifier] (get-latest config item-identifier nil))
  ([{:keys [api-key cache-dir progress]} item-identifier existing-date]
   (when-let [latest (release/get-latest api-key item-identifier)]
     (if-not (or (nil? existing-date) (.isBefore ^java.time.LocalDate existing-date (:releaseDate latest)))
       latest
       (when-let [f (cache/get-release-file cache-dir latest {:progress progress})]
         (assoc latest :needsUpdate? true :archiveFile f :archiveFilePath (.toPath f)))))))

(defn download
  [{:keys [api-key cache-dir items] :as opts}]
  (if-not (and api-key cache-dir items)
    (println "Incorrect parameters\nUsage: clj -X:download :progress 'true' :api-key '\"XXX\"' :cache-dir '\"/tmp/trud\"' :items '[341 101 105]'")
    (doseq [item items]
      (log/info "Processing item" item)
      (let [release (get-latest opts item)]
        (log/info "Latest for item" item ":" (:id release) (select-keys release [:archiveFilePath :archiveFileSizeBytes]))))))

(def unzip
  "Unzip a zip archive to the directory specified.
  Parameters:
  - in  : path of zip file
  - out : path of the directory to which files will be extracted.

  If no `out` path is specified, a temporary directory will be created.
  The out directory will be created if it doesn't exist."
  zip/unzip)

(def unzip-query
  "Resolves a query representing files from a nested directory structure,
  including extracting nested zip files.

  A query is a potentially nested vector of strings or paths.
  [\"test.zip\"
   [\"nested1.zip\"]
   [\"nested2.zip\" \"file.txt\"]]

  This will extract the test.zip file, extract the files in both nested1.zip
  and nested2.zip and also returns a path for `file.txt` from the nested2.zip.

  Results will be java.nio.file.Path objects in the same shape as the query.
  For the example above, four paths will be returned."
  zip/unzip-query)

(def unzip-nested
  "Unzip a zip archive to the directory specified, unzipping nested zip files.
  Parameters:
  - in  : path of zip file
  - out : path of the directory to which files will be extracted.

  If no `out` path is specified, a temporary directory will be created.
  The out directory will be created if it doesn't exist."
  zip/unzip-nested)

(comment
  (require '[clojure.string :as str])
  (def api-key (str/trim-newline (slurp "api-key.txt")))
  api-key
  (def latest (release/get-latest api-key 341))
  (cache/get-release-file "cache" (release/get-latest api-key 341))
  (def latest (get-latest {:api-key api-key :cache-dir "/tmp/trud"} 341)))
