(ns com.thinkslate.codeq-playground.core
  (:require [datomic.api :as d]
            [clojure.pprint :refer [pprint]]))

 ;; Change me to the db-name you used when running ./script/analyze-repo
(def db-name "clojure")

(def uri (str "datomic:free://localhost:4334/" db-name))
(def conn (d/connect uri))
(def db (d/db conn))

(defn get-committers []
  (d/q '[:find ?email
         :where
         [_ :commit/committer ?u]
         [?u :email/address ?email]]
       db))
;; (get-committers)

(defn get-authors []
  (d/q '[:find ?email
         :where
         [_ :commit/author ?e]
         [?u :email/address ?email]]
       db))
;; (get-authors)

(defn initial-commit-date []
  (d/q '[:find (min ?date)
         :where
         [_ :commit/committedAt ?date]]
       db))
;; (initial-commit-date)

(defn num-commits []
  (d/q '[:find (count ?c)
         :where
         [?c :git/type :commit]]
       db))
;; (num-commits)

(defn top-n-committers [n]
  (->> (d/q '[:find ?email (count ?commit)
              :where
              [?commit :commit/author ?author]
              [?author :email/address ?email]]
            db)
       (sort-by second)
       reverse
       (take n)))
;; (top-n-committers 5)

(defn first-and-latest-commit-by-author-email [email]
  (d/q '[:find ?email (min ?date) (max ?date)
         :in $ ?email
         :where
         [?e :commit/committedAt ?date]
         [?e :commit/author ?u]
         [?u :email/address ?email]]
       db email))
;; (first-and-latest-commit-by-author-email "chouser@n01se.net")

(def
  ^{:doc "http://blog.datomic.com/2012/10/codeq.html"}
  rules
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
       db rules fq-fn))
;; (find-commits-for-fn "clojure.core/map")

(defn commit-dates [name]
  (map first
       (d/q '[:find (min ?date) ?sha
              :in $ % ?name
              :where
              [?n :code/name ?name]
              [?cq :clj/def ?n]
              [?cq :codeq/code ?c]
              [?c :code/sha ?sha]
              (codeq-commits ?cq ?commit)
              [?commit :commit/committedAt ?date]]
            db rules name)))
;; (commit-dates "clojure.core/pmap")

(defn committer-by-insts [insts]
  (d/q '[:find ?email
         :in $ [?inst ...]
         :where
         [?commit :commit/committedAt ?inst]
         [?commit :commit/committer ?u]
         [?u :email/address ?email]]
       db
       insts))
;; (-> "clojure.core/pmap" commit-dates committer-by-insts)

(defn get-authors-for-codeq [name]
  (-> name commit-dates committer-by-insts))
;; (get-authors-for-codeq "clojure.core/pmap")

(comment
  ;; Delete your DB
  (def db-name "clojure")
  (d/delete-database (str "datomic:free://localhost:4334/" db-name))
)
