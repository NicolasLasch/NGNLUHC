# Server setup

## What you need
* **Java 21** and a **Purpur (or Paper) 1.21.4** server. 4–6 GB RAM for ~16 players.
* The release files from the [Releases](../../releases) page:
  * `NoGameNoLifeUHC-<version>-server.zip` — a **ready-to-run server folder** (easiest), or
  * `NoGameNoLifeUHC-<version>.jar` — the plugin alone, if you already have a server.

## Option 1 — ready-to-run bundle
1. Unzip `NoGameNoLifeUHC-server-<version>.zip` anywhere.
2. Run `./setup.sh` (Linux/macOS) — it downloads the official Purpur 1.21.4 — or just `start.bat` on Windows.
3. Open `eula.txt` and set `eula=true` (you accept <https://aka.ms/MinecraftEULA>).
4. Start with `./start.sh` / `start.bat`.
5. Decide how players get the card pack: see [Role cards pack](#role-cards-resource-pack).
6. Join, `/op` yourself from the console, then `/ngnl start` once there are enough players.

## Option 2 — add the plugin to an existing server
Drop `NoGameNoLifeUHC-<version>.jar` in `plugins/`, start the server once, stop it, edit
`plugins/NoGameNoLifeUHC/config.yml`, start again.

## The arena map
The final "Love Fight" arena is a custom map.

* **Bundled in the plugin**: if the release you use embeds the map (`src/main/resources/maps/ngnl_arena_city.zip`), nothing to do.
* **Next to the plugin** (any release): put a zip named `ngnl_arena_city.zip` in `plugins/NoGameNoLifeUHC/maps/`.
  The plugin extracts it on the first start when no `ngnl_arena_city` world folder exists yet.
* **As a world folder**: copy your map folder to the server root and name it `ngnl_arena_city`.
* **No map**: a city of towers is generated automatically so the game is playable.

How to make the zip: zip the world folder of your arena (`level.dat`, `region/`, …). Files at the root of the zip or inside a single folder both work. The name (without `.zip`) must equal `arena.world-name` in `config.yml`.

> The arena map of the original author is **not** part of the repository: add your zip to `maps/` (it is then packaged in the server bundle) or to `src/main/resources/maps/` (embedded in the jar), see [maps/README.md](../maps/README.md).

## Role cards resource pack
Choose one:
1. **Built-in host** — in `config.yml`: `resourcepack.self-host.enabled: true`, `public-host: <your public IP or domain>`, open the TCP port `8123` on your firewall/router.
2. **Your own URL** — upload `NGNL-ResourcePack.zip` (release asset) somewhere with a direct link and set `resourcepack.url` (and `resourcepack.sha1`).
Without the pack the cards are shown as text.

## First checks
* The console shows `No Game No Life UHC has been enabled!` and `Worlds initialized`.
* Joining puts you on a quartz platform (lobby).
* Follow [docs/TESTING.md](TESTING.md).

## Updating
Stop the server, replace the jar in `plugins/`, keep `config.yml` (new options use their defaults), start.
