(ns com.eldrix.trud.cache
  "Provides a simple caching mechanism for TRUD files."
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clj-http.client :as client]
            [com.eldrix.trud.check :as check]
            [progrock.core :as pr])
  (:import (java.nio.file Paths Path Files LinkOption)
           (java.time LocalDate)
           (java.time.format DateTimeFormatter)
           (java.nio.file.attribute FileAttribute)))


(defn- download-url
  "Download a file from the URL to the target path.
   Parameters:
    - url     : a string representation of a URL.
    - target  : path to target.
    - options : optional :
                - :show-progress?      : whether to print a progress bar
                - :expected-size-bytes : total bytes."
  ([^String url ^Path target] (download-url url target {}))
  ([^String url ^Path target {:keys [show-progress? expected-size-bytes]}]
   (let [request (client/get url {:as :stream})
         buffer-size (* 1024 10)]
     (with-open [input (:body request)
                 output (io/output-stream (.toFile target))]
       (let [buffer (make-array Byte/TYPE buffer-size)
             progress (pr/progress-bar expected-size-bytes)]
         (loop [count 0 total 0]
           (let [size (.read input buffer)]
             (if-not (pos? size)
               (when show-progress? (pr/print (pr/done (pr/tick progress total))))
               (do (.write output buffer 0 size)
                   (when (and show-progress? (or (= 0 (mod count 100)))) (pr/print (pr/tick progress total)))
                   (recur (inc count) (+ total size)))))))))))

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
  [^Path path {:keys [archiveFileSizeBytes] :as release}]
  (let [exists? (Files/exists path (make-array LinkOption 0))]
    (when exists?
      (let [size (Files/size path)
            right-size? (= archiveFileSizeBytes size)
            checksum? (when right-size? (check/valid-checksum? release (.toFile path)))]
        (cond
          (and exists? right-size? checksum?)
          path
          (not right-size?)
          (println "Incorrect cached file size:" {:expected archiveFileSizeBytes :got size})
          (not checksum?)
          (println "Incorrect checksum for cached file."))))))

(defn- archive-file-from-cache
  "Return an archive file from the cache, if it exists."
  [dir {:keys [itemIdentifier releaseDate archiveFileUrl archiveFileName archiveFileSizeBytes] :as release}]
  (let [cp (cache-path dir release)]
    (validate-file cp release)))

(defn- download-archive-file-to-cache
  "Download an archive file to the cache."
  ([dir {:keys [itemIdentifier releaseDate archiveFileUrl archiveFileName archiveFileSizeBytes] :as release}]
   (download-archive-file-to-cache dir release {}))
  ([dir {:keys [itemIdentifier releaseDate archiveFileUrl archiveFileName archiveFileSizeBytes] :as release} {:keys [show-progress?] :as opts}]
   (let [cp (cache-path dir release)]
     (Files/createDirectories (.getParent cp) (make-array FileAttribute 0))
     (try
       (download-url archiveFileUrl cp {:show-progress? show-progress? :expected-size-bytes archiveFileSizeBytes})
       (validate-file cp release)
       (catch Exception e (println "Failed to download item: " e))))))

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
  ([dir release] (get-archive-file dir release {}))
  ([dir release {:keys [show-progress?] :as opts}]
   (when-not (s/valid? ::release release)
     (throw (ex-info "invalid release" (s/explain-data ::release release))))
   (if-let [p (archive-file-from-cache dir release)]
     (do
       (println "Item already in cache" (select-keys release [:itemIdentifier :archiveFileName :releaseDate]))
       p)
     (do
       (println "Downloading item" (select-keys release [:itemIdentifier :archiveFileName :releaseDate]))
       (download-archive-file-to-cache dir release opts)
       (archive-file-from-cache dir release)))))

(comment
  (get-archive-file "/tmp/trud" {:itemIdentifier       341
                                 :releaseDate          (LocalDate/of 2021 01 29)
                                 :archiveFileUrl       "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
                                 :archiveFileSizeBytes 13264
                                 :archiveFileName      "dummy.pdf"})



  )