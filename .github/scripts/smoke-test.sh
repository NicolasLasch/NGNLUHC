#!/usr/bin/env bash
# Starts a real Purpur 1.21.4 server with the freshly built plugin and plays a scripted game with no
# player: world generation, hidden structures (dungeon, cloud, library), the arena city, the
# arena phase and the clean-up are all exercised.  Fails on any plugin exception in the log.
set -u
ROOT="$(pwd)"
WORK="$ROOT/smoke"
rm -rf "$WORK" && mkdir -p "$WORK/plugins/NoGameNoLifeUHC" && cd "$WORK"

JAR="$(ls "$ROOT"/jar/NoGameNoLifeUHC-*.jar | head -1)"
echo "Plugin jar: $JAR"
cp "$JAR" plugins/

if [ -n "${PURPUR_JAR:-}" ]; then
  # Local run: reuse an existing server (jar + its cache/libraries/versions folders)
  cp "$PURPUR_JAR" purpur.jar
  for folder in cache libraries versions; do
    [ -d "$(dirname "$PURPUR_JAR")/$folder" ] && cp -r "$(dirname "$PURPUR_JAR")/$folder" .
  done
else
  echo "Downloading Purpur 1.21.4..."
  curl -fsSL -o purpur.jar "https://api.purpurmc.org/v2/purpur/1.21.4/latest/download" || { echo "Could not download Purpur"; exit 1; }
fi

echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
server-port=25599
view-distance=4
simulation-distance=4
spawn-protection=0
level-name=world
motd=ngnl smoke test
PROPS

# Test configuration: no player needed, small worlds, quick Aka Si Anse
cat > plugins/NoGameNoLifeUHC/config.yml <<CFG
game:
  minimum_players: 0
  episode_length: 1
  aka_si_anse_appear_time: 1
world:
  mining_world_border_size: 200
  destroy_worlds_after_game: true
resourcepack:
  send-on-join: true
  self-host:
    enabled: true
    port: 8123
    public-host: localhost
CFG

mkfifo input
( tail -f input | java -Xmx2G -jar purpur.jar --nogui > server.log 2>&1 ) &
SERVER_WRAPPER=$!

send() { echo "$1" > input; }
wait_for() {  # wait_for <regex> <seconds>
  for _ in $(seq 1 "$2"); do grep -Eq "$1" server.log && return 0; sleep 1; done
  echo "Timeout waiting for: $1"; return 1
}

fail() { echo "SMOKE TEST FAILED: $1"; tail -80 server.log; kill $SERVER_WRAPPER 2>/dev/null; pkill -f purpur.jar 2>/dev/null; exit 1; }

wait_for 'Done \(' 420 || fail "server did not start"
wait_for 'No Game No Life UHC has been enabled' 30 || fail "plugin did not enable"
grep -q 'Worlds initialized' server.log || fail "worlds were not initialized"

send "ngnl info";      sleep 3

# ---- Part 1: a real client (bot) plays: join, pack, role card, lethal hit, arena, stop ----
send "op NgnlBot"
if [ -d "$ROOT/.github/scripts/bot" ]; then
  cp -r "$ROOT/.github/scripts/bot" "$WORK/bot" && (cd "$WORK/bot" && npm install --no-audit --no-fund --silent) \
    || fail "could not install the bot (npm)"
  (cd "$WORK/bot" && node bot-test.js localhost 25599) | tee "$WORK/bot.log"
  grep -q "BOT TEST PASSED" "$WORK/bot.log" || fail "the bot scenario failed (see above)"
  sleep 10
fi

# ---- Part 2: an empty second game right after (clean restart, structures, arena) ----
send "ngnl start";     wait_for 'has begun' 240 || fail "game did not start"
sleep 10
send "ngnl forcearena"; sleep 15
grep -q 'LOVE FIGHT' server.log || fail "arena phase did not start"
send "ngnl stop";      sleep 20
send "stop"
wait_for 'No Game No Life UHC has been disabled' 90 || fail "plugin did not disable cleanly"
sleep 5
pkill -f purpur.jar 2>/dev/null

echo "---- checking the log for plugin errors ----"
if grep -E "at be\.thespattt\.ngnl|Could not pass event|Error occurred while enabling|Error occurred while disabling|Exception in thread" server.log; then
  fail "the plugin threw exceptions (see above)"
fi
echo "SMOKE TEST PASSED"
