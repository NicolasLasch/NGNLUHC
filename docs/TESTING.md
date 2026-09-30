# Testing guide

Nothing here has been run on a real server yet, so please go through these checks in order (they are
sorted from "will it even start" to "fine details"). Tick what works, note what does not (console log
+ what you did) and send it back.

## 0. Test server setup (5 min)
1. Purpur/Paper **1.21.4**, Java 21. Put the jar in `plugins/`. For a second account use `online-mode=false` on a *test* server only.
2. Fast config for testing (`/ngnl settings` or `config.yml`):
   - `minimum_players: 1` (`/ngnl settings minplayers 1`)
   - `episode_length: 1` (`/ngnl settings episodelength 1`) → roles appear after ~1 minute
   - `mining_world_border_size: 300`
3. Give yourself `/op` and watch the console while testing (`logs/latest.log`).

## 1. Startup (must pass first)
| Check | Expected |
| --- | --- |
| Start the server | No exception; logs `Worlds initialized`, `Loaded 29 roles`; worlds `ngnl_waiting`, `ngnl_minigame`, `ngnl_arena_city`, `ngnl_mining` exist |
| First start only | Log says it generates the fallback arena city (takes a few seconds) |
| Join | You are teleported onto a white/grey quartz platform in the sky (lobby), adventure mode |
| `ngnlconfig` | The configuration GUI opens |

## 2. Resource pack & role card (the new feature)
Do this with the pack **loaded** and **declined**.

| Check | Expected |
| --- | --- |
| Join with `resourcepack.self-host.enabled: true` (+ public-host/port open) or a `url` | The client shows the pack prompt "Les cartes de rôles de No Game No Life UHC" and downloads it |
| Decline the pack, `/ngnl setrole <you> SORA` (game running) | A **text card** inventory opens + chat line "Ton rôle : …" |
| Accept the pack, same test | A **card with picture** opens, centered over the window: name, faction chip, portrait, objective |
| Click the `>` button (top-right) | Card flips to the powers page; click again flips back |
| `ESC` then `/role` | The card reopens |
| Check alignment | Card not cut, no 1-px seam between tiles, colours not dark/tinted. **If it is shifted**, tell me the offset in pixels (it's two numbers in `tools/generate_resourcepack.py`: `BASELINE` and the left shift) |
| Check all roles | `/ngnl setrole` a few roles (Sora, Holou, Nonna, Jibril…): text fits, nothing overflows |

## 3. Game start, worlds, phases
| Check | Expected |
| --- | --- |
| `/ngnl start` | Everybody teleported to **different** spots of a fresh `ngnl_mining`, on dry land; border = 2× radius |
| Wait for episode 2 (1 min with the test config) | "Episode 2 has started", PvP message, roles revealed + cards |
| Kill a mob / fall / lava | You never die: at worst you stay at 1 heart + message |
| `/ngnl forcearena` | Everyone teleported to the arena (generated city), Love Gun received, finale items appear, border starts shrinking |
| `/ngnl stop` | Back to lobby, inventories cleared, **mining world deleted and a new one generated** (check the `ngnl_mining` folder timestamp) |
| Start a second game right after | Works the same (no stale roles, no leftover clones/items, scoreboard OK) |

## 4. Mini-game duel (needs 2 players)
| Check | Expected |
| --- | --- |
| Episode ≥ 2: player A hits B until B would die | B does **not** die; both are pulled into the mini-game roulette/selection |
| Finish a game | Loser loses 3 (if he won the PvP) or 5 (if he lost it) **max hearts**; winner gets a random enchanted book |
| Lose until 0 hearts | Eliminated, spectator mode, broadcast shows **only the role** (no name) |
| Floor Is Lava | Standing in lava/fire does not hurt; you are eliminated only by the rising level |
| Sumo with Ivan or Nonna | A knockback stick is given at each round start and disappears after one hit |

## 5. Roles — quick scenario per role
Use `/ngnl setrole <player> <ROLE>` (do it before episode 2, use a long `episode_length`, because episode 2 re-rolls roles) and `/ngnl forcearena` for the finale part. Priority ones first (★ = most complex / most likely to have bugs):

| Role | Test | Expected |
| --- | --- | --- |
| ★ Sora & Shiro | Both roles, go to arena, stay near/far | Near: Speed+Resistance; > 30 blocks: Weakness. Crown → 5 clones for **10 s**, hit a clone → it vanishes. Action-bar arrow to partner |
| ★ Kurami / Feel | `/duo together`, lose a mini-game | Both fall to 5 max hearts. Oracle Card: Kurami picks 2 players (heads GUI) |
| Stéphanie | Arena, right-click the golden hoe | Snowball; a hit sets the target to 6 hearts for 30 s, then restores |
| Makoto | Lose a mini-game, `/duo cancel` within 60 s | Hearts restored (even if it eliminated you) |
| Riku / Schwi | Win a mini-game as Riku; kill Schwi and survive 5 min | +1 heart; Schwi revived with both −3 hearts. `/heal` moves 2 HP |
| Izuna / Ino | Kill Izuna, Ino kills the murderer within 10 s | Izuna revived; otherwise Ino dies |
| ★ Fiel | Shard: place an illusion, sneak-click to swap. Arena: mask → pick a player | Clone appears where you look; disguise changes skin **and** name for 30 s then restores. *(Paper profile API: tell me if it breaks)* |
| Chlammy | Book → pick a player | Inventory listed in chat, once per episode |
| ★ Shi / Ku | Hit Shi | Ku takes the same damage; healing one heals both. Safety Core: nobody in the zone takes damage for 15 s |
| Ivan | Arena, wear the ID Helmet | Orange footprints of nearby players for 10 s |
| ★ Jibril | Arena, book | One of 4 catastrophes (crater and lava are restored afterwards); Holou takes no damage, Artosh double |
| Azriel | Anti-flight item near a flying Think | Flight removed in the zone |
| Corone + Ghost | Ghost hidden: Corone sees particles; Ghost kills Corone | Corone revived as ally with the Ghost item |
| Miko | Start | 12 hearts; Mechanical Eye rewinds nearby players ~10 s |
| Teto | Reveal, then arena | Picker to choose whose role to learn (Eye item reopens it); King's Piece −2 hearts + glow + no attacks; Royal Recall → choose a dead player |
| Artosh / Okein / Kainas | Arena items | Blade executes < 2 hearts and gives +1 heart; Hammer drops 5×5 anvils (no anvil block remains); Cage builds 5×5 leaves and disappears after 1 min |
| Okein | Others try flint & steel / lava bucket | Refused ("Seul Ōkein peut utiliser le feu"); water hurts Okein 1 heart/s |
| Kainas | `/forest` | Chest of plants: click = full stack |
| Holou | Sneak+right-click the Veil; `/teleport a b`; arena TP Stick | −2 hearts and deaths hidden for others; swap + random books; teleport 30 blocks from a chosen player |
| Einzig | Arena: die once | Revived with Strength and 8 hearts (no elimination) |
| Think | Arena `/rite 1`, hit someone | Flight + invisibility ends at first damage. `/alliance` refused for Think |
| Plum | Device item: place, walk other players past, sneak+click to read | Logs names / chat; 20 % "you noticed a device" |

## 6. Items, factions, worlds
| Check | Expected |
| --- | --- |
| Find the dungeon (Think knows its coordinates; or read the log) | Stone-brick room with **Nina Clive** (evoker). She throws fireballs; a lethal hit teleports you away at 5 hearts. She drops the Aka Si Anse |
| Wait for the announcement (`aka_si_anse_appear_time: 2` for a quick test) | Chat message with coordinates ± 50 |
| Aka Si Anse right-click | Heads GUI, target at 2 hearts, one use |
| Old Deus role | Receives the cloud coordinates; first god to pick the Suniaster gets 15 hearts; others cannot use it |
| Imanity `/shop` | 5 items, random prices; purchase removes the payment and gives the item; non-Imanity refused |
| Flügel | Gets the library coordinates; enchanting table works once, other players are refused |
| Werebeasts | Action-bar arrow "Une présence rôde à Xm" with another player within 40 blocks |
| Betrayal | Two players of the same faction: one kills (mini-game win) the other before the arena | Killer glows in the arena + broadcast |

## 7. Long-run / robustness (run at least once for a couple of hours with friends)
- Memory and TPS (`/spark tps` or `/tps`) after the arena starts, especially with the generated city and catastrophes.
- A player disconnects and reconnects in each phase.
- Two games in a row without restarting the server.
- `/ngnl reload` while idle; server stop during a game (mining world deletion must not crash the shutdown).

## What to send me
For each failure: what you did, the **console stack trace** (`logs/latest.log`), the role/phase, and a screenshot for anything visual (card alignment especially).
