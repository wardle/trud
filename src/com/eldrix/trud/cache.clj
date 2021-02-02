(ns com.eldrix.trud.cache
  "Provides a simple caching mechanism for TRUD files."
  (:require [clojure.java.io :as io]
            [clj-http.client :as client]
            [clojure.tools.logging :as log])
  (:import (java.nio.file Paths Path Files LinkOption)
           (java.time LocalDate)
           (java.time.format DateTimeFormatter)
           (java.nio.file.attribute FileAttribute)))

(defn- download-url
  "Download a file from the URL to the target path
   Parameters:
    - url    : a string representation of a URL.
    - target : path to target."
  ([^String url ^Path target]
   (let [request (client/get url {:as :stream})
         buffer-size (* 1024 10)]
     (with-open [input (:body request)
                 output (io/output-stream (.toFile target))]
       (let [buffer (make-array Byte/TYPE buffer-size)]
         (loop []
           (let [size (.read input buffer)]
             (when (pos? size)
               (.write output buffer 0 size)
               (recur)))))))))

(defn- cache-path
  "Return the path to be used for the archive."
  [dir {:keys [releaseIdentifier releaseDate archiveFileName]}]
  (let [dir-path ^Path (if (instance? Path dir) dir (Paths/get dir (make-array String 0)))
        filename (str releaseIdentifier
                      "--"
                      (.format releaseDate DateTimeFormatter/ISO_LOCAL_DATE)
                      "--"
                      archiveFileName)]
    (.resolve dir-path filename)))

(defn- validate-file
  "Validates a downloaded release file according to release metadata,
  returning the path if it is valid."
  [^Path path {:keys [archiveFileSizeBytes checksumFileUrl] :as release}]
  (let [exists? (Files/exists path (make-array LinkOption 0))]
    (when exists?
      (let [size (Files/size path)
            right-size? (= archiveFileSizeBytes size)
            checksum? (when right-size? true)]              ;; TODO: implement checksum check?
        (cond
          (and exists? right-size? checksum?)
          path
          (not right-size?)
          (log/warn "incorrect cached file size." {:expected archiveFileSizeBytes :got size :release release})
          (not checksum?)
          (log/warn "incorrect checksum for cached file" {:release release}))))))

(defn- archive-file-from-cache
  "Return an archive file from the cache, if it exists."
  [dir {:keys [releaseIdentifier releaseDate archiveFileUrl archiveFileName archiveFileSizeBytes] :as release}]
  (let [cp (cache-path dir release)]
    (validate-file cp release)))

(defn- download-archive-file-to-cache
  "Download an archive file to the cache."
  [base {:keys [releaseIdentifier releaseDate archiveFileUrl archiveFileName] :as release}]
  (let [cp (cache-path base release)]
    (Files/createDirectories (.getParent cp) (make-array FileAttribute 0))
    (download-url archiveFileUrl cp)
    (validate-file cp release)))

(defn get-archive-file
  "Get an archive file either from the cache or downloaded from TRUD."
  [base release]
  (if-let [p (archive-file-from-cache base release)]
    p
    (do
      (log/debug "downloading release" (select-keys release [:releaseIdentifier]))
      (download-archive-file-to-cache base release)
      (archive-file-from-cache base release))))

(comment
  (get-archive-file "/tmp/trud" {:releaseIdentifier    341
                                 :releaseDate          (LocalDate/of 2021 01 29)
                                 :archiveFileUrl       "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
                                 :archiveFileSizeBytes 13264
                                 :archiveFileName      "dummy.pdf"})
  )