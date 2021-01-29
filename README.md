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

```
 com.eldrix/trud                {:git/url "https://github.com/wardle/trud.git"
                                             :sha     "774c6e76d681bb318dd2a6a3d174f46e8ba9c831"}
```

### 3. Work out the subscriptions you need.

There is no API for this part. Login to TRUD and get the release pack identifiers
for the distributions you want.

### 4. Use the trud library 

You express your subscriptions as a vector of release identifiers

```clojure
(def ch (trud/download-releases api-key [58 341 246]))
```

or as a a map
```clojure
(def ch (download-updated-releases api-key [{:release-identifier 58 :release-date (LocalDate/now)}
                                    {:release-identifier 341 :release-date (LocalDate/of 2020 11 19)}
                                    {:release-identifier 246}]))

```

The release-date should be the date of your currently installed version. If
provided, only distributions updated after that date will be download.

### 5. Wait for results on the channel and process as you need.

```clojure
(loop [data (clojure.core.async/<!! ch)]
  (when data
    (process data)
    (recur (clojure.core.async/<!! ch))))
```

Each result will be the metadata directly from the TRUD API for that release
supplemented with a key `:download-path` with a
java.nio.file.Path representing the path to the unzipped distribution
files.