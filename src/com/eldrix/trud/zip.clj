(ns com.eldrix.trud.zip
  (:require [clojure.java.io :as io])
  (:import (java.nio.file Files Path)
           (java.nio.file.attribute FileAttribute)
           (java.util.zip ZipInputStream)))

(defn unzip
  "Unzip a zip archive to the directory specified.
  Parameters:
  - in  : path of zip file
  - out : path of the directory to which files will be extracted.

  The out directory will be created if it doesn't exist."
  [^Path in ^Path out]
  (Files/createDirectories out (make-array FileAttribute 0)) ;; this will be a NOP if directory already exists
  (with-open [input (ZipInputStream. (io/input-stream (.toFile in)))]
    (loop [entry (.getNextEntry input)]
      (when entry
        (if (.isDirectory entry)
          (Files/createDirectories (.resolve out (.getName entry)) (make-array FileAttribute 0))
          (let [new-file (.toFile (.resolve out (.getName entry)))
                parent (.getParentFile new-file)]
            (when-not (.isDirectory parent) (.mkdirs parent)) ;; if parent directory doesn't exist, create
            (io/copy input (.toFile (.resolve out (.getName entry))))))
        (recur (.getNextEntry input))))))
