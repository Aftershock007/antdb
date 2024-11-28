#!/bin/sh
set -e
mvn -B --quiet package -Ddir=/tmp/codecrafters-sqlite-target
exec java -jar /tmp/codecrafters-sqlite-target/java_sqlite.jar "$@"