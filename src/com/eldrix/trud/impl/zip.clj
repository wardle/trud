(ns com.eldrix.trud.impl.zip
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.nio.file Files Path Paths)
           (java.nio.file.attribute FileAttribute)
           (java.util.zip ZipInputStream)
           (java.util.regex Pattern)))

(defn unzip
  "Unzip a zip archive to the directory specified.
  Parameters:
  - in  : path of zip file
  - out : path of the directory to which files will be extracted.

  If no `out` path is specified, a temporary directory will be created.
  The out directory will be created if it doesn't exist."
  ([^Path in] (unzip in nil))
  ([^Path in ^Path out]
   (let [out-path (if out (Files/createDirectories out (make-array FileAttribute 0)) ;; this will be a NOP if directory already exists
                          (Files/createTempDirectory "trud" (make-array FileAttribute 0)))]
     (with-open [input (ZipInputStream. (io/input-stream (.toFile in)))]
       (loop [entry (.getNextEntry input)]
         (if-not entry
           out-path
           (do (if (.isDirectory entry)
                 (Files/createDirectories (.resolve out-path (.getName entry)) (make-array FileAttribute 0))
                 (let [new-file (.toFile (.resolve out-path (.getName entry)))
                       parent (.getParentFile new-file)]
                   (when-not (.isDirectory parent) (.mkdirs parent)) ;; if parent directory doesn't exist, create
                   (io/copy input (.toFile (.resolve out-path (.getName entry))))))
               (recur (.getNextEntry input)))))))))


(defn is-zip-file? [s]
  (str/ends-with? (str/lower-case s) ".zip"))

(declare unzip-in-place)

(defn unzip-nested
  "Unzip a zip archive to the directory specified, unzipping nested zip files.
  Parameters:
  - in  : path of zip file
  - out : path of the directory to which files will be extracted.

  If no `out` path is specified, a temporary directory will be created.
  The out directory will be created if it doesn't exist."
  ([^Path in] (unzip-nested in nil))
  ([^Path in ^Path out]
   (let [out-path (if out (Files/createDirectories out (make-array FileAttribute 0)) ;; this will be a NOP if directory already exists
                          (Files/createTempDirectory "trud" (make-array FileAttribute 0)))]
     (with-open [input (ZipInputStream. (io/input-stream (.toFile in)))]
       (loop [entry (.getNextEntry input)
              nested []]
         (if-not entry
           (do
             (unzip-in-place nested)
             out-path)
           (do (if (.isDirectory entry)
                 (Files/createDirectories (.resolve out-path (.getName entry)) (make-array FileAttribute 0))
                 (let [new-file (.toFile (.resolve out-path (.getName entry)))
                       parent (.getParentFile new-file)]
                   (when-not (.isDirectory parent) (.mkdirs parent)) ;; if parent directory doesn't exist, create
                   (io/copy input (.toFile (.resolve out-path (.getName entry))))))
               (recur (.getNextEntry input)
                      (if (is-zip-file? (.getName entry)) (conj nested (.resolve out-path (.getName entry))) nested)))))))))

(defn unzip-in-place
  "Unzip each path at the same location, creating a directory based on the name
   of the zip file, replacing any '.' in the filename with '_'. This doesn't
   check that this won't clobber an existing file.

   Parameters:
   - paths: a sequence of objects of type `java.nio.file.Path`."
  [paths]
  (doseq [^Path path paths]
    (let [n (.toString (.getFileName path))
          n' (str/replace n #"\." "-")]
      (unzip path (.resolve (.getParent path) ^String n')))))

(defn unzip2
  "Resolves a query representing files from a nested directory structure,
  including extracting nested zip files.

  A query is a potentially nested vector of strings or paths.
  [\"test.zip\"
   [\"nested1.zip\"]
   [\"nested2.zip\" \"file.txt\"]]

  This will extract the test.zip file, extract the files in both nested1.zip
  and nested2.zip and also returns a path for `file.txt` from the nested2.zip.

  Results will be java.nio.file.Path objects in the same shape as the query.
  For the example below, four paths will be returned."
  ([q] (unzip2 nil q))
  ([^Path p q]
   (cond
     ;; turn a string query into a path and re-run
     (string? q)
     (unzip2 (if p (.resolve p ^String q) (Paths/get q (make-array String 0))))

     ;; process a vector by resolving (perhaps unzipping?) first item and resolving each subsequent in context
     (vector? q) (let [unzipped (unzip2 p (first q))]
                   (apply conj [unzipped] (map #(unzip2 unzipped %) (rest q))))

     ;; process a path depending on the file type
     (instance? Path q)
     (if (str/ends-with? (str/lower-case (.toString q)) ".zip") ; - unzip zip files and return extracted files
       (unzip q)
       (if p (.resolve p ^Path q) q))

     ;; turn a regexp into a vector of filenames matching that regexp
     (and p (instance? Pattern q))
     (->> (file-seq (.toFile p))                            ;; sequence; each a java.io.File
          (map #(.toPath %))                                ;; convert each to a java.nio.Path
          (map #(.relativize p %))                          ;; generate a relative path
          (filter #(re-matches q (.toString %)))            ;; filter matching based on path
          (map #(.resolve p ^Path %)))                      ;; finally, back to an absolute path

     ;; return an empty slot if we haven't been able to resolve anything sensible
     :else
     nil)))

(defn delete-files
  "Delete all files from the `dir` specified, where `dir` can be anything
  coercible to a `file` using [[clojure.java.io/as-file]]."
  [dir]
  (doseq [f (file-seq dir)]
    (io/delete-file f :silently true)))

(defn delete-paths
  "Delete all paths specified, including nested structures.
  Parameters:
  - paths : a sequence of objects `java.nio.file.Path`."
  [paths]
  (doseq [path (flatten paths)]
    (delete-files (.toFile path))))

(comment)
