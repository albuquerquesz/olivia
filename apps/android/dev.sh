#!/bin/sh
# Mock flavor: JDK 17/21, assemble, install on a connected device, rebuild on change.
set -eu
cd "$(dirname "$0")"
exec ./with-jdk.sh ./gradlew installMockDebugAdb --continuous
