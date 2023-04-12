(ns com.eldrix.trud.impl.cache
  "Provides a simple caching mechanism for TRUD files.

  This is deliberately not a generic download manager. It provides only
  a lightweight and synchronous cache for downloads."
  (:require [clojure.core.async :as a]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.logging.readable :as log]
            [com.eldrix.trud.impl.check :as check]
            [hato.client :as hc]
            [progrock.core :as pr])
  (:import (java.io File)
           (java.nio.file Path)
           (java.time LocalDate)
           (java.time.format DateTimeFormatter)))

(set! *warn-on-reflection* true)

;;
;; A download 'job' can be submitted and will either be returned from a
;; directory-based cache, or downloaded. An optional validate fn can be provided
;; with the 'job' to validate the download (e.g. by size or checksum)
;;
(s/def ::url string?)
(s/def ::filename string?)
(s/def ::file-size pos-int?)
(s/def ::file #(instance? File %))
(s/def ::validate fn?)
(s/def ::job (s/keys :req-un [::url ::filename]
                     :opt-un [::file-size ::validate]))

(defn download-url
  "Download a file from the URL to the target path.
   Parameters:
    - url     : a string representation of a URL.
    - target  : target (outputstream, writer or file)."
  [^String url target]
  (let [{:keys [status body error]}                         ;; body will be a java.io.InputStream
        (hc/get url {:as :stream :http-client {:redirect-policy :normal}})]
    (if (or (not= 200 status) error)
      (throw (ex-info "Unable to download" {:url url :status status :error error :body body}))
      (io/copy body (if (instance? Path target) (.toFile ^Path target) (io/file target))))))

(defn ^:private print-progress
  [ch]
  (loop [bar (a/<!! ch)]
    (when bar
      (pr/print bar)
      (recur (a/<!! ch)))))

(defn ^:private report-progress
  "Reports progress of a file to a channel.
  This continues until either the file size has reached the total-file-size, or
  the output channel is closed."
  [^File f total-file-size ch]
  (loop [bar (pr/progress-bar total-file-size)]
    (if (= (:progress bar) (:total bar))                    ;; are we done yet?
      (when (a/>!! ch (pr/done bar)) (a/close! ch))         ;; send finished and close channel if not already closed
      (do (Thread/sleep 500)
          (when (a/>!! ch bar)                              ;; send progress, and if channel not closed, loop
            (recur (assoc bar :progress (.length f))))))))

(defn make-cache
  "Create a simple cache in the directory specified.
  Returns a function that can be used to download from a URL to a filename, that
  will use the directory as a cache. An optional `validate` function can be
  provided to check file size or checksum, for example. If `progress` is
  truthy, then progress will be printed.
  The resulting function will take a job (a map of :url :filename :file-size and
  :validate) and return a map of :cached and :f."
  ([dir] (make-cache dir {}))
  ([dir {:keys [progress download-fn] :or {download-fn download-url}}]
   (fn [{:keys [url filename file-size validate] :as job :or {validate (constantly true)}}]
     (when-not (s/valid? ::job job)
       (throw (ex-info "invalid download job" (s/explain-data ::job job))))
     (let [cached-file (io/file dir filename)
           valid? (when (.exists cached-file) (validate cached-file))
           progress-ch (a/chan)]
       (if valid?
         {:from-cache true :f cached-file}                   ;; we have a valid file in cache, just return it
         (do                                                 ;; no file, let's create cache [safe if already exists)
           (io/make-parents cached-file)
           (try
             (when (and file-size progress)
               (a/thread (report-progress cached-file file-size progress-ch))
               (a/go (print-progress progress-ch)))
             (download-fn url cached-file)                   ;; try to download
             (when (validate cached-file) {:from-cache false :f cached-file})
             (catch Exception e (println "Failed to download item: " e))
             (finally (a/close! progress-ch)))))))))          ;; no matter what happens, close progress channel


;;
;; TRUD specific caching functionality
;;

(defn- trud-cache-filename
  "Return the file to be used for the archive."
  [dir {:keys [itemIdentifier ^LocalDate releaseDate archiveFileName]}]
  (str itemIdentifier
       "--"
       (.format releaseDate DateTimeFormatter/ISO_LOCAL_DATE)
       "--"
       archiveFileName))

(defn- validate-trud-file
  "Validates a downloaded TRUD release file according to release metadata,
  returning a `java.io.File` if it is valid."
  [{:keys [itemIdentifier archiveFileSizeBytes] :as release} f]
  (let [f' (io/file f)]
    (when (.exists f')
      (let [size (.length f')
            right-size? (= archiveFileSizeBytes size)
            checksum? (when right-size? (check/valid-checksum? release f'))]
        (cond
          (and right-size? checksum?)
          f'
          (not right-size?)
          (log/info "Unable to use archive: incorrect file size:" {:itemIdentifier itemIdentifier :expected archiveFileSizeBytes :got size})
          (not checksum?)
          (log/info "Incorrect checksum for archive file"))))))

(s/def ::itemIdentifier int?)
(s/def ::releaseDate (partial instance? LocalDate))
(s/def ::archiveFileUrl string?)
(s/def ::archiveFileSizeBytes int?)
(s/def ::archiveFileName string?)

(s/def ::release
  (s/keys :req-un [::itemIdentifier ::releaseDate ::archiveFileUrl
                   ::archiveFileSizeBytes ::archiveFileName]))

(defn get-release-file
  "Get an archive file either from the cache or downloaded from TRUD."
  (^File [dir release] (get-release-file dir release {:progress false}))
  (^File [dir {url :archiveFileUrl file-size :archiveFileSizeBytes :as release} {:keys [progress]}]
   (when-not (s/valid? ::release release)
     (throw (ex-info "invalid release" (s/explain-data ::release release))))
   (let [item (select-keys release [:itemIdentifier :archiveFileName :releaseDate])
         cache (make-cache dir {:progress    progress
                                :download-fn (fn [url target] (do (log/info "Downloading item" item) (download-url url target)))})
         job {:url       url, :filename (trud-cache-filename dir release)
              :file-size file-size, :validate (partial validate-trud-file release)}
         {:keys [from-cache f]} (cache job)]
     (if from-cache
       (log/info "Item already in cache" (select-keys release [:itemIdentifier :archiveFileName :releaseDate]))
       (log/info "Item downloaded " (select-keys release [:itemIdentifier :archiveFileName :releaseDate])))
     f)))

(defn ^:deprecated get-archive-file
  "Get an archive file either from the cache or downloaded from TRUD.
  Returns result as a `java.nio.file.Path`."
  [dir release]
  (when-let [f (get-release-file dir release)]
    (.toPath f)))

(comment
  (get-archive-file "/tmp/trud" {:itemIdentifier       341
                                 :releaseDate          (LocalDate/of 2021 01 29)
                                 :archiveFileUrl       "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
                                 :archiveFileSizeBytes 13264
                                 :archiveFileName      "dummy.pdf"}))



