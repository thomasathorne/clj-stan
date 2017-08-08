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

(ns clj-stan.read-output
  (:require [clojure.string :as str]
            [clojure.data.csv :as csv]))

(defn vec-assoc-in
  "A version of the `assoc-in` function that defaults to vectors
  rather than maps when adding a new integer key:

  ```(vec-assoc-in {} [:a 0] \"foo\") => {:a [\"foo\"]}```

  This is not safe: `(vec-assoc-in {} [:a 1] 2)` produces an index out
  of range exception."
  [coll [k & ks :as korks] x]
  (cond
    (nil? coll) (vec-assoc-in [] korks x)
    ks          (assoc coll k (vec-assoc-in (get coll k) ks x))
    :else       (assoc coll k x)))

(defn into-maps
  "Translates rows with STAN's output key names (eg. `:mu.1.1`,
  `:mu.2.3`) into the natural clojure nested vectors."
  [rows]
  (let [paths (map (comp (fn [[a & rest]]
                           (into [(keyword a)] (map (comp dec #(Integer/parseInt %)) rest)))
                         #(str/split % #"\."))
                   (first rows))]
    (into []
          (map (fn [row]
                 (reduce (fn [accum [path v]]
                           (vec-assoc-in accum path (Double/parseDouble v)))
                         {}
                         (map vector paths row))))
          (rest rows))))

(defn read-stan-output
  "Read a STAN output csv file, return a vector of clojure maps."
  [filename]
  (into-maps (csv/read-csv
              (str/join "\n" (remove #(re-find #"^#" %) (str/split-lines (slurp filename)))))))
