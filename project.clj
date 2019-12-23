(defproject supremereality "0.1.8"
  :description "responsive imageboard software"
  :url "https://www.supremereality.us/"
  :license {:name "BSD 3 Clause"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring "1.7.1"]
                 [metosin/ring-http-response "0.9.1"]
                 [ring-middleware-format "0.7.4"]
                 [ring/ring-anti-forgery "1.3.0"]
                 [compojure "1.6.1"]
                 [selmer "1.12.17"]
                 [org.clojure/java.jdbc "0.7.10"]
                 [org.postgresql/postgresql "42.2.8.jre7"]
                 [buddy/buddy-hashers "1.4.0"]
                 [clojure.java-time "0.3.2"]
                 [overtone/at-at "1.2.0"]
                 [http-kit "2.3.0"]]
  :repl-options {:init-ns supremereality.core}
  :main supremereality.core)
