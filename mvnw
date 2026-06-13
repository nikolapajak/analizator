#!/bin/sh
# Minimal Maven Wrapper – pobiera Maven przy pierwszym uruchomieniu
set -e
MAVEN_VERSION="3.9.9"
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
MVN="$MAVEN_HOME/bin/mvn"

if [ ! -f "$MVN" ]; then
  echo "Pobieranie Apache Maven ${MAVEN_VERSION}..."
  mkdir -p "$MAVEN_HOME"
  URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  curl -sL "$URL" | tar -xz -C "$HOME/.m2/wrapper/dists/" \
    && mv "$HOME/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}" "$MAVEN_HOME" 2>/dev/null || true
fi

exec "$MVN" "$@"
