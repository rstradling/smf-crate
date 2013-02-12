(ns ^{:doc "This is a smf utility for pallet that is based upon a utility called
      manifold. This is based upon a system called manifold to create smf
      services.  Please see http://chrismiles.livejournal.com/26279.html for a
      good example of the functionality this is trying to recreate.
      For documentation on the smf format please see
      http://www.oracle.com/technetwork/server-storage/solaris/solaris-smf-manifest-wp-167902.pdf"
      :see-also [
                 "SMF Format"
                 "http://www.oracle.com/technetwork/server-storage/solaris/solaris-smf-manifest-wp-167902.pdf"
                 "Manifold Examle" "http://chrismiles.livejournal.com/26279.html" ]
      }
  pallet.crate.smf
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [pallet.action.exec-script :as exec-script]
            [pallet.action.remote-file :as remote-file]))

(def stable-set #{ :Standard :Stable :Evolving :Unstable :External :Obsolete})
(def proc-mgmt-model #{:wait :contract :transient})

(defn- create-network-dependency
  [network?]
  (if network?
    [:dependency {:name "net-physical" :grouping "require_all" :restart_on "none" :type "service"}
     [:service_fmri {:value "svc:/network/physical"}]]))

(defn- create-file-dependency
  [file-dependency?]
  (if file-dependency?
    [:dependency {:name "filesystem" :grouping "require_all" :restart_on "none" :type "service"}
     [:service_fmri {:value "svc:/system/filesystem/local"}]]))

(defn- create-method
  [name command timeout]
  [:exec_method {:type "method" :name name :exec command
                 :timeout_seconds timeout}])

(defn- create-property-group-startd
  [proc-mgmt]
  [:property_group {:name "startd" :type "framework"}
   [:propval {:name "duration" :type "astring" :value proc-mgmt}]
   [:propval {:name "ignore_error" :type "astring" :value "core,signal"}]])

(defn- create-property-group-application
  []
  [:property_group {:name "application" :type "application"}])

(defn- create-single-instance?
  [multiple-instances?]
  (if multiple-instances? nil [:single_instance]))

(defn- create-default-instance
  [enabled?]
  [:create_default_instance {:enabled enabled?}])

(defn- create-method-context
  [working-dir user group]
  (if working-dir
    [:method_context {:working_directory working-dir} [:method_credential
                                                       {:user user :group group}]]
    [:method_context [:method_credential {:user user :group group}]]))

(defn get-lines [fname]
  (with-open [r (io/reader fname)]
    (doall (line-seq r))))

(def smf-defaults {:multiple-instances? false :instance-name "default"
                    :config-file "" :stop-command :kill
                    :process-management :wait :network? true
                   :enabled? false :timeout 60 :stability-value :Evolving
                   :working-dir nil})


(defn create-smf
  "This will create smf clojure data that can be exported to xml.  Please note
   this does not include the DOCTYPE header for the xml file.  The DOCTYPE gets
   output in the write function.
   A user can take and expand upon this returned smf adding their own xml
   elements if they want to.  An optional dictionary can be passed in for values
   that have defaults.  By default opts values are
   :instance-name \"default\"
   :config-file \"\"
   :stop-command :kill 
   :process-management \"wait\"
   :network? true Whether this smf file depends upon the network already running
   :enabled? false Whether the service is enabled by default
   :timeout 60 # of seconds the service will try to run within
   :working-dir nil If this is set to something then the working directory of the method context
                    will be set to this working directory
   "
  ([service-category service-name service-version start-command user group opts]
     (let [merged (merge smf-defaults opts)
           {:keys [multiple-instances? instance-name config-file stop-command
                   process-management network? enabled? timeout
                   stability-value working-dir]} merged]
       [:service_bundle {:type "manifest" :name service-name}
        [:service {:name (str service-category "/" service-name)
                   :type "service"
                   :version service-version}
         (create-default-instance enabled?)
         (create-single-instance? multiple-instances?)
         (create-network-dependency network?)
         (create-file-dependency true)
         (create-method-context working-dir user group)
         (create-method "start" start-command timeout)
         (create-method "stop" stop-command timeout)
         (create-property-group-startd (name process-management))
         (create-property-group-application)         
         [:stability {:value (name stability-value)}]]]))
  ([service-category service-name service-version start-command user group]
     (create-smf service-category service-name service-version start-command
                 user group {})))

(defn write-smf
  "Write out the valid smf-data clojure file to a temporary file as xml
   return the name of the temporary file.  If delete-temp-file is true then
   the temp file is deleted on exit of the application per Java guidelines
   regarding deleteOnExit"
  [smf-data & [delete-temp-file?]]
  (let [temp (java.io.File/createTempFile "pallet" "smf")
        smf-xml (xml/indent-str (xml/sexp-as-element smf-data))
        ;; Cannot easily add a doctype so have to process the header.
        ;; Insert the DOCTYPE, and then insert the rest
        lines (clojure.string/split-lines smf-xml)
        first-line (first lines)
        rest-lines (subvec lines 1)
        output [ (str first-line "\n") "<!DOCTYPE service_bundle SYSTEM \"/usr/share/lib/xml/dtd/service_bundle.dtd.1\">\n"
                 (clojure.string/join "\n" rest-lines)]

        ]
    (if delete-temp-file? (.deleteOnExit temp))
    (with-open [wrtr (io/writer temp)]
      (doseq [line output]
        (.write wrtr line)))
    (.getAbsolutePath temp)))


(defn install-smf-service
  [session smf-data remote-path & [delete-temp-file?]]
  (-> session
   (remote-file/remote-file remote-path :local-file (write-smf smf-data delete-temp-file?))
   (exec-script/exec-checked-script
    (str "install the service " remote-path)
    ("svccfg validate " ~remote-path)
    ("svccfg import " ~remote-path))))

(defn delete-smf-service
  "Delete the specified smf service.  If force is true it will force delete the service"
  [session smf-name force?]
  (let [
        command (if force? "svccfg delete -f " "svccfg delete ")]
    (-> session
        (exec-script/exec-checked-script
         (str "Delete the service " smf-name)
         (~command ~smf-name)))))