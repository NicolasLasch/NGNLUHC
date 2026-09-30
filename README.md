# No Game No Life UHC

Welcome to **No Game No Life UHC**, a custom Minecraft Ultra Hardcore plugin inspired by the hit anime and movie *No Game No Life*. In this world, bloodshed is restricted, and everything is decided by games!

This plugin features a robust phase system, 14 unique mini-games, complex duo and solo roles, and a custom final Arena phase.

Google Doc (In French) : https://docs.google.com/document/d/1blBPdmT5l3AH29eRe4WNXCGbC-yFB59FCwmSEmxozhI/edit?usp=sharing

## 🎮 Game Flow & Phases

The game is divided into distinct episodes and phases, managed by the `EpisodeManager` and `GameManager`.

* **Waiting / Starting:** Players gather in the lobby before being scattered into the mining world.
* **Mining Phase:** Players gather resources and prepare. The game operates on an episodic timer. PvP is disabled initially but automatically enables on Episode 2 (configurable). Roles and factions are also assigned during Episode 2.
* **Mini-Games:** During the game, players will be pulled into mini-games against each other. Winning grants advantages, while losing strips away maximum health.
* **Arena Phase (LOVE FIGHT):** Once the player count drops to a specific threshold, the game shifts to the Arena Phase.
  * Players are teleported to a custom Arena World where the border actively shrinks.
  * Traditional weapons are disabled.
  * Players are given custom "Love Guns" to fight with.
  * Players unlock their powerful "Finale" role items and abilities.
* **Ended:** The game concludes when one player or a winning alliance/duo remains.

## 🎲 The Mini-Games

The plugin features a massive suite of 14 custom mini-games. Players' roles often give them specific advantages or disadvantages depending on the game played.

**Available Mini-Games:**

* Spleef & Splegg
* TNT Run
* Parkour
* Bloc Party
* Sumo
* Floor is Lava
* Des a Coudre
* Anvil Rain
* Word Chain Battle
* Logical Deduction
* Mental Chess
* Memory Game
* Materialization Shiritori

## 🎭 Factions & Characters (Roles)

Players are assigned roles tied to specific factions (Imanity, Flugel, Werebeasts, Ex-Machina, Elves, Old Deus, or Other). Roles are revealed in a popup **role card** (see below); reopen it any time with `/role`. Roles dictate your win conditions, mini-game modifiers, and Arena Phase abilities.

### 👯 Duo Roles

These roles must work together to win.

* **Sora & Shiro:** Shiro knows Sora's identity and can grant him Speed and Luck buffs during mini-games. Sora can ask Shiro to substitute for him in mini-games. In the Arena, both receive a Crown that summons 5 clones. They gain buffs when close together but suffer Weakness when separated.
* **Stephanie & Makoto:** Stephanie can choose the next mini-game upon winning PvP, gets Speed in Parkour/Floor is Lava, and wields the LOVE GUN 2 in the Arena (restricts targets to 12 max HP). Makoto can cancel a mini-game defeat once per game and automatically allies with the top mini-game winner if Stephanie dies.
* **Riku & Schwi:** Schwi gets permanent Speed/Jump Boost in mini-games, but dies instantly if Riku dies. Riku gains +1 maximum heart per mini-game win and can transfer his health to Schwi. If Schwi dies, Riku can revive her by surviving for 5 minutes.
* **Izuna & Ino:** Izuna inflicts Blindness on opponents in Bloc Party. Ino inflicts Slowness in TNT Run/Splegg and receives the Hatsuse Shield to protect Izuna. If Izuna dies, Ino has 10 seconds to kill her murderer or he dies too.
* **Feel & Kurami:** Can link their fates so a mini-game loss applies to both. Feel negates a loss in Anvil Rain/Floor is Lava. Kurami negates a loss in Bloc Party/Des a Coudre. In the Arena, they receive Oracle Cards for teleportation.
* **Chlammy & Fiel:** Chlammy can read nearby players' inventories and uses a Prediction Orb (Slowness) in the Arena. Fiel can disrupt players with an Illusion Shard (Blindness/Nausea) and gets an Illusion Mask (Invisibility/Speed).
* **Shi & Ku:** Their health pools are permanently synchronized. Shi has a Safety Core (Resistance). Ku has a Repair Pulse (repairs Shi's gear) and passively detects nearby players carrying special role items.
* **Ivan & Nonna:** Both receive a Knockback stick during Sumo. Ivan has an ID Helmet to see footprints. Nonna has an Oracle Card to teleport players and can join Corone or Riku's camp if Ivan dies.

### 👤 Solo Roles

These roles win alone or by forming alliances via the `/alliance` command.

* **Azriel:** Detects nearby Flugels. Once per episode, can copy a fragment of Flugel power (Speed/Regen). Uses an Anti-Flight Zone in the Arena.
* **Jibril:** Possesses permanent natural regeneration. In the Arena, she wields the Book of 18 Wings to summon Catastrophes (Nuclear Explosions, Lava Cascades, Craters, or Radiation).
* **Okein:** Discovers the role of the first player to take damage. Starts with 200 XP levels, permanent Fire Resistance, and an Anvil. Uses the Hammer of Destruction to summon Anvil Rain in the Arena.
* **Artosh:** Learns Schwi's identity (with a 40% chance of receiving false info). Kills grant Strength for 5 minutes. Wields the Blade of Infinity to execute enemies under 2 hearts.
* **Miko:** Knows Riku's identity and permanently has 12 hearts. Uses the Mechanical Eye in the Arena to rewind nearby players' locations by 10 seconds.
* **Plum:** Thrives in the dark, giving the Glowing effect to nearby players when light levels are low. Uses Listening Devices to record nearby player names.
* **Einzig:** Knows Sora's identity. Starts with only 10 hearts and Weakness. If he dies, he revives with 8 hearts and Strength. Automatically allies with Sora if he kills Riku.
* **Teto:** Knows one random player's role at the start. Always controls mini-game selection. Wields the King's Piece (10 seconds of invincibility at the cost of health/position reveal) and Royal Recall (revives an eliminated player as an ally).
* **Think Nirvalen:** Gains 1 extra heart per kill. Uses powerful commands in the Arena: `/rite 1` (Invisibility/Flight) and `/rite 2` (Levitation on a nearby enemy).
* **Kainas:** Learns the identity of the first player to gain Regeneration. Can use `/forest` for endless building supplies and creates a Vegetal Cage (buffs self, slows enemies) in the Arena.
* **Holou:** Can use `/teleport <p1> <p2>` once to force-swap the locations of two players. Uses a TP Stick in the Arena for short-range teleportation.
* **179 Ghost:** Knows a mixed identity list containing Riku, Corone, and Schwi. In the Arena, can toggle "Ghost Form" (Invisibility, Speed II, Slow Falling) but cannot attack while hidden.
* **Corone:** Knows Riku's identity and has natural regeneration. Her compass actively tracks 179 Ghost during the Arena phase.

## ⚙️ Core Systems

* **Clone Manager:** dependency-free decoys (armor stands wearing the player's head and armor) that wander around their owner. Used by the Sora/Shiro/Izuna crowns and by Fiel's illusion (one decoy placed where she looks, swappable once per game). Hitting a clone makes it vanish.
* **Dynamic Scoreboard:** A smart, rotating scoreboard displays the current Game State, Episode, Timer, Border Size, and the player's Role/Faction/Alliance. It also periodically cycles to show a list of all currently active roles in the match.
* **Config GUIManager:** A fully loaded config system allows server admins to tweak Episode lengths, Border sizes, Mini-Game damage values, and toggle specific roles/mini-games directly from configuration files.

## 🃏 Role cards & resource pack

When roles are revealed, every player gets a popup card (picture + explanation, two pages: front and powers).
Close it with ESC and reopen it with `/role`.

* The pictures come from a resource pack: the card is cut in 2x2 tiles of 256 px and drawn by the inventory title through a custom font (no mod, no shader).
* The pack is bundled in the jar (`resourcepack/NGNL-ResourcePack.zip`). Configure how players get it in `config.yml` (`resourcepack`): either your own URL (+ `sha1`), or `self-host.enabled: true` to let the plugin serve it (open the TCP port and set `public-host`).
* Players without the pack get a plain text card.
* **Regenerate / customise**: `pip install pillow && python3 tools/generate_resourcepack.py`. Drop portraits named after the role (`tools/portraits/SORA.png`, `SHIRO.png`...) to replace the generated placeholder pictures, edit the texts in `tools/cards_data.py`, use `--size 1024` for sharper cards.

## 🌍 Worlds

| World | Purpose |
| --- | --- |
| `ngnl_waiting` | Lobby (a quartz platform is generated automatically) |
| `ngnl_mining` | Qualification world, border = `world.mining_world_border_size`; deleted and regenerated after each game when `destroy_worlds_after_game` is true (optional fixed `world.generation.mining_seed`) |
| `ngnl_minigame` | Void world hosting the mini-game rooms |
| `ngnl_arena_city` | Final "Love Fight" arena. Put your own map in this folder; otherwise a city of towers is generated on the first start |

## 🧿 Special items

* **Aka Si Anse**: hidden in a dungeon guarded by **Nina Clive** (fragile but powerful; she cannot kill: a lethal hit teleports you away with 5 hearts). Approximate coordinates are announced between 50 and 80 minutes (`game.aka_si_anse_appear_time` ± ). Right-click opens a target menu (one use). Think Nirvalen knows the exact position.
* **Suniaster**: on a cloud near the spawn. The first Old Deus to take it gets 15 hearts; Old Deus know the exact coordinates.
* **Blood Destruction Bomb, Elf Runes, Imanity Crown, Ex-Machina Core, Old Deus Fragment**: sold in the Imanity shop (`/shop`, random prices in emeralds/gold drawn every game).

## ⚙️ Building

`./gradlew build` (copies the jar to `server/plugins`). Targets Purpur/Paper 1.21.4. No external plugin is required.
