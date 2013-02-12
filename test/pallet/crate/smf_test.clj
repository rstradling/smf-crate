(ns pallet.crate.smf-test
  (:use clojure.test
        pallet.crate.smf
        clojure.data.xml
        clojure.tools.logging
        pallet.compute.node-list)
  (:require pallet.compute)
  (:require [pallet.core :as pallet.core]
            [pallet.utils])
  (:require pallet.configure)
  (:require [clojure.java.io :as io]))

(info "testing pallet")
(def expected-results
  [:service_bundle { :type "manifest" :name "memcached"}
   [:service {:name "site/memcached" :type "service" :version "1"}
    [:create_default_instance {:enabled false}]
    [:single_instance]
    [:dependency {:name "net-physical" :grouping "require_all"
                  :restart_on "none" :type "service"}
     [:service_fmri {:value "svc:/network/physical"}]]
    [:dependency {:name "filesystem" :grouping "require_all"
                  :restart_on "none" :type "service"}
     [:service_fmri {:value "svc:/system/filesystem/local"}]]    
    [:method_context
     [:method_credential {:user "user" :group "group"}]]
    [:exec_method {:type "method" :name "start" :exec "exe run"
                   :timeout_seconds 60}]
    [:exec_method {:type "method" :name "stop" :exec :kill
                   :timeout_seconds 60}]
    [:property_group {:name "startd" :type "framework"}
     [:propval {:name "duration" :type "astring" :value "wait"}]
     [:propval {:name "ignore_error" :type "astring" :value "core,signal"}]]
    [:property_group {:name "application" :type "application"}]
    [:stability {:value  "Evolving"}]]])

(def mod-expected-results
  [:service_bundle { :type "manifest" :name "memcached"}
   [:service {:name "site/memcached" :type "service" :version "1"}
    [:create_default_instance {:enabled true}]
    nil
    nil
    [:dependency {:name "filesystem" :grouping "require_all"
                  :restart_on "none" :type "service"}
     [:service_fmri {:value "svc:/system/filesystem/local"}]]        
    [:method_context {:working_directory "/root"}
     [:method_credential {:user "user" :group "group"}]]
    [:exec_method {:type "method" :name "start" :exec "exe run"
                   :timeout_seconds 60}]
    [:exec_method {:type "method" :name "stop" :exec "exe stop"
                   :timeout_seconds 60}]
    [:property_group {:name "startd" :type "framework"}
     [:propval {:name "duration" :type "astring" :value "contract"}]
     [:propval {:name "ignore_error" :type "astring" :value "core,signal"}]]
    [:property_group {:name "application" :type "application"}]
    [:stability {:value  "Unstable"}]]])

(deftest create-smf-test
  (testing "That the default options work correctly"
    (let [
          smf (create-smf  "site" "memcached" "1" "exe run" "user" "group")]
      (is (= smf expected-results))))
  (testing "That specified options work correctly"
        (let [
          smf (create-smf  "site" "memcached" "1" "exe run" "user" "group"
                            { 
                             :multiple-instances? false
                             :instance-name "default"
                             :config-file ""
                             :stop-command :kill
                             :process-management :wait
                             :network? true
                             :enabled? false
                             :timeout 60
                             :stability-value :Evolving})]
      (is (= smf expected-results))))
  (testing "That modified options work correctly"
    (let [
          smf (create-smf "site" "memcached" "1" "exe run" "user" "group"
                          {
                           :multiple-instances? true
                           :instance-name "testing"
                           :config-file ""
                           :stop-command "exe stop"
                           :process-management :contract
                           :network? false
                           :enabled? true
                           :timeout 60
                           :stability-value :Unstable
                           :working-dir "/root"})]
      (is (= smf mod-expected-results)))))

(deftest write-smf-test
  (testing "Testing that the write-smf works property with defaults"
    (let [
          smf (create-smf "site" "memcached" "1" "exe run" "user" "group")
          filename (write-smf smf true)
          xml-file (parse (io/reader filename))
          xml-expected (sexp-as-element expected-results)]
      (is (= xml-file xml-expected))))
  (testing "Testing that the write-smf works properly with different data"
    (let [
          smf (create-smf "site" "memcached" "1" "exe run" "user" "group"
                          {
                           :multiple-instances? true
                           :instance-name "testing"
                           :config-file ""
                           :stop-command "exe stop"
                           :process-management :contract
                           :network? false
                           :enabled? true
                           :timeout 60
                           :stability-value :Unstable
                           :working-dir "/root"})
          filename (write-smf smf true)
          xml-file (parse (io/reader filename))
          xml-expected (sexp-as-element mod-expected-results)]
      (is (= xml-file xml-expected)))))

(deftest ^:live-test install-smf-service-test
  (defn configure
    [session]
    (let [smf  (create-smf "site" "testing" "1" "bash" "root" "root"
                           {:enabled :true :process-management :contract})]
      (install-smf-service session smf "/root/test1.smf" true)))
  (defn configure2
    [session]
    (let [smf  (create-smf "site" "testing2" "1" "bash" "root" "root"
                           {:enabled :false :process-management :contract
                            :network? false :multiple-instances? true})]
      (install-smf-service session smf "/root/test2.smf" true)))
  (def my-data-center (pallet.compute/service :data-center))
  (def group-spec1 (pallet.core/group-spec "smartos-testing"
                                          :phases {:configure configure}))
  (def group-spec2 (pallet.core/group-spec "smartos-testing"
                                           :phases {:configure configure2
                                                    }))
  (testing "Testing that the smf works on an actual service"
    (let
        [ _ (pallet.core/lift group-spec1 :compute my-data-center
                              :user (pallet.utils/make-user "root"))
         ]
      (is (= 1 1)) ; assume if the above does not throw exceptions
                   ; everything works
      ))
  (testing "Testing that a modified smf works on an actual service"
        (let
        [ _ (pallet.core/lift group-spec2 :compute my-data-center
                              :user (pallet.utils/make-user "root"))
         ]
      (is (= 1 1)) ; assume if the above does not throw exceptions
                   ; everything works
      )))



