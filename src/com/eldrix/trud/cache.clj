(ns com.eldrix.trud.cache
  "Provides a simple caching mechanism for TRUD files."
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.logging.readable :as log]
            [com.eldrix.trud.check :as check]
            [org.httpkit.client :as http])
  (:import (java.nio.file Paths Path Files LinkOption)
           (java.time LocalDate)
           (java.time.format DateTimeFormatter)
           (java.nio.file.attribute FileAttribute)))

(defn download-url
  "Download a file from the URL to the target path.
   Parameters:
    - url     : a string representation of a URL.
    - target  : path to target."
  [^String url ^Path target]
  @(http/get url {:as :stream}                              ;; body will be a java.io.InputStream
             (fn [{:keys [status body error]}]
               (if (or (not= 200 status) error)
                 (throw (ex-info "Unable to download" {:url url :status status :error error :body body}))
                 (io/copy body (.toFile target))))))

(defn- cache-path
  "Return the path to be used for the archive."
  [dir {:keys [itemIdentifier releaseDate archiveFileName]}]
  (let [dir-path ^Path (if (instance? Path dir) dir (Paths/get dir (make-array String 0)))
        filename (str itemIdentifier
                      "--"
                      (.format releaseDate DateTimeFormatter/ISO_LOCAL_DATE)
                      "--"
                      archiveFileName)]
    (.resolve dir-path filename)))

(defn- validate-file
  "Validates a downloaded release file according to release metadata,
  returning the path if it is valid."
  [^Path path {:keys [itemIdentifier archiveFileSizeBytes] :as release}]
  (let [exists? (Files/exists path (make-array LinkOption 0))]
    (when exists?
      (let [size (Files/size path)
            right-size? (= archiveFileSizeBytes size)
            checksum? (when right-size? (check/valid-checksum? release (.toFile path)))]
        (cond
          (and exists? right-size? checksum?)
          path
          (not right-size?)
          (log/info "Unable to use archive: incorrect file size:" {:itemIdentifier itemIdentifier :expected archiveFileSizeBytes :got size})
          (not checksum?)
          (log/info "Incorrect checksum for archive file"))))))

(defn- archive-file-from-cache
  "Return an archive file from the cache, if it exists."
  [dir {:keys [_itemIdentifier _releaseDate _archiveFileUrl _archiveFileName _archiveFileSizeBytes] :as release}]
  (let [cp (cache-path dir release)]
    (validate-file cp release)))

(defn- download-archive-file-to-cache
  "Download an archive file to the cache."
  [dir {:keys [_itemIdentifier _releaseDate archiveFileUrl _archiveFileName archiveFileSizeBytes] :as release}]
  (let [cp (cache-path dir release)]
    (Files/createDirectories (.getParent cp) (make-array FileAttribute 0))
    (try
      (download-url archiveFileUrl cp)
      (validate-file cp release)
      (catch Exception e (println "Failed to download item: " e)))))

(s/def ::itemIdentifier int?)
(s/def ::releaseDate (partial instance? LocalDate))
(s/def ::archiveFileUrl string?)
(s/def ::archiveFileSizeBytes int?)
(s/def ::archiveFileName string?)

(s/def ::release (s/keys :req-un [::itemIdentifier
                                  ::releaseDate
                                  ::archiveFileUrl
                                  ::archiveFileSizeBytes
                                  ::archiveFileName]))

(defn get-archive-file
  "Get an archive file either from the cache or downloaded from TRUD.
  Returns result as a `java.nio.file.Path`."
  [dir release]
  (when-not (s/valid? ::release release)
    (throw (ex-info "invalid release" (s/explain-data ::release release))))
  (if-let [p (archive-file-from-cache dir release)]
    (do
      (log/info "Item already in cache" (select-keys release [:itemIdentifier :archiveFileName :releaseDate]))
      p)
    (do
      (log/info "Downloading item" (select-keys release [:itemIdentifier :archiveFileName :releaseDate]))
      (download-archive-file-to-cache dir release)
      (archive-file-from-cache dir release))))

(comment
  (get-archive-file "/tmp/trud" {:itemIdentifier       341
                                 :releaseDate          (LocalDate/of 2021 01 29)
                                 :archiveFileUrl       "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
                                 :archiveFileSizeBytes 13264
                                 :archiveFileName      "dummy.pdf"}))



