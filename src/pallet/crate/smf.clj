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
            [clojure.tools.logging :refer [debugf]]
            [pallet.actions :refer [exec-checked-script remote-file plan-when
                                    plan-when-not assoc-in-settings directory]]
            [pallet.api :as api]
            [pallet.crate :as crate]
            [pallet.crate.service :as service]
            [pallet.stevedore :refer [with-script-language script]]
            [pallet.utils :refer [apply-map]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Manifest

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

(def smf-defaults
  {:multiple-instances? false
   :instance-name "default"
   :config-file ""
   :stop-command :kill
   :process-management :child
   :network? true
   :enabled? false
   :timeout 60
   :stability-value :Evolving
   :working-dir nil})


(defn create-manifest-xml
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
  ([service-category
    service-name
    service-version
    start-command
    user
    group
    opts]
     (let [merged (merge smf-defaults opts)
           {:keys [multiple-instances?
                   instance-name
                   config-file
                   stop-command
                   process-management
                   network?
                   enabled?
                   timeout
                   stability-value
                   working-dir]} merged]

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

  ([service-category
    service-name
    service-version
    start-command
    user
    group]
     (create-manifest-xml service-category
                          service-name
                          service-version
                          start-command
                          user
                          group
                          {})))

(defn- get-service-manifest-content
  "build SMF manifest XML file content"
  [smf-data
   & [delete-temp-file?]]
  (let [smf-xml (xml/indent-str (xml/sexp-as-element smf-data))
        ;; Cannot easily add a doctype so have to process the header.
        ;; Insert the DOCTYPE, and then insert the rest
        lines (clojure.string/split-lines smf-xml)
        first-line (first lines)
        rest-lines (subvec lines 1)]
    (clojure.string/join "\n"
                         (apply vector
                                (str first-line "\n")
                                "<!DOCTYPE service_bundle SYSTEM \"/usr/share/lib/xml/dtd/service_bundle.dtd.1\">"
                                rest-lines))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Service Supervisor

(defmethod service/service-supervisor-available? :smf
  [_]
  true)

(defn- default-service-options
  []
  {:user (:username (crate/admin-user))
   :group (:username (crate/admin-user))})

(defmethod service/service-supervisor-config :smf
  [_
   {:keys [service-name init-file manifest-data manifest-path] :as service-options}
   {:keys [instance-id] :as options}]
  {:pre [service-name
         (or manifest-data init-file)
         (not (and manifest-data init-file))]}
  (debugf "Adding service settings for %s" service-name)
  (assoc-in-settings [:smf :method-dir] "/opt/custom/bin")
  (assoc-in-settings [:smf :manifest-dir] "/opt/custom/smf")
  (assoc-in-settings [:smf :services service-name]
                     (merge (default-service-options)
                            service-options)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Pallet service-supervisor actions

(def ^:private
  pallet-action->smf-action
  {:start   :enable
   :stop    :disable
   :enable  :enable
   :restart :restart})

(defn- perform-service-action
  [service-name
   action
   {:keys [wait-until-started] :as options}]
  {:pre [(string? service-name)
         (keyword? action)]}
  (let [action-name (name action)
        wait-until-started-string (if wait-until-started
                                    "-s"
                                    "")]
    (exec-checked-script
     (format "running SMF service action %s on %s %s"
             action-name
             service-name
             wait-until-started-string)
     ;; If the service is in maintenance mode, we need to clear that
     ;; fact before enabling, however clear doesn't offer a -s option,
     ;; so we disable instead
     (when (= "maintenance" @("svcs" -H -o "STATE" ~service-name))
       ("svcadm"
        "disable"
        ~service-name))
     ;; svcadm restart doesn't offer a -s option, so we disable and
     ;; re-enable
     (if (= "restart" ~(name action))
       (chain-and ("svcadm"
                   "disable"
                   ~service-name)
                  ("svcadm"
                   "enable"
                   ~wait-until-started-string
                   ~service-name))
       ("svcadm"
        ~action-name
        ~wait-until-started-string
        ~service-name)))))

(defmethod service/service-supervisor :smf
  [_
   {:keys [service-name]}
   {:keys [action if-flag instance-id wait-until-started]
    :or {action :start
         wait-until-started false}
    :as options}]
  {:pre [service-name action]}
  (assert (not (contains? options :if-stopped))
          "SMF does not support the concept of if-stopped")
  (let [perform-action-fn #(perform-service-action service-name
                                                   (pallet-action->smf-action action)
                                                   {:wait-until-started wait-until-started})]
    (if if-flag
      (plan-when (crate/target-flag? if-flag)
                 (perform-action-fn))
      (perform-action-fn))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Install a new SMF Service

(defn install-smf-service
  "install the new SMF manifest"
  [smf-data
   remote-path]
  (remote-file remote-path
               :literal true
               :content (get-service-manifest-content smf-data true))
  (exec-checked-script
   (str "install smf service")
   ("svccfg validate " ~remote-path)
   ("svccfg import " ~remote-path)))


(defn delete-smf-service
  "Delete the specified smf service.
    'force' will force delete"
  [smf-name force?]
  (let [command (if force?
                  "svccfg delete -f "
                  "svccfg delete ")]
    (exec-checked-script
     (~command ~smf-name))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Install Service for init-file or smf-data

(defn get-init-file-path
  [settings service-name]
  (let [method-dir (:method-dir settings)]
    (str method-dir "/" service-name)))

(defmulti install-service
  (fn [settings service-name service-options]
    (if (:init-file service-options)
      :init-file
      :manifest-xml)))

(defmethod install-service :init-file
  [settings
   service-name
   service-options]
  (let [init-file-path (get-init-file-path settings service-name)
        start-command (str init-file-path " start")
        stop-command (str init-file-path " stop")
        manifest-xml (create-manifest-xml (:service-category service-options)
                                          service-name
                                          (:version service-options)
                                          start-command
                                          (:user service-options)
                                          (:group service-options)
                                          (assoc (:options service-options)
                                            :stop-command stop-command))
        manifest-path (str (:manifest-dir settings) "/" service-name "-manifest.xml")]
    (apply-map remote-file
               init-file-path
               :mode "0755"
               :owner "root"
               :group "root"
               :literal true
               (:init-file service-options))

    (install-smf-service manifest-xml
                         manifest-path)))

(defmethod install-service :manifest-xml
  [settings service-name service-options]
  (let [manifest-path (str (:manifest-dir settings) "/" service-name "-manifest.xml")]
    (install-smf-service (:manifest-data service-options)
                         manifest-path)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## pallet configure phase

(crate/defplan configure-plan
  "create a new SMF manifest and load it"
  [{:keys [instance-id] :as options}]

  (let [settings (crate/get-settings :smf
                                     {:instance-id instance-id})
        services (:services settings)]
    (directory (:method-dir settings))
    (directory (:manifest-dir settings))

    (doseq [[service-name service-options] services]
      (install-service settings
                       service-name
                       service-options))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Pallet Server Specification

(defn server-spec
  [& {:keys [instance-id] :as options}]
  ;; TODO move this from :configure to :bootstrap
  (api/server-spec
   :phases {:configure (api/plan-fn (configure-plan options))}))
