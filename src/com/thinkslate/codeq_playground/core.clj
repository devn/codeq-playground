(ns com.thinkslate.codeq-playground.core
  (:require [datomic.api :as d]))

(def uri "datomic:free://localhost:4334/clojure")
(def conn (d/connect uri))
(def db (d/db conn))

(def rules
  '[[(node-files ?n ?f) [?n :node/object ?f] [?f :git/type :blob]]
    [(node-files ?n ?f) [?n :node/object ?t] [?t :git/type :tree]
     [?t :tree/nodes ?n2] (node-files ?n2 ?f)]
    [(object-nodes ?o ?n) [?n :node/object ?o]]
    [(object-nodes ?o ?n) [?n2 :node/object ?o] [?t :tree/nodes ?n2] (object-nodes ?t ?n)]
    [(commit-files ?c ?f) [?c :commit/tree ?root] (node-files ?root ?f)]
    [(commit-codeqs ?c ?cq) (commit-files ?c ?f) [?cq :codeq/file ?f]]
    [(file-commits ?f ?c) (object-nodes ?f ?n) [?c :commit/tree ?n]]
    [(codeq-commits ?cq ?c) [?cq :codeq/file ?f] (file-commits ?f ?c)]])

(defn find-commits-for-fn
  "Usage: (find-commits-for-fn \"clojure.core/pmap\")"
  [fq-fn]
  (d/q '[:find ?src (min ?date)
         :in $ % ?name
         :where
         [?n :code/name ?name]
         [?cq :clj/def ?n]
         [?cq :codeq/code ?cs]
         [?cs :code/text ?src]
         [?cq :codeq/file ?f]
         (file-commits ?f ?c)
         (?c :commit/authoredAt ?date)]
       db rules "clojure.core/pmap"))

(comment
  ;; Delete a DB
  (d/delete-database "datomic:free://localhost:4334/YOUR_DB_NAME")
)
