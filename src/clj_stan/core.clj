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
  (optimize [this data-map]))

(defn tmp-dir
  []
  (let [d (io/file (str (System/getProperty "java.io.tmpdir") "/"
                        (System/currentTimeMillis) "-" (rand-int 1000000)))]
    (.mkdir d)
    d))

(defrecord CompiledModel [executable]

  Model
  (sample [this data-map] (sample this data-map {}))

  (sample [this data-map {:keys [chains chain-length] :or {chains 5 chain-length 2000}}]
    (let [t (tmp-dir)]
      (r-dump/r-dump (str t "/tmp-data.R") data-map)
      (let [procs (mapv #(conch/execute executable "sample"
                                        (str "num_samples=" chain-length)
                                        "data" (str "file=" t "/tmp-data.R")
                                        "output" (str "file=" t "/output-" % ".csv")
                                        {:background true})
                        (range chains))]
        (mapv deref procs)
        (into []
              (mapcat #(output/read-stan-output (str t "/output-" % ".csv")))
              (range chains)))))

  (optimize [this data-map]
    (let [t (tmp-dir)]
      (r-dump/r-dump (str t "/tmp-data.R") data-map)
      (execute executable "optimize"
               "data" (str "file=" t "/tmp-data.R")
               "output" (str "file=" t "/output.csv"))
      (first (output/read-stan-output (str t "/output.csv"))))))

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
            hpp-file (str exe ".hpp")]
        (spit model-file text)
        (println (format "Compiling %s to C++." model))
        (execute (str home "/bin/stanc") model-file (str "--o=" hpp-file))
        (println (format "Compiling C++ for %s into executable." model))
        (execute "g++"
                 (str "-I" home "/src")
                 (str "-I" home "/stan/src")
                 "-isystem" (str home "/stan/lib/stan_math/")
                 "-isystem" (str home "/stan/lib/stan_math/lib/eigen_3.3.3")
                 "-isystem" (str home "/stan/lib/stan_math/lib/boost_1.62.0")
                 "-isystem" (str home "/stan/lib/stan_math/lib/cvodes_2.9.0/include")
                 "-Wall"
                 "-DEIGEN_NO_DEBUG" "-DBOOST_RESULT_OF_USE_TR1" "-DBOOST_NO_DECLTYPE"
                 "-DBOOST_DISABLE_ASSERTS" "-DFUSION_MAX_VECTOR_SIZE=12" "-DNO_FPRINTF_OUTPUT"
                 "-pipe" "-Wno-unused-local-typedefs" "-lpthread"
                 "-O3" "-o" exe
                 (str home "/src/cmdstan/main.cpp")
                 "-include" hpp-file
                 (str home "/stan/lib/stan_math/lib/cvodes_2.9.0/lib/libsundials_nvecserial.a")
                 (str home "/stan/lib/stan_math/lib/cvodes_2.9.0/lib/libsundials_cvodes.a"))))
    (->CompiledModel exe)))
