# smf-crate

A Clojure library designed to work with pallet to create an smf file.  It is specifically used with smartos but hopefully will work for other Solaris based OSes as well.  It is inspired by manifold.

## Continuous Integration Status
[![Build Status](https://travis-ci.org/rstradling/smf-crate.png)](https://travis-ci.org/rstradling/smf-crate])

## Usage
Artifacts are [released to Clojars](https://clojars.org/strad/smf-crate).  If you are using Maven, add the following definition to your `pom.xml`:
```xml
<repository>
 <id>clojars.org</id>
 <url>http://clojars.org/repo</url>
</repository>
```

### The Most Recent Release
With Leiningen
```clojure
   [org.clojars.strad/smf-crate "0.1.0"]
```

With Maven
```xml
   <dependency>
      <groupId>org.clojars.strad</groupId>
      <artifactId>smf-crate</artifactId>
      <version>0.1.0</version>
   </dependency>
```

```clojure
  (def smf-data (create-smf "<category>" "<name of service>" "<version>" "run command" "user" "group"))
  (install-smf-service session smf-data "<remote location of smf file>" <true|false>) 
  ;; Please see the source code for options that can be passed in and overridden
  ;; for smf-data.
  ;; Also note that the true or false parameter for install-smf-service 
  ;; represents whether to remove the
  ;; temporary file on exit of the application or not.
```

### Live test
To run the live-test sample you will need to do the following...
* Create a local smartos image where root can log in to it.
* Update your ~/pallet/config.clj to have the following 

```clojure
{:datacenter {:provider "node-list"
	:node-list [["<machine-name>" "smartos-testing" 
			"<ip address>" :smartos]]
        }
}
```
* Run the test with
```
lein with-profile live-test test :live-test
```

## License

Copyright © 2013 Ryan Stradling

Distributed under the Eclipse Public License, the same as Clojure.
