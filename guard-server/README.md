# Knact Guard server


This module contains a self-contained guard server ready to record node telemetries.

The guard server is responsible for

 * Monitoring and collecting node telemetries
 * Telemetry persistence
 * Telemetry Retrieval via REST endpoints


## How to build

To create a executable jar, start`sbt` then run:

    sbt> project guard-server
    sbt:guard-server> assembly

## Instructions

Instructions of use are included in the top-level `deploy-kit` directory.


