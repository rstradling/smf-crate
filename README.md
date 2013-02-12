# smf-crate

A Clojure library designed to work with pallet to create an smf file.  It is specifically used with smartos but hopefully will work for other Solaris based OSes as well.  It is inspired by manifold.

## Usage

```clojure
   (create-smf "<category>" "<name of service>" "<version>" "run command" "user" "group") 
  ;; Please see the source code for options that can be passed in and overridden
```

To compile the live-test sample you will need to do the following...
* Create a local smartos image where root can log in to it.
* Update your ~/pallet/config.clj to have the 
```clojure
{:datacenter {:provider "node-list"
	:node-list [["<machine-name>" "smartos-testing" "<ip address> :smartos]]
        }
}
```
run the tests by typing in 
lein with-profile live-test test :live-test


FIXME

## License

Copyright © 2013 Ryan Stradling

Distributed under the Eclipse Public License, the same as Clojure.
