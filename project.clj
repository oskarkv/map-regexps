(defproject org.clojars.oskarkv/map-regexps "0.1.0-SNAPSHOT"
  :description "Regexps for maps."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.json "0.2.0"]
                 [asm "3.2"]]
  :source-paths      ["src/clojure"]
  :java-source-paths ["src/java"]
  :resource-paths    ["lib"]
  :target-path "target/"
  :compile-path "%s/classes")
