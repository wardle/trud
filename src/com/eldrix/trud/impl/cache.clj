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

(defn ^:private print-bar
  "Prints a progress bar. If the progress bar has a zero total, then an
  indeterminate spinner is printed. Otherwise, a progress bar is shown."
  [prefix {:keys [total] :as bar}]
  (pr/print bar {:format (if (zero? total)
                           (str prefix " :progress :bar (:elapsed)")
                           (str prefix " :progress/:total   :percent% [:bar]  (:elapsed / :remaining)"))}))

(defn ^:private print-progress
  "Prints progress of a file, continuing until either the file size has reached
  the total-file-size, or cancel-ch is closed."
  [^File f total-file-size cancel-ch]
  (loop [bar (pr/progress-bar total-file-size)
         spinner (cycle (seq "|/-\\"))]
    (let [[v p] (a/alts!! [(a/timeout 1000) cancel-ch])]
      (if (= v :error)                                      ;; if there's a download error, print bar as-is and quit
        (print-bar (first spinner) bar)
        (if (= p cancel-ch)                                 ;; anything else sent on channel means we are finished
          (print-bar " " (assoc (pr/done bar) :progress total-file-size)) ;; ensure print a 100% complete with newline
          (do                                               ;; we were woken by the timeout, so loop
            (print-bar (first spinner) bar)                 ;; print current progress
            (recur (assoc bar :progress (.length f))
                   (next spinner))))))))

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
   (fn [{:keys [url filename file-size validate] :as job :or {file-size 0, validate (constantly true)}}]
     (when-not (s/valid? ::job job)
       (throw (ex-info "invalid download job" (s/explain-data ::job job))))
     (let [cached-file (io/file dir filename)
           valid? (when (.exists cached-file) (validate cached-file))
           status (a/chan)]
       (if valid?
         {:from-cache true :f cached-file}                  ;; we have a valid file in cache, just return it
         (do                                                ;; no file, let's create cache [safe if already exists)
           (io/make-parents cached-file)
           (when progress
             (a/thread (print-progress cached-file file-size status)))
           (try
             (download-fn url cached-file)                  ;; try to download
             (when progress (a/>!! status :done))
             (when (validate cached-file) {:from-cache false :f cached-file})
             (catch Exception e (when progress (a/>!! status :error)) (println "Failed to download item: " e) (throw e))
             (finally (a/close! status)))))))))             ;; signal to progress printer that we're done

;;
;; TRUD specific caching functionality
;;

(defn- trud-cache-filename
  "Return the filename to be used for the archive."
  [{:keys [itemIdentifier releaseDate archiveFileName]}]
  (str itemIdentifier
       "--"
       (.format ^LocalDate releaseDate DateTimeFormatter/ISO_LOCAL_DATE)
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
                                :download-fn (fn [url target] (log/info "Downloading item" item) (download-url url target))})
         job {:url       url, :filename (trud-cache-filename release)
              :file-size file-size, :validate (partial validate-trud-file release)}
         {:keys [from-cache f]} (cache job)]
     (log/info (if from-cache "Item already in cache" "Item downloaded") item)
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



