#!/bin/sh
java -cp "guard-server.jar:bcprov/*" io.knact.guard.server.Main "$@"
