#!/bin/sh
set -eu
cp -R /source/. /workspace/
cd /workspace
sh gradlew --no-daemon assembleRelease bundleRelease lint
cp app/build/outputs/apk/release/app-release-unsigned.apk /artifacts/nine-feelings-linux-unsigned.apk
cp app/build/outputs/bundle/release/app-release.aab /artifacts/nine-feelings-linux-unsigned.aab
cp -R app/build/reports /artifacts/reports
