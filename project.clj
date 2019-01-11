(defproject clj-shell "0.1.0-SNAPSHOT"
  :description "Shell-like utility functions for Clojure"
  :url "http://example.com/FIXME"
  :license {:name "GPL V3"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.10.0" :scope "provided"]]
  :plugins [[lein-codox "0.10.5"]]
  :codox {:output-path "codox"
          :metadata {:doc/format :markdown}})
