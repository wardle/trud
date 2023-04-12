# trud

[![Scc Count Badge](https://sloc.xyz/github/wardle/trud)](https://github.com/wardle/trud/)
[![Scc Cocomo Badge](https://sloc.xyz/github/wardle/trud?category=cocomo&avg-wage=100000)](https://github.com/wardle/trud/)
[![CircleCI](https://circleci.com/gh/wardle/trud.svg?style=svg)](https://circleci.com/gh/wardle/trud)
[![Clojars Project](https://img.shields.io/clojars/v/com.eldrix/trud.svg)](https://clojars.org/com.eldrix/trud)

A clojure library to automate downloads from the UK TRUD (Technology Reference data Update Distribution).

## Introduction

The source of reference data in the UK is TRUD. However, until 25/1/2021, users needed to manually download distribution files from a web portal. NHS Digital used to provide an ftp server. NHS Digital have now released an API providing metadata on each reference data release, as well as links to the distribution files themselves.

## Getting started

### 1. Obtain a TRUD API key, and subscribe to reference data.

You will need to register as a user of TRUD and get an API key.

Login here [https://isd.digital.nhs.uk/trud3/user/guest/group/0/login/form](https://isd.digital.nhs.uk/trud3/user/guest/group/0/login/form).

Choose your products and request a subscription using their portal. 

There is no API for this part. Login to TRUD and get the item identifiers
for the distributions you want.


### 2. Using from command-line

While not primarily designed to be used from the command-line, it is possible
to use this as a tool to automatically download multiple distributions from
the NHS Digital service to a directory of your choosing.

Here, include the right API key and distributions 101 and 105 will be downloaded
into the archive directory specified.

From source code (you need to escape data literals when invoked this way):

```shell
clj -X:download :progress 'true' :api-key '"xxx"' :cache-dir '"/tmp/trud"' :items '[101 105]'
```

Parameters:
- :progress - whether to show download progress (useful when used interactively)
- :api-key - the API key for the NHS Digital TRUD service (the key, not a filename of a file containing the key)
- :cache-dir - a directory to use as a cache, will be created if it doesn't exist

If you ran the above command at intervals, the cache directory would be 
populated with the latest releases of the items you specify. 
I have used this approach in a cronjob, for example. Any tools that use `trud`
for downloads can then easily share the same cache directory. 

If there is interest, it would be straightforward to make a simple command-line 
tool that can be run as a jar file. Raise an issue if you need this, but I 
now increasingly simply run from source code, even in production systems.

For example, here I run a command to download the latest versions of items 101 and 105:
```shell
mark@jupiter trud % clj -X:download :api-key '"***MY API KEY***"' :cache-dir '"cache"' :items '[101 105]'
12:22:36.095 [main] INFO com.eldrix.trud.core - Processing item 101
12:22:41.992 [main] INFO com.eldrix.trud.cache - Item already in cache {:itemIdentifier 101, :archiveFileName "uk_sct2cl_33.0.0_20220511000001Z.zip", :releaseDate #object[java.time.LocalDate 0x24615687 "2022-05-18"]}
12:22:41.996 [main] INFO com.eldrix.trud.core - Latest for item 101 : "uk_sct2cl_33.0.0_20220511000001Z.zip" {:archiveFilePath #object[sun.nio.fs.UnixPath 0x7f4f1536 "cache/101--2022-05-18--uk_sct2cl_33.0.0_20220511000001Z.zip"], :archiveFileSizeBytes 843903155}
12:22:41.996 [main] INFO com.eldrix.trud.core - Processing item 105
12:22:44.644 [main] INFO com.eldrix.trud.cache - Item already in cache {:itemIdentifier 105, :archiveFileName "uk_sct2dr_33.0.0_20220511000001Z.zip", :releaseDate #object[java.time.LocalDate 0x1ad1b737 "2022-05-18"]}
12:22:44.644 [main] INFO com.eldrix.trud.core - Latest for item 105 : "uk_sct2dr_33.0.0_20220511000001Z.zip" {:archiveFilePath #object[sun.nio.fs.UnixPath 0x342a1f84 "cache/105--2022-05-18--uk_sct2dr_33.0.0_20220511000001Z.zip"], :archiveFileSizeBytes 393105290}
```

You can of course use `trud` interactively if you wish, but this approach also
lends itself to automated scripting. For automatic scripts, you'll likely not
want to show download progress reporting.

### 3. Include the trud library in your project

[![Clojars Project](https://img.shields.io/clojars/v/com.eldrix/trud.svg)](https://clojars.org/com.eldrix/trud)

e.g. when using deps.edn, you can reference the library using git or maven coordinates. 

For git coordinates, make sure you use the latest commit hash from [https://github.com/wardle/trud](https://github.com/wardle/trud):

```clojure
 com.eldrix/trud                {:git/url "https://github.com/wardle/trud.git"
                                 :sha     "xxx"}
```

For a maven coordinate, simply specify the latest version from clojars.

```clojure
com.eldrix/trud {:mvn/version "xxx"}
```

See [clojars](https://clojars.org/com.eldrix/trud) for the latest release.

### 4. Use the trud library 

By default, archive files are stored in a cache directory. 
Here I use `"/tmp/trud"`:

```clojure
(require '[com.eldrix.trud.core :as trud])
(def api-key "xxx")
(def latest (trud/get-latest api-key "/tmp/trud" 341))
```

The result will be a map of data direct from the TRUD API for item `341`.
The archive file will have been downloaded and available via `:archiveFilePath`.
It will have had some integrity checks made, including checks on file size
and message digest (checksumming).

Result:

(note: this result includes URLs generated using one of my old API keys. 
Your URLs will be different as they will include your API key and should not
be publicly shared.)

```clojure
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

### 5. Processing your zip file (optional)

Once you have the zip file, you can unzip to a temporary directory and
process, as necessary For convenience, you can use the utility functions in
`com.eldrix.trud.zip`.

Here we are looking at the NHS ODS XML distribution, which always contains
two nested zip files "archive.zip" and "fullfile.zip". Here we extract 
any .xml files using a regexp in our nested query:

```clojure
(require '[com.eldrix.trud.zip :refer [unzip2 delete-paths]])
(def ods-xml-files [(:archiveFilePath latest)
                    ["archive.zip" #"\w+.xml"]
                    ["fullfile.zip" #"\w+.xml"]])
(def results (unzip2 ods-xml-files))
(get-in results [1 1])    ;; sequence of any XML files in archive zip
(get-in results [2 1])    ;; sequence of any XML files in fullfile.zip
(delete-paths results)
```


# Developer information (optional)

Identify outdated dependencies:
```shell
clj -M:outdated
```

Run compilation checks

```shell
clj -M:check
```

Run linting

```shell
clj -M:lint/eastwood
clj -M:lint/kondo

```

To build a library jar and publish to local maven repository:
```shell
clj -T:build install
```

To build a library jar and publish to [clojars](https://clojars.org)

```shell
clj -T:build deploy
```

The Circle CI badge indicates the results of automated tests - including a live test against the NHS Digital TRUD service. 
If these tests fail, it may be because the service is down or there has been a breaking versionc change.


