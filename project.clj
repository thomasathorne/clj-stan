(defproject clj-stan "0.2.0-SNAPSHOT"
  :description "A library to interface with STAN, using the command line interface."
  :url "https://github.com/thomasathorne/clj-stan"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [me.raynes/conch "0.8.0"]
                 [pandect "0.6.1"]
                 [environ "1.1.0"]]
  :profiles {:dev {:resource-paths ["test-resources"]}})
