# trud
A clojure library to automate downloads from the UK TRUD (Technology Reference data Update Distribution).

## Introduction

The source of reference data in the UK is TRUD. However, until 25/1/2021, users needed to manually download distribution files from a web portal. NHS Digital used to provide an ftp server. NHS Digital have now released an API providing metadata on each reference data release, as well as links to the distribution files themselves.

## Getting started

### 1. Obtain a TRUD API key, and subscribe to reference data.

You will need to register as a user of TRUD and get an API key.

Login here [https://isd.digital.nhs.uk/trud3/user/guest/group/0/login/form](https://isd.digital.nhs.uk/trud3/user/guest/group/0/login/form).

### 2. Include the trud library in your project

e.g. when using deps.edn:

Make sure you use the latest commit hash from [https://github.com/wardle/trud](https://github.com/wardle/trud)

```clojure
 com.eldrix/trud                {:git/url "https://github.com/wardle/trud.git"
                                 :sha     "c4de38d56869e30c86dc785fec470da68f6d7be5"}
```

### 3. Work out the subscriptions you need.

There is no API for this part. Login to TRUD and get the release pack identifiers
for the distributions you want.

### 4. Use the trud library 

You express your subscriptions as a map. Each subscription is a map 
containing the keys:

- :release-identifier : the TRUD release identifier
- :existing-date      : optional, the date of the release you currently have

The existing-date should be the date of your currently installed version. If
provided, only distributions updated after that date will be download.

Here we pretend we don't have a release of 246 but do already have today's release of 341.

```clojure
(require '[com.eldrix.trud.core :as trud])
(def api-key "xxx")
(trud/download-subscriptions api-key [{:release-identifier 246}
                                    {:release-identifier 341 :release-date (LocalDate/now)}])
```

Result:

Each subscription will be augmented with the following keys:

- :latest-release : TRUD metadata about the release
- :latest-date    : The latest date of release
- :needs-update?  : Boolean, indicating whether an update is needed
- :download-path  : only when an update needed, the archive file downloaded and unzipped.

```clojure
({:release-identifier 246,
  :latest-release {:checksumFileLastModifiedTimestamp #object[java.time.Instant 0x4c7e988b "2020-07-08T10:23:38Z"],
                   :publicKeyFileSizeBytes 1736,
                   :checksumFileSizeBytes 186,
                   :releaseIdentifier 246,
                   :signatureFileName "trud_dd_cosds_9.0.1_20200625000001.sig",
                   :name "Cancer Outcomes and Services Data Set XML schema release 9.0.1",
                   :signatureFileLastModifiedTimestamp #object[java.time.Instant 0xeb69970 "2020-07-08T10:23:38Z"],
                   :releaseDate #object[java.time.LocalDate 0x1ca3207 "2020-07-08"],
                   :checksumFileName "trud_dd_cosds_9.0.1_20200625000001.xml",
                   :archiveFileLastModifiedTimestamp #object[java.time.Instant 0x28e653c2 "2020-07-08T10:13:41Z"],
                   :publicKeyFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/public-keys/trud-public-key-2013-04-01.pgp",
                   :publicKeyFileName "trud-public-key-2013-04-01.pgp",
                   :archiveFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/DD/9.0.1/DD_COSDS/dd_cosds_9.0.1_20200625000001.zip",
                   :archiveFileSizeBytes 1279705,
                   :id "DD_9.0.1_20200625000001",
                   :signatureFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/DD/9.0.1/DD_COSDS/trud_dd_cosds_9.0.1_20200625000001.xml.asc",
                   :checksumFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/DD/9.0.1/DD_COSDS/trud_dd_cosds_9.0.1_20200625000001.xml",
                   :publicKeyId 6,
                   :signatureFileSizeBytes 488,
                   :archiveFileName "dd_cosds_9.0.1_20200625000001.zip"},
  :latest-date #object[java.time.LocalDate 0x1ca3207 "2020-07-08"],
  :needs-update? true,
  :download-path #object[sun.nio.fs.UnixPath
                         0x10642c95
                         "/var/folders/w_/s108lpdd1bn84sntjbghwz3w0000gn/T/trud8189818739559265155"]}
 {:release-identifier 341,
  :existing-date #object[java.time.LocalDate 0x52283473 "2021-01-31"],
  :latest-release {:checksumFileLastModifiedTimestamp #object[java.time.Instant 0x2aa34af0 "2021-01-29T13:28:21Z"],
                   :publicKeyFileSizeBytes 1736,
                   :checksumFileSizeBytes 187,
                   :releaseIdentifier 341,
                   :signatureFileName "trud_hscorgrefdataxml_data_1.0.0_20210129000001.sig",
                   :name "Release 1.0.0",
                   :signatureFileLastModifiedTimestamp #object[java.time.Instant 0x7aa90fa9 "2021-01-29T13:28:24Z"],
                   :releaseDate #object[java.time.LocalDate 0x60cb2b7d "2021-01-29"],
                   :checksumFileName "trud_hscorgrefdataxml_data_1.0.0_20210129000001.xml",
                   :archiveFileLastModifiedTimestamp #object[java.time.Instant 0x51fab375 "2021-01-29T13:26:23Z"],
                   :publicKeyFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/public-keys/trud-public-key-2013-04-01.pgp",
                   :publicKeyFileName "trud-public-key-2013-04-01.pgp",
                   :archiveFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/ODS/1.0.0/HSCORGREFDATAXML_DATA/hscorgrefdataxml_data_1.0.0_20210129000001.zip",
                   :archiveFileSizeBytes 26688464,
                   :id "hscorgrefdataxml_data_1.0.0_20210129000001.zip",
                   :signatureFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/ODS/1.0.0/HSCORGREFDATAXML_DATA/trud_hscorgrefdataxml_data_1.0.0_20210129000001.xml.asc",
                   :checksumFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/ODS/1.0.0/HSCORGREFDATAXML_DATA/trud_hscorgrefdataxml_data_1.0.0_20210129000001.xml",
                   :publicKeyId 6,
                   :signatureFileSizeBytes 488,
                   :archiveFileName "hscorgrefdataxml_data_1.0.0_20210129000001.zip"},
  :latest-date #object[java.time.LocalDate 0x60cb2b7d "2021-01-29"],
  :needs-update? false})
```
