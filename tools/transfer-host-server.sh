#!/usr/bin/env bash
# Runs Transfer to phone's HTTP server (protocol 2) on this machine, serving a folder laid out like a memory card
# (DCIM/100MSDCF/*.ARW and *.JPG, RECIPES/*.YML). For testing Recipe Lab Studio, or its end-to-end tests, without a
# camera. It answers as an ILCE-6000; the server code is the app's own (src/com/voxivoid/recipelab/transfer/), built
# here against the bare JDK like tools/test.sh does.
#
#   tools/transfer-host-server.sh /path/to/card [port]      # default port 8080; 0 picks a free one
#
# Prints "PORT n" once it is listening, then every request's log line. Connect by typing this machine's address in
# the phone app.
# Toolchain: JAVA_HOME pointing at a JDK 17 (as for build.sh), or javac on PATH.
set -euo pipefail
cd "$(dirname "$0")/.."
CARD="${1:?usage: tools/transfer-host-server.sh /path/to/card [port]}"
PORT="${2:-8080}"
[ -d "$CARD" ] || { echo "transfer-host-server.sh: $CARD is not a folder" >&2; exit 1; }

if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin"; else JAVA="$(dirname "$(command -v javac 2>/dev/null || echo /nonexistent/javac)")"; fi
[ -x "$JAVA/javac" ] || { echo "transfer-host-server.sh: no javac -- set JAVA_HOME to a JDK 17" >&2; exit 1; }

# the camera-free half of transfer/: everything but Hotspot and TransferMode, which need Android
SOURCES=()
for f in src/com/voxivoid/recipelab/transfer/*.java; do
  case "$f" in */Hotspot.java|*/TransferMode.java) ;; *) SOURCES+=("$f") ;; esac
done

rm -rf out/transfer-host
mkdir -p out/transfer-host
"$JAVA/javac" -encoding UTF-8 --release 8 -Xlint:-options -d out/transfer-host "${SOURCES[@]}" tools/transfer-host/TransferHostServer.java
exec "$JAVA/java" -cp out/transfer-host TransferHostServer "$CARD" "$PORT"
