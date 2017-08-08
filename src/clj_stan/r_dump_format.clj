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

(ns clj-stan.r-dump-format
  (:require [clojure.string :as str]))

(defn dims
  "Returns a list of dimensions for nested sequential types.
  Returns () for anything that doesn't have well defined dimensions.

  A special case: `{:dimensions [0 2]}` is considered to represent an
  empty vector of length 2 vectors---something like this is needed
  because clojure has no types, while STAN is quite strongly typed"
  [coll]
  (cond
    (sequential? coll) (cons (count coll)
                             (dims (first coll)))
    (map? coll) (or (:dimensions coll) ())
    :else ()))

(defn r-flatten
  "Flatten a nested sequence, while also transposing at each
  level---this translates naturally between clojure's natural indexing
  and the way the r-dump format treats array structures."
  [coll]
  (cond
    (not (sequential? coll))          ()
    (not (sequential? (first coll)))  coll
    (not (sequential? (ffirst coll))) (apply mapcat (partial vector) coll)
    :else                             (r-flatten (map r-flatten coll))))

(defn r-dump-vector
  [v]
  (str "c(" (str/join "," v) ")"))

(defn r-dump-key-value
  [[k v]]
  (str (name k) " <- "
       (cond
         (number? v)         v
         (number? (first v)) (r-dump-vector v)
         :else               (str "structure(" (r-dump-vector (r-flatten v))
                                  ", .Dim = " (r-dump-vector (dims v)) ")"))))

(defn r-dump
  "Write the data in a clojure map to the given file in r-dump
  format."
  [filename data]
  (spit filename (str/join "\n" (map r-dump-key-value data))))
