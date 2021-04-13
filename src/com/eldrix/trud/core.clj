(ns com.eldrix.trud.core
  (:require [com.eldrix.trud.cache :as cache]
            [com.eldrix.trud.release :as release]))

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
  - api-key : the TRUD api key
  - release : release information from TRUD, as returned by `get-releases`.
  Returns result as a `java.nio.file.Path`"
  [api-key release]
  (cache/get-archive-file api-key release))

(defn get-latest
  "Returns the latest release of the specified item, if existing is outdated.
  Currently only uses release date and does not use archive timestamp.

  Parameters:
  - config : a configuration map containing:
             - api-key        : your NHS Digital TRUD api key
             - cache-dir      : where to download and cache distributions
             - show-progress? : (optional) show progress bar for download.

  - item-identifier : the TRUD API item identifier you want.
  - existing-date   : (optional) existing date of this item you have

  Result is the data from the source [TRUD API](https://isd.digital.nhs.uk/trud3/user/guest/group/0/api)
  with except that dates are parsed into java LocalDates to simplify sorting and
  comparison and the following keys are added:

  - :needsUpdate?     : indicates if your current version (`existing-date`) is
                        outdated.
  - :archiveFilePath  : a `java.nio.files.Path` to the downloaded
                        distribution, if an update is required."
  ([config item-identifier] (get-latest config item-identifier nil))
  ([{:keys [api-key cache-dir show-progress?] :as config} item-identifier existing-date]
   (when-let [latest (release/get-latest api-key item-identifier)]
     (if-not (or (nil? existing-date) (.isBefore existing-date (:releaseDate latest)))
       latest
       (assoc latest :needsUpdate? true
                     :archiveFilePath (cache/get-archive-file cache-dir latest config))))))

(defn download
  [{:keys [api-key cache-dir items] :as opts}]
  (if-not (and api-key cache-dir items)
    (println "Incorrect parameters\nUsage: clj -X com.eldrix.trud.core/download :api-key '\"XXX\"' :cache-dir '\"/tmp/trud\"' :items '[341 101 105]'")
    (try
      (doseq [item items]
        (get-latest (assoc opts :show-progress? true) item))
      (catch Exception e (println "Failed to download release: " (.getMessage e))))))

(comment
  (def api-key (slurp "api-key.txt"))
  api-key
  (def latest (get-latest api-key "/tmp/trud" 341)))