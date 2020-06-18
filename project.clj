(defproject pocket-conn-pool "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "1.2.603"]
                 [org.clojure/test.check "1.0.0"]
                 [org.clojars.czan/stateful-check "0.4.1"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [postgresql/postgresql "8.4-702.jdbc4"]]
  :repl-options {:init-ns pocket-conn-pool.core})
