(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'com.eldrix/trud)
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
      (b/delete {:path "target"}))

(defn jar
      "Create a library jar file."
      [_]
      (println "Building" lib version)
      (b/write-pom {:class-dir class-dir
                    :lib       lib
                    :version   version
                    :basis     basis
                    :src-dirs  ["src"]
                    :scm       {:url                 "https://github.com/wardle/trud"
                                :tag                 (str "v" version)
                                :connection          "scm:git:git://github.com/wardle/trud.git"
                                :developerConnection "scm:git:ssh://git@github.com/wardle/trud.git"}
                    :pom-data  [[:description "Tools to automate downloads from the UK TRUD (Technology Reference data Update Distribution)"]
                                [:developers
                                 [:developer
                                  [:id "wardle"] [:name "Mark Wardle"] [:email "mark@wardle.org"] [:url "https://wardle.org"]]]
                                [:organization [:name "Eldrix Ltd"]]
                                [:licenses
                                 [:license
                                  [:name "Eclipse Public License v2.0"]
                                  [:url "https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html"]
                                  [:distribution "repo"]]]]})
      (b/copy-dir {:src-dirs ["src"], :target-dir class-dir})
      (b/copy-file {:src "LICENSE" :target "target/classes/META-INF/LICENSE"})
      (b/jar {:class-dir class-dir, :jar-file jar-file}))

(defn install
      "Install library to local maven repository."
      [_]
      (clean nil)
      (jar nil)
      (println "Installing" lib version)
      (b/install {:basis     basis
                  :lib       lib
                  :version   version
                  :jar-file  jar-file
                  :class-dir class-dir}))

(defn deploy
      "Deploy library to clojars.
      Environment variables CLOJARS_USERNAME and CLOJARS_PASSWORD must be set."
      [_]
      (clean nil)
      (jar nil)
      (dd/deploy {:installer :remote
                  :artifact  jar-file
                  :pom-file  (b/pom-path {:lib       lib
                                          :class-dir class-dir})}))