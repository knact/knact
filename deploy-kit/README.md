# Knact guard


This package contains both the client and the server executable JAR files. 
All dependencies are packaged together with the exception of JCE signed JARs which 
are included separately in the `bcprov` directory.


## Client instruction

Simply execute the JAR by doing 

    java -jar guard-client-jfx.jar
    
To connect to a server, set the URL in the address bar to your guard server's address then 
press enter. 

## Server instruction

To use the server, prepare the following files:

 * config.json
 * targets.json

Place the two files next to the server executable, you may also specify them as CLI arguments, 
the synopsis are:

```
kanct-guard 0.0.1
Usage: knact-guard [options]

  --config <file>   configuration file path, defaults to ./config.json
  --targets <file>  targets file path, defaults to ./targets.json
```

You can use the script `run.sh` to start the server. Using `java -jar` is not advised as BouncyCastle 
is signed which means we cannot bundle it into the fat-jar.

Log files will be written to two separate files(debug.log and error.log) in `./log/`.

General configuration of the server is done through `configs.json`, for example:
```json
{
  "port" : 8080,
  "eventInterval" : "1 second",
  "commandMaxThread" : 80,  
  "serverMaxThread" : 16
}
```
The schema is defined at `io.knact.guard.server.Config`

Target of nodes can be defined in `targets.json`, for example, to monitor a single node:

```json
[
  {
    "SshPasswordTarget" : {
      "host" : "localhost",
      "port" : 22,
      "username" : "foo",
      "password" : "bar"
    }
  }
]
```
To monitor nodes that uses public key authentication:

```
[
  {
    "SshKeyTarget" : {
      "host" : "the.domain",
      "port" : 22,
      "username" : "foo",
      "keyPath" : "/absolute/path/path/to/the/key"
    }
  },
  {
    "SshKeyTarget" : {
    "host" : "another.domain",
    "port" : 22,
    "username" : "bar",
    "keyPath" : "/absolute/path/to/the/key"
  }
]
```

**NOTE: using `~` for home directory is not supported, use absolute path instead**

The schema is defined at `io.knact.guard.Entity.Target`

Changes can be made to the `targets.json` file while the server is running. The server will listen 
for file changes and reload them when required; errors will be logged but are otherwise harmless.





 
 
