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

(ns clj-stan.core-test
  (:require [clojure.test :refer :all]
            [clj-stan.core :as stan]))

(defn approx=
  ([a b] (approx= a b 0.0001))
  ([a b allowed-error] (< (- a allowed-error) b (+ a allowed-error))))

(defn mean [xs] (/ (apply + xs) (double (count xs))))

(deftest bernoulli-test
  (let [bernoulli (stan/make "test-resources/bernoulli.stan" "bernoulli")]
    (is (= 100 (count (stan/sample bernoulli {:N 0 :y []} {:chains 1 :chain-length 100}))))
    (is (= 100 (count (stan/sample bernoulli {:N 0 :y []} {:chains 10 :chain-length 10}))))
    (is (= 100 (count (stan/sample bernoulli {:N 0 :y []} {:chains 100 :chain-length 1}))))
    (let [data {:N 4 :y [0 1 0 1]}
          samples (stan/sample bernoulli data)
          optimized (stan/optimize bernoulli data)]
      (is (approx= 0.5 (:theta optimized)))
      (is (< (apply max (map :lp__ samples)) (:lp__ optimized)))
      (is (approx= 0.5 (mean (map :theta samples)) 0.01)))
    (let [data {:N 26 :y [0 1 0 1 1 1 1 1 0 1 1 1 1 0 0 1 1 0 0 0 0 1 1 1 1 1]}
          samples (stan/sample bernoulli data)
          optimized (stan/optimize bernoulli data)]
      (is (approx= (/ 16.5 25) (:theta optimized)))
      (is (< (apply max (map :lp__ samples)) (:lp__ optimized)))
      (is (approx= (/ 17.5 27) (mean (map :theta samples)) 0.01)))))

(deftest array-handling-test
  (let [arrays (stan/make "test-resources/arrays.stan" "arrays")]
    (let [data {:N 4 :K 3
                :sample_cov [[[10 2 2]
                              [2 10 2]
                              [2 2 10]]
                             [[11 3 2]
                              [3 10 1]
                              [2 1 9]]
                             [[10.1 4.4 -2]
                              [4.4 8.7 2]
                              [-2 2 10.1]]
                             [[10.3 2.1 0.4]
                              [2.1 12.1 -0.3]
                              [0.4 -0.3 9.8]]]}
          samples (stan/sample arrays data)
          optimized (stan/optimize arrays data)]
      (is (< (apply max (map :lp__ samples)) (:lp__ optimized))))
    (let [data {:N 1 :K 2 :sample_cov [[[10 1] [2 10]]]}
          exception (try (stan/sample arrays data)
                         (catch Exception e e))]
      (is (= java.lang.Exception (type exception)))
      (is (re-find #"sample_cov\[k0__\] is not symmetric" (.getMessage exception))))
    (let [data {:N 1 :K 2 :sample_cov []}
          exception (try (stan/sample arrays data)
                         (catch Exception e e))]
      (is (= java.lang.Exception (type exception)))
      (is (re-find #"mismatch in number dimensions" (.getMessage exception))))))
