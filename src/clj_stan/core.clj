;;   Copyright (c) Metail and Thomas Athorne
;;
;;   Licensed under the Apache License, Version 2.0 (the "License");
;;   you may not use this file except in compliance with the License.
;;   You may obtain a copy of the License at
;;
;;       http://www.apache.org/licenses/LICENSE-2.0
;;
;;   Unless required by applicable law or agreed to in writing, software
;;   distributed under the License is distributed on an "AS IS" BASIS,
;;   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;;   See the License for the specific language governing permissions and
;;   limitations under the License.

(ns clj-stan.core
  (:require [clj-stan.r-dump-format :as r-dump]
            [clj-stan.read-output :as output]
            [me.raynes.conch :as conch]
            [environ.core :refer [env]]
            [pandect.algo.sha256 :refer [sha256]]
            [clojure.java.io :as io]))

(defn execute
  [& args]
  (try (apply conch/execute args)
       (catch Exception e
         (run! println (:err (:proc (.data e))))
         (run! println (:out (:proc (.data e))))
         (throw e))))

(defprotocol Model
  (sample [this data-map] [this data-map params])
  (optimize [this data-map] [this data-map params])
  (variational [this data-map algorithm] [this data-map algorithm params]))

(defn tmp-dir
  []
  (let [d (io/file (str (System/getProperty "java.io.tmpdir") "/"
                        (System/currentTimeMillis) "-" (rand-int 1000000)))]
    (.mkdir d)
    d))

(defrecord CompiledModel [executable]

  Model
  (sample [this data-map] (sample this data-map {}))

  (sample [this data-map {:keys [chains chain-length seed]
                          :or {chains 5 chain-length 2000 seed -1}}]
    (let [t (tmp-dir)]
      (r-dump/r-dump (str t "/tmp-data.R") data-map)
      (let [procs (mapv #(conch/execute executable "sample"
                                        (str "num_samples=" chain-length)
                                        "random" (str "seed=" seed)
                                        "data" (str "file=" t "/tmp-data.R")
                                        "output" (str "file=" t "/output-" % ".csv")
                                        {:background true})
                        (range chains))]
        (mapv deref procs)
        (into []
              (mapcat #(output/read-stan-output (str t "/output-" % ".csv")))
              (range chains)))))

  (optimize [this data-map] (optimize this data-map {}))

  (optimize [this data-map {:keys [seed] :or {seed -1}}]
    (let [t (tmp-dir)]
      (r-dump/r-dump (str t "/tmp-data.R") data-map)
      (execute executable "optimize"
               "random" (str "seed=" seed)
               "data" (str "file=" t "/tmp-data.R")
               "output" (str "file=" t "/output.csv"))
      (first (output/read-stan-output (str t "/output.csv")))))

  (variational [this data-map algorithm] (variational this data-map algorithm {}))

  (variational [this data-map algorithm {:keys [seed samples] :or {seed -1 samples 1000}}]
    (let [t (tmp-dir)]
      (r-dump/r-dump (str t "/tmp-data.R") data-map)
      (execute executable "variational"
               (str "algorithm=" algorithm)
               (str "output_samples=" samples)
               "random" (str "seed=" seed)
               "data" (str "file=" t "/tmp-data.R")
               "output" (str "file=" t "/output.csv"))
      (let [[mode & samples] (output/read-stan-output (str t "/output.csv"))]
        {:mode    mode
         :samples samples}))))

(defn lib
  [path lib-name]
  (first (filter (fn [s]
                   (.startsWith s (str path lib-name "_")))
                 (map str (.listFiles (io/file path))))))

(defn make
  [model]
  (let [text (slurp model)
        hash (sha256 text)
        exe (str "target/clj-stan/_" hash)
        _ (io/make-parents exe)]
    (when (not (.exists (io/file exe)))
      (let [home (or (env :stan-home)
                     (throw (Exception. "STAN_HOME environment variable is not set.
                                     See `clj-stan` documentation.")))
            model-file (str exe ".stan")
            hpp-file (str exe ".hpp")
            lib-path (str home "/stan/lib/stan_math/lib/")]
        (spit model-file text)
        (println (format "Compiling %s to C++." model))
        (execute (str home "/bin/stanc") model-file (str "--o=" hpp-file))
        (println (format "Compiling C++ for %s into executable." model))
        (execute "g++"
                 "-I" home "-I" (str home "/src")
                 "-isystem" (str home "/stan/src")
                 "-isystem" (str home "/stan/lib/stan_math/")
                 "-isystem" (lib lib-path "eigen")
                 "-isystem" (lib lib-path "boost")
                 "-isystem" (str (lib lib-path "sundials") "/include")
                 "-std=c++1y" "-Wall"
                 "-DEIGEN_NO_DEBUG" "-DBOOST_RESULT_OF_USE_TR1" "-DBOOST_NO_DECLTYPE"
                 "-DBOOST_DISABLE_ASSERTS" "-DBOOST_PHOENIX_NO_VARIADIC_EXPRESSION"
                 "-DFUSION_MAX_VECTOR_SIZE=12" "-DNO_FPRINTF_OUTPUT"
                 "-Wno-unused-function" "-Wno-uninitialized" "-Wno-unused-local-typedefs"
                 "-pipe" "-O3" "-o" exe (str home "/src/cmdstan/main.cpp")
                 "-include" hpp-file
                 (str (lib lib-path "sundials") "/lib/libsundials_nvecserial.a")
                 (str (lib lib-path "sundials") "/lib/libsundials_cvodes.a")
                 (str (lib lib-path "sundials") "/lib/libsundials_idas.a"))))
    (->CompiledModel exe)))
