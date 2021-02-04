(ns com.eldrix.trud.core
  (:require [com.eldrix.trud.cache :as cache]
            [com.eldrix.trud.release :as release]))


(defn get-latest
  "Returns the latest release of the specified item, if existing is outdated.
  Currently only uses release date and does not use archive timestamp."
  ([api-key cache-dir item-identifier] (get-latest api-key cache-dir item-identifier nil))
  ([api-key cache-dir item-identifier existing-date]
   (when-let [latest (release/get-latest api-key item-identifier)]
     (if-not (or (nil? existing-date) (.isBefore existing-date (:releaseDate latest)))
       latest
       (assoc latest :needsUpdate? true
                     :archiveFilePath (cache/get-archive-file cache-dir latest))))))

(comment
  (def api-key (slurp "api-key.txt"))
  api-key
  (def latest (get-latest api-key "/tmp/trud" 341))
  latest
  (def ods-xml-files [(:archiveFilePath latest)
                      ["archive.zip" #"\w+.xml"]
                      ["fullfile.zip" #"\w+.xml"]])
  ods-xml-files
  (def results (com.eldrix.trud.zip/unzip2 ods-xml-files))
  (get-in results [1 1])    ;; sequence of any XML files in archive zip
  (get-in results [2 1])    ;; sequence of any XML files in fullfile.zip
  (com.eldrix.trud.zip/delete-paths results)

  )