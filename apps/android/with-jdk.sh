#!/bin/sh
# Gradle 8.14's Kotlin DSL cannot parse Java 26's version string (26.0.2.1).
# AGP 8.13 also expects JDK 17 or 21 to run the daemon.
set -eu

is_supported_jdk() {
  home=$1
  [ -x "$home/bin/java" ] || return 1
  ver=$("$home/bin/java" -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')
  case "$ver" in
    17|17.*|21|21.*) return 0 ;;
    *) return 1 ;;
  esac
}

if [ -z "${JAVA_HOME:-}" ] || ! is_supported_jdk "$JAVA_HOME"; then
  JAVA_HOME=""
  for candidate in \
    /usr/lib/jvm/java-21-openjdk \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-17-openjdk \
    /usr/lib/jvm/java-17-openjdk-amd64 \
    /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
    /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
    /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
  do
    if is_supported_jdk "$candidate"; then
      JAVA_HOME=$candidate
      break
    fi
  done
fi

if [ -z "${JAVA_HOME:-}" ]; then
  echo "Android Gradle needs JDK 17 or 21. The default java (often 26) cannot compile this build." >&2
  echo "Install JDK 21 and set JAVA_HOME." >&2
  exit 1
fi

export JAVA_HOME
exec "$@"
