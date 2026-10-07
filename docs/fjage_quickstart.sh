#!/bin/sh
set -eu

VERSION=${VERSION:-2.6.0}
case "$VERSION" in
  ''|*[!A-Za-z0-9._-]*) echo "Invalid fjage version: $VERSION" >&2; exit 1 ;;
esac

RELEASE=https://github.com/org-arl/fjage/releases/download/v$VERSION
RAW=https://raw.githubusercontent.com/org-arl/fjage/v$VERSION
MVN=https://repo.maven.apache.org/maven2
MANIFEST=fjage-$VERSION-dependencies.txt

# Download everything before updating the project.
INSTALL_DIR=$(mktemp -d .fjage-install.XXXXXX)
trap 'rm -rf "$INSTALL_DIR"' 0
trap 'exit 1' HUP INT TERM
mkdir -p "$INSTALL_DIR/build/libs" "$INSTALL_DIR/etc" "$INSTALL_DIR/samples"

if ! curl -fsSL "$RELEASE/$MANIFEST" -o "$INSTALL_DIR/$MANIFEST" 2>/dev/null; then
  # Older releases have a manifest generated from their tagged build.
  curl -fsSL "https://raw.githubusercontent.com/org-arl/fjage/master/docs/releases/$MANIFEST" -o "$INSTALL_DIR/$MANIFEST"
fi
test -s "$INSTALL_DIR/$MANIFEST"

while IFS= read -r path; do
  case "$path" in
    ''|/*|*..*|*[!A-Za-z0-9_./+-]*) echo "Invalid dependency path: $path" >&2; exit 1 ;;
    *.jar) ;;
    *) echo "Invalid dependency path: $path" >&2; exit 1 ;;
  esac
  curl -fsSL "$MVN/$path" -o "$INSTALL_DIR/build/libs/${path##*/}"
done < "$INSTALL_DIR/$MANIFEST"

curl -fsSL "$RELEASE/fjage-$VERSION.jar" -o "$INSTALL_DIR/build/libs/fjage-$VERSION.jar"
for script in initrc.groovy initrc-rconsole.groovy; do
  curl -fsSL "$RAW/etc/$script" -o "$INSTALL_DIR/etc/$script"
done
for sample in 01_hello.groovy 02_ticker.groovy 03_weatherStation.groovy 03_weatherRequest.groovy WeatherForecastReqMsg.groovy 04_weatherStation.groovy 04_weatherRequest.groovy; do
  curl -fsSL "$RAW/samples/$sample" -o "$INSTALL_DIR/samples/$sample"
done
for script in fjage.sh rconsole.sh; do
  curl -fsSL "$RAW/$script" -o "$INSTALL_DIR/$script"
  chmod +x "$INSTALL_DIR/$script"
done

# Refuse conflicting dependency versions instead of creating an ambiguous classpath.
for jar in build/libs/*.jar; do
  test -e "$jar" || continue
  if test ! -f "$INSTALL_DIR/build/libs/${jar##*/}"; then
    echo "Conflicting JAR: $jar. Install into a fresh project folder." >&2
    exit 1
  fi
done
mkdir -p build/libs etc logs samples
cp "$INSTALL_DIR/build/libs/"* build/libs/
cp "$INSTALL_DIR/etc/"* etc/
cp "$INSTALL_DIR/samples/"* samples/
cp "$INSTALL_DIR/fjage.sh" "$INSTALL_DIR/rconsole.sh" .
cp "$INSTALL_DIR/$MANIFEST" build/libs/
