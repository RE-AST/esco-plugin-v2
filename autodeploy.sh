#!/bin/bash

set -e

./gradlew jar

COMMIT=$(git rev-parse --short HEAD)
echo "Commit: $COMMIT"

REMOTE_PATH="/www/builds/$COMMIT"

ssh hostn "mkdir -p $REMOTE_PATH"

scp build/libs/esco-plugin-v2.jar hostn:$REMOTE_PATH/plugin.jar

ssh hostn "echo $COMMIT > /www/builds/latest.txt"

echo "Done: $REMOTE_PATH (latest = $COMMIT)"