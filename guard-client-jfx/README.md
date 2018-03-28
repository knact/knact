# Knact Guard JavaFX client

This module contains a self-contained JavaFX interface for accessing the guard-sever via the REST 
API.


## How to build

To create a executable jar, start`sbt` then run:

    sbt> project guard-client-jfx
    sbt:guard-client-jfx> assembly
    
    
## Setup
 
To use the client, run:

    java -jar knact-client-jfx.jar 
    
Select the address bar on top and enter your server's address then press `ENTER`, you may reconnect
or connect to a different server by pressing `ENTER` again while focusing on the address bar.

You may switch between three views: `Node`, `Guard status`, and `Client log`:

**Node** 

The node view gives you a overview of all the nodes currently being monitored by the guard server.

**Guard status**

The guard status view gives you visualised health status of the guard server itself

**Client log**

The Client log gives you general logs created within the client