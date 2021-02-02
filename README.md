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
                                 :sha     "xxx"}
```

### 3. Work out the subscriptions you need.

There is no API for this part. Login to TRUD and get the item identifiers
for the distributions you want.

### 4. Use the trud library 

By default, archive files are stored in a cache directory. 
Here I use `"/tmp/trud"`.

```clojure
(require '[com.eldrix.trud.core :as trud])
(def api-key "xxx")
(def latest (trud/get-latest api-key "/tmp/trud" 341))
```

The result will be a map of data direct from the TRUD API for item `341`.
The archive file will have been downloaded and available via `:archiveFilePath`

```clojure
Result:
{:checksumFileLastModifiedTimestamp #object[java.time.Instant 0x72d19fd2 "2021-01-29T13:28:21Z"],
:publicKeyFileSizeBytes 1736,
:checksumFileSizeBytes 187,
:signatureFileName "trud_hscorgrefdataxml_data_1.0.0_20210129000001.sig",
:name "Release 1.0.0",
:signatureFileLastModifiedTimestamp #object[java.time.Instant 0x3b6f0a6f "2021-01-29T13:28:24Z"],
:itemIdentifier 341,
:releaseDate #object[java.time.LocalDate 0x6cdace1f "2021-01-29"],
:checksumFileName "trud_hscorgrefdataxml_data_1.0.0_20210129000001.xml",
:archiveFileLastModifiedTimestamp #object[java.time.Instant 0x738f5264 "2021-01-29T13:26:23Z"],
:publicKeyFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/public-keys/trud-public-key-2013-04-01.pgp",
:publicKeyFileName "trud-public-key-2013-04-01.pgp",
:archiveFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/ODS/1.0.0/HSCORGREFDATAXML_DATA/hscorgrefdataxml_data_1.0.0_20210129000001.zip",
:archiveFileSizeBytes 26688464,
:id "hscorgrefdataxml_data_1.0.0_20210129000001.zip",
:signatureFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/ODS/1.0.0/HSCORGREFDATAXML_DATA/trud_hscorgrefdataxml_data_1.0.0_20210129000001.xml.asc",
:checksumFileUrl "https://isd.digital.nhs.uk/api/v1/keys/7daa48e2a26f3afeef6f6c2a2feb00b62bcbe68b/files/ODS/1.0.0/HSCORGREFDATAXML_DATA/trud_hscorgrefdataxml_data_1.0.0_20210129000001.xml",
:publicKeyId 6,
:signatureFileSizeBytes 488,
:archiveFileName "hscorgrefdataxml_data_1.0.0_20210129000001.zip",
:needsUpdate? true,
:archiveFilePath #object[sun.nio.fs.UnixPath
0xdde6cc8
"/tmp/trud/341--2021-01-29--hscorgrefdataxml_data_1.0.0_20210129000001.zip"]}
```

Once you have the zip file, you can unzip to a temporary directory and
process, as necessary.