(ns com.eldrix.trud.impl.check
  "Check provides support for validating downloaded TRUD files.

  There are four mechanisms for checking the integrity of downloaded files.

  1. SHA256 hash provided in release metadata
  2. Checksum file containing a hash (legacy; using FCIV)
  3. Signature file (GPG)
  4. The use of https to download the files.

  It appears as if the legacy checksums are generated using a Windows command
  line tool called FCIV (https://docs.microsoft.com/en-us/troubleshoot/windows-server/windows-security/fciv-availability-and-description).

  <?XML version=\"1.0\" encoding=\"utf-8\"?>
  <FCIV>
      <FILE_ENTRY>
         <name>ntdll.dll</name> <MD5>bL/ZGbqnyeA8hHGuTY+LsA==</MD5>
      </FILE_ENTRY>
  </FCIV>

  It appears that FCIV generates only MD5 and SHA1 hashes.

  TRUD has been updated (see https://isd.digital.nhs.uk/trud/users/guest/filters/0/releases-help/sha256)
  and now recommends using the SHA256 hash provided in the release metadata,
  and so we can now avoid using the FCIV based hash system entirely in favour
  of simply checking the SHA256 hash."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [buddy.core.codecs :as codecs]
            [buddy.core.hash :as hash]))

(set! *warn-on-reflection* true)

(defn sha256sum
  "Return the SHA256 message digest for a file. Returns a string encoding the
  digest as hexadecimal. Equivalent to the command-line 'sha256sum' on BSD/Linux
  systems."
  ^String [f]
  (with-open [is (io/input-stream f)]
    (codecs/bytes->hex (hash/sha256 is))))

(defn check-integrity
  "Checks integrity of a downloaded release file.
  Parameters:
  - release - release metadata
  - downloaded-file - anything coercible using [[clojure.java.io/as-file]].
  Returns a map containing
  :status  - :valid, :invalid, or :not-checked if checksum could not be checked.
  :reason  - a keyword if invalid (e.g. :size, :digest, :not-found)
  :message - human-readable message"
  [{:keys [archiveFileSha256 archiveFileSizeBytes] :as _release} downloaded-file]
  (let [f (io/as-file downloaded-file)
        size (.length f)]
    (cond
      (not (.exists f))
      {:status :invalid :reason :not-found :message "File not found"}

      (and archiveFileSizeBytes (not= archiveFileSizeBytes size))
      {:status :invalid, :reason :size, :message (str "Incorrect file size; expected: '" archiveFileSizeBytes "', got: '" size "'")}

      archiveFileSha256
      (if (.equalsIgnoreCase (sha256sum f) archiveFileSha256)
        {:status :valid}
        {:status :invalid, :reason :digest, :message "Incorrect SHA256 digest"})

      :else
      {:status :not-checked, :message "No supported digest in release file"})))

(comment
  (def api-key (str/trim (slurp "api-key.txt")))
  (require '[com.eldrix.trud.impl.release :as release])
  (release/get-releases api-key 101)
  (def release (release/get-latest api-key 101))
  release
  (sha256sum "README.md")
  (check-integrity {:archiveFileSizeBytes 8878 :archiveFileSha56 "1bcbf6877871c3edb4ff3075819f3a773c62f923c8187af4e4efc6ea9c15f624"} "README.md"))
