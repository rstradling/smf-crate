(defproject org.clojars.strad/smf-crate "0.2.0-SNAPSHOT"
  :description "Provides pallet commands to create an smf service"
  :url "https://github.com/rstradling/smf-crate"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.palletops/pallet "0.8.0-SNAPSHOT"]
                 [org.clojure/tools.logging "0.2.6"]]
  :repositories {"sonatype"
                 "http://oss.sonatype.org/content/repositories/releases"
                 "sonatype-snapshots"
                 "http://oss.sonatype.org/content/repositories/snapshots"}
  :test-selectors {:default (complement :live-test)
                   :live-test :live-test
                   :integration :integration
                   :all (constantly true)}
  :test-paths ["test" "test-resources"]
  :profiles {:live-test {:dependencies
                         [[com.palletops/pallet "0.8.0-SNAPSHOT"]
                          [org.cloudhoist/pallet-jclouds "1.5.1"]
                          [org.jclouds/jclouds-compute "1.5.6"]
                          [org.jclouds.labs/joyent-cloudapi "1.5.6"]
                          [org.jclouds.labs/joyentcloud "1.5.6"]
                          [org.slf4j/slf4j-api "1.6.1"]
                          [org.jclouds.driver/jclouds-sshj "1.5.6"]
                          [com.jcraft/jsch "0.1.49"]]}})
