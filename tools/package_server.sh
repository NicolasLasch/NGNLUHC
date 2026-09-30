#!/usr/bin/env bash
# Builds a ready-to-run server bundle: plugin jar, configuration, start scripts and the arena map.
#   tools/package_server.sh <plugin-jar> <version> [output-dir]
# Purpur itself is NOT bundled: setup.sh downloads the official build the first time.
set -euo pipefail

JAR="$1"
VERSION="$2"
OUT="${3:-dist}"
NAME="NoGameNoLifeUHC-server-$VERSION"
DIR="$OUT/$NAME"

rm -rf "$DIR" && mkdir -p "$DIR/plugins/NoGameNoLifeUHC/maps"
cp "$JAR" "$DIR/plugins/"

# Default configuration and the optional arena map(s)
cp src/main/resources/config.yml "$DIR/plugins/NoGameNoLifeUHC/config.yml"
if compgen -G "maps/*.zip" > /dev/null; then
  cp maps/*.zip "$DIR/plugins/NoGameNoLifeUHC/maps/"
fi
if compgen -G "src/main/resources/maps/*.zip" > /dev/null; then
  echo "Arena map is already embedded in the plugin jar."
fi

cat > "$DIR/server.properties" <<PROPS
motd=No Game No Life UHC
difficulty=hard
spawn-protection=0
view-distance=8
simulation-distance=6
allow-flight=true
white-list=false
enforce-whitelist=false
PROPS
echo "eula=false" > "$DIR/eula.txt"

cat > "$DIR/setup.sh" <<'SCRIPT'
#!/usr/bin/env bash
# Downloads Purpur 1.21.4 (Java 21 required) and accepts nothing on your behalf: edit eula.txt yourself.
set -e
cd "$(dirname "$0")"
if [ ! -f purpur.jar ]; then
  echo "Downloading Purpur 1.21.4..."
  curl -fL -o purpur.jar "https://api.purpurmc.org/v2/purpur/1.21.4/latest/download"
fi
echo "Done. 1) set eula=true in eula.txt (you agree to https://aka.ms/MinecraftEULA)  2) ./start.sh"
SCRIPT

cat > "$DIR/start.sh" <<'SCRIPT'
#!/usr/bin/env bash
cd "$(dirname "$0")"
[ -f purpur.jar ] || ./setup.sh
exec java -Xms4G -Xmx6G -jar purpur.jar --nogui
SCRIPT

cat > "$DIR/start.bat" <<'SCRIPT'
@echo off
cd /d %~dp0
if not exist purpur.jar (
  powershell -Command "Invoke-WebRequest -Uri https://api.purpurmc.org/v2/purpur/1.21.4/latest/download -OutFile purpur.jar"
)
java -Xms4G -Xmx6G -jar purpur.jar --nogui
pause
SCRIPT
chmod +x "$DIR/setup.sh" "$DIR/start.sh"

cp docs/SERVER_SETUP.md "$DIR/README-SERVER.md"
(cd "$OUT" && zip -qr "$NAME.zip" "$NAME")
echo "Created $OUT/$NAME.zip"
