Knact
=====

Periodic data sampling made easy!

## Project structure

The project is made up two primary parts: the core library and a guard server for monitoring large 
numbers of node(computer). 

For the core library:

* `core` - the core library with periodic sampling mechanism and interfaces for transport/node
* `core-ssh-transport` - a ssh transport implementation
* `core-linux-perf` - a collection of linux performance command implementations
* `core-sample` - samples for the core library

For the guard server/client:

* `guard-server` - a REST API server with node history/health management
* `guard-client-javafx` - JavaFX based GUI client for the server
* `guard-client-cli` - commandline client for the server
* `guard-client-web` - web based client for the server

See README files in the respective project directory for more details.

## How to build

A sbt launcher script has been bundled so installation of sbt is not strictly required.

In project root, simply run

    ./sbt #*nix
    sbt   #Windows

sbt should start and enter interactive mode, proceed to build project with `assembly`

Upon completion, the directory `deploy-kit` at project root will contain all the required files. 




