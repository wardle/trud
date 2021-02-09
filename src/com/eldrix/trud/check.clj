(ns com.eldrix.trud.check
  "Check provides support for validating downloaded TRUD files.

  There are three mechanisms for checking the integrity of downloaded files.

  1. Checksum  (hash)
  2. Signature (GPG)
  3. The use of https to download the files.

  It appears as if checksums are generated using a Windows command line tool
  called FCIV (https://docs.microsoft.com/en-us/troubleshoot/windows-server/windows-security/fciv-availability-and-description).

  <?XML version=\"1.0\" encoding=\"utf-8\"?>
  <FCIV>
      <FILE_ENTRY>
         <name>ntdll.dll</name> <MD5>bL/ZGbqnyeA8hHGuTY+LsA==</MD5>
      </FILE_ENTRY>
  </FCIV>

  It appears that FCIV generates only MD5 and SHA1 hashes."
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clj-http.client :as client]
            [buddy.core.codecs :as codecs]
            [buddy.core.hash :as hash])
  (:import (java.io File)))

(defn- parse-fciv-file-entry [loc]
  (let [props (:content (zip/node loc))]
    (apply hash-map (interleave (map :tag props) (map (comp first :content) props)))))

(defn- fetch-fciv
  "Fetch and parse a FCIV XML structure from the URL specified.
  Returns a map keyed by filename. Each value is itself a map with keys
  as the type of hash (`:MD5` or `:SHA1` at the time of writing) and the
  actual hash as the value."
  [url]
  (let [fciv (-> (client/get url)
                 :body
                 xml/parse-str
                 zip/xml-zip
                 (zx/xml-> :FCIV :FILE_ENTRY parse-fciv-file-entry))]
    (apply hash-map (mapcat #(vector (:name %) (dissoc % :name)) fciv))))

(defn valid-checksum?
  "Determines whether the file specified has a valid checksum.
  Note: if we do not support a checksum type, then we return `true` with a
  warning."
  [{:keys [checksumFileUrl archiveFileName] :as release} ^File downloaded-file]
  (let [fciv (fetch-fciv checksumFileUrl)
        filename archiveFileName]
    (loop [props (get fciv filename)]
      (if-not props
        (do (println "Warning: unable to validate checksum: no supported checksum available.\nPublished checksums:" fciv)
            true)
        (let [[k v] (first props)
              engine (hash/resolve-digest-engine (keyword (str/lower-case (name k))))]
          (if engine
            (let [calc (-> downloaded-file
                           io/input-stream
                           (hash/-digest engine)
                           codecs/bytes->b64
                           codecs/bytes->str)]
              (= v calc))
            (recur (next props))))))))

(comment
  (require '[com.eldrix.trud.release :as release])
  (def api-key (slurp "api-key.txt"))
  (def release (release/get-latest api-key 341))
  release
  (fetch-fciv (:checksumFileUrl release))
  (valid-checksum? release (File. "/tmp/trud/341--2021-01-29--hscorgrefdataxml_data_1.0.0_20210129000001.zip"))
  )