# HunterBuddy

> **Every line in this repository was written by us, or comes from open-source projects whose licenses we actually read. If you came here looking for your code: it isn't here. Take your time though, reading it is free. That's how open source works.**

A Meteor Client addon for hunting, flight, visuals, inventory logistics, and utility tools.

Created by **Texy** and **DragonFood**, for their own 2b2t hunting. Texy started it to get off FutureClient and
RusherHack and build everything on Meteor instead — that mostly worked out; the only piece of either one still
around is code that ended up ported into this addon rather than run from its original client (RusherHack's elytra
flight shape in `elytra-auto-fly`, Future's totem swap in `f-totem`). mlep's code was the base a lot of the rest
grew from, and bits of Jeff mod's code went in without much hesitation where they were useful. Have
fun with it, and open a GitHub issue if you hit a problem.

Target: **Minecraft 1.21.11 only** (Yarn `1.21.11+build.3`, Fabric Loader `0.18.2`, Meteor `1.21.11-SNAPSHOT`).

## Categories and modules

Only modules registered by `HunterBuddyAddon` are listed below. Source files that are present but deliberately not registered are not documented as available modules.

### Hunt (16)

Stash hunting, trail and waypoint travel, elytra flight, and world reconnaissance.

| Meteor name | Description |
|---|---|
| `300kmfly` | Fast firework flight on a fixed heading, alternating dive and climb legs (or a level mode) inside an altitude band. `boost-mode` picks WINDOW (fastest speed Grim accepts, capped by `speed-cap`) or ROCKET_BOOST. It takes off, relights rockets in time and slows near terrain. |
| `arealoader` | Loads chunks by flying a rectangle, spiral, zigzag or circle route, with Pitch40 or RocketFly in the Overworld (`overworld-flight-mode`) and Baritone's elytra pathing in the Nether. Routes can be saved and resumed, and `disconnect-on-completion` logs you out at the end. |
| `auto-portal` | Builds a nether portal frame with obsidian, lights it, and highlights it as it goes up. With `baritone-path-to-portal` it then walks you into the portal. |
| `chunk-radar` | Follows the 1.12 trail from the blocks in each arriving chunk, fits a line through the hits, and tells you when the trail is acquired, lost, turning or a stash. Remembers trails between sessions, marks them on Xaero, and can alert Discord. |
| `elytra-auto-fly` | Elytra flight cycle: Pitch40 climb up to `max-altitude`, then a manual-pitch descent (`descend-pitch`) with optional auto fireworks until `min-altitude`, looped. |
| `ElytraBounce` | Bounce-style elytra travel with speed and rotation locks, a highway obstacle passer and portal-trap avoidance. `solve-pitch` picks the fastest bounce angle for the ceiling above you. `chestplate-fakefly` keeps a chest piece worn and swaps the elytra in only for take-off. |
| `ElytraRecast` | Flight recovery fallback: when you stop flying or drop below `min-altitude`, it jumps, reopens the elytra and climbs back with rockets. It can switch itself off when you run out of rockets. |
| `heading-lock` | Holds your yaw on the nearest multiple of 45 degrees. `2b2t-bypass` adds a tiny random wobble so the yaw is never perfectly constant. |
| `old-chunk-notifier` | Detects 1.12 and/or 1.19+ old chunks (`chunk-type`) in chosen dimensions and sends a Discord webhook, an Xaero marker, or both. Only groups of at least `min-cluster-size` adjacent chunks count, and it can ping you. |
| `Pitch40` | Pitch40 helper: syncs bounds with Meteor's ElytraFly and turns Pitch40 back on after a reconnect. `chest-swap` glides with a chest piece worn and swaps the elytra in only when needed. |
| `pitch40-classic` | Standalone elytra pitch oscillation between `lower-bound` and `upper-bound` (CLASSIC +40/-40 or EFFICIENT angles) with its own auto-firework. It does not use Meteor's ElytraFly. |
| `RocketFly` | Keeps level flight with fireworks used every `timed-delay` ms, at a fixed Y level. It can climb over terrain ahead (`obstacle-lookahead`) and take off from the ground. |
| `stash-finder` | Finds player stashes by counting containers, signs, item frames, stasis pearls and more per chunk, with per-type minimums and filters for generated structures. Alerts by chat, tracer, Xaero waypoint or Discord, and can hide coordinates. |
| `TrailFollower` | Follows detected trails in all dimensions. `heading-turn-limit` limits turning, `ignore-behind` drops chunks far behind you, and `lost-trail-scan` (weave, spin or hold) searches when the trail runs out. |
| `visual-range-notifier` | Alerts when players enter or leave visual range, when chosen items lie on the ground, and on nearby end crystal or respawn anchor activity. Sends to chat, sound or Discord, which can also report your death and disconnects. |
| `waypoint-follower` | Follows Xaero waypoints whose name starts with `waypoint-prefix` (`Hunt_` by default), in order or closest first, with a flight mode per dimension. At the end it stops, switches off or leaves the server. `seed-route` can lay the waypoints along a mapped trail for you. |

### Visuals (9)

ESP, overlays, and render helpers.

| Meteor name | Description |
|---|---|
| `cave-air` | Highlights portal-shaped holes of air inside cave air, a sign of a broken nether portal, with a beacon line and an optional sound. Scans per dimension between `min-y` and `max-y`; `strict-portal-shape` and the max portal size tune false positives. |
| `entity-view` | Rescales mobs and players, shows their gear above them through walls, names dropped items on the ground, and outlines nether portals with a tracer to the nearest one. |
| `FlightTrail` | A light two-trail wake behind the elytra while gliding, with speed-reactive flames at the wingtips, instead of dbrighthd's heavier particle trail. |
| `flow-esp` | Marks chunks where lava (in the Nether) or water (in the Overworld) is still flowing, a hint that something was built or changed there. Colors can fade from fresh to mature flows; `render-distance` and the size limits tune it. |
| `rotation-detector` | Highlights logs, basalt, purpur pillars, hay bales and deepslate lying sideways, since in nature they only generate upright. Block types, `min-y`/`max-y` and `ignore-near-air` (skips surface builds) are configurable. |
| `shader` | Draws a crisp outline, an optional glow and an optional fill around entities (players, mobs, items, pearls...), nether portals, storage blocks and spawners, each with its own color. `outline-width`, `glow` and `filled` set the look. |
| `sign-reader` | Shows the text of nearby signs on screen, through walls, grouping the ones that overlap. |
| `SpawnerDetector` | Finds classic monster-room dungeons (with or without spawner), activated trial spawners and shipwrecks, and scores the player traces around each room. With `auto-farm` it flies to a traced room (`auto-farm-all`: any room with a chest), digs down, mines the spawner, loots the chests and climbs out. Runs need at least `auto-min-health` and `auto-min-rockets`. |
| `VanityESP` | Highlights map and shulker item frames, banners, ominous vaults, buried treasure, endermen holding blocks, XP orbs and stacked chest minecarts. Each type has its own colors and tracers; buried treasure can alert in chat and add temporary waypoints. |

### Logistics (6)

Restocking and inventory handling tools.

| Meteor name | Description |
|---|---|
| `AutoFlyingRegear` | Lands on solid ground, never over lava, builds a walled box and restocks rockets, elytras, golden apples, totems and ender chests from an ender chest when one runs short (`min-rockets`, `min-elytras`...). When supplies run out (`out-of-supplies`) it stays in the box and switches modules off, or leaves the server. |
| `elytra-keeper` | Keeps a working elytra on: swaps in a fresh one when it wears out, puts one on a bare chest, and can wear a chestplate while you are hit or when another module asks for it. |
| `hotbar-refill` | Tops up hotbar stacks from your inventory before they run out, by shift-click or by pick-up and place. |
| `pearl-butler` | Walks back and forth to stay online, and loads an ender pearl stasis, by trapdoor or by walking to a spot, when a chat keyword arrives. |
| `shulker-peek` | Draws the main item of each shulker box over its icon, in inventories and on the hotbar, with a mark when the box holds several kinds of items. |
| `shulker-sorter` | Sorts the shulkers and loose items from your input chests into category chests, walking between them with Baritone. Register chests with `tag-key` and the buttons in its settings; `dry-run` reports where items would go without moving them. It pauses while another player is in sight. |

### Utility (16)

| Meteor name | Description |
|---|---|
| `auto-eat-sync` | Eats golden apples without pausing Baritone, in a safe window while flying, fighting or regearing. Below `emergency-health`, or when health drops fast, it eats first. |
| `auto-exp-plus` | Repairs armor and tools with experience bottles, restocking bottles into a chosen hotbar slot. Can swap out a worn elytra once it is repaired enough. |
| `auto-log-plus` | Disconnects you on chosen triggers: low health (with a totem check), totem pop, lethal crystal, low armor or elytra, a Y level, portals, thunderstorms, nearby players or mobs, or a frozen server. |
| `client-side-time` | Sets the displayed client-side time of day; server time and mob spawning are unaffected. |
| `Experience Trader Starter` | Switches Villager Trading on once per in-game day, at the time you choose (13:00 by default), with a fresh list of clerics to visit. |
| `f-totem` | Keeps a totem in your off hand, replaced the moment it is used. |
| `h-mine` | Breaks blocks with packets, faster than holding the button. Click several blocks to queue them (`queue`, `queue-through` lines up a row), `instant` re-breaks a cleared spot, `double-break` works two blocks at once, `auto-mine` clears around enemies and `anti-crawl` stands you up. |
| `inventory-cleaner` | Throws out every item not on its list (or only the listed ones in blacklist mode), one stack every `delay` ticks. The hotbar is optional; works in the inventory, a crafting table or a container. |
| `kick-alarm` | Plays a sound when you get disconnected from a server: kicked, timed out or the connection failed. |
| `kill-aura-plus` | Attacks the entities you choose with the weapon that hurts them most, timed to the attack cooldown for full damage. Has target filters, `only-crits`, rotation options and pauses while eating or mining. |
| `messenger` | Moves private messages into small movable glass windows over the chat, one per player, with an unread counter button. Replies go out through `reply-command` (`msg` by default), spaced by `send-interval`. `hide-from-main-chat`, `friends-only`, `ignored-names` and `stream-mode` keep the chat clean, and `keep-history` brings conversations back next session. |
| `mlep-air-place` | Places a block in air where your crosshair is pointing. |
| `mlep-scaffold` | Places blocks under you as you move, ahead of your motion, with `tower`, `air-place` and `safewalk` options. |
| `NoJumpDelay` | Removes the delay between jumps. |
| `rocket-boost` | Extends each firework's boost window with a fixed or automatic `speed-multiplier`, and can brake near terrain. Can trace every boosted tick to a CSV for tuning. |
| `Villager Trading` | Buys experience bottles from clerics with emeralds, visiting each cleric in range with Baritone while the price stays under `experience-max-price`. Restocks emeralds from a chest, drops bottles in another, then switches off and walks home. |

## Commands (4)

Commands use the configured Meteor command prefix.

| Command | Description |
|---|---|
| `arealoaderreset` | Delete every AreaLoader saved profile, not just the selected one. |
| `messenger` | Open a conversation (`open <name>`), clear every conversation (`clear`), or `ignore` and `unignore` a player. |
| `sorter` | Setup and withdrawal for the shulker sorter: `input`, `dest`, `reserve`, `station`, `remove`, `list`, `get`, `categories`, `check`, `reload`, `resetfull`. |
| `trails` | List, restore, forget one or clear the trails chunk-radar remembers. |

## HUD elements (24)

All HUD elements are registered in the `HunterBuddy` HUD group.

| HUD name | Description |
|---|---|
| `ChunkRadar` | Top-down view of the 1.12 trail the chunk-radar module is following. |
| `elytra-helper` | Central flight panel: elytra durability, flight time estimates, session stats and hunt module status. |
| `ElytraStatus` | Compact one-line elytra status: speed, pitch, rockets, durability. |
| `FindsTicker` | Recent finds and sightings as a fading feed. |
| `FollowerHeading` | Heading tape for the waypoint follower: the target as a sliding diamond, the next turns as ghosts. |
| `FollowerRoute` | The follower's route as a line of milestones, with a halo on the current one and +N for the rest. |
| `HuntTally` | Session tally: portals, ender chests, shulkers, rockets, stashes, blocks. |
| `MessengerBubble` | Messenger button with three shapes, three drawn icons, Flat or Glass finishes and a colored unread badge. Click it in chat to open contacts. |
| `movement-status` | Current sneaking and sprinting status. |
| `Odometer` | Session distance on mechanical drums, lifetime total below. |
| `Performance` | FPS, ping and server TPS in one line, colored by level and flashing on spikes and drops. |
| `PingMeter` | Ping with sparkline, jitter and keepalive freshness. |
| `Pitch40Cycle` | The pitch40 climb/dive cycle as a wave, with your position on it. |
| `RegearStatus` | Regear progress: station on its line, stocks and gains, counters, next-regear estimate and last event. |
| `SorterStatus` | Shulker sorter state, target, input progress, deliveries, shulkers made and alerts. |
| `SpawnerFarm` | SpawnerDetector auto-farm tally with item icons: spawners, chests, shulkers, rooms and the bot's state. |
| `SpawnerRun` | Current step of the spawner farm run while SpawnerDetector is on, as a console log, card, single line or timeline. |
| `SpeedSpectrum` | Equalizer bars driven by your speed, spiking on rocket boosts. |
| `SpiralCoverage` | AreaLoader coverage in any mode: flown legs, current leg, planned turns. |
| `SystemStats` | RAM and CPU usage with line graphs. |
| `ThreatBoard` | Players in range: distance, approach vector, gear summary. |
| `TimerGauge` | Meteor Timer's current speed multiplier, including speeds forced by other modules. |
| `TpsCardiogram` | Server TPS as a scrolling cardiogram, flatlining on lag. |
| `Travel` | Distance, ETA and speed toward a named waypoint, checked against elytra range. |

## Dependencies

You need **Minecraft 1.21.11**, **Java 21**, **Fabric Loader** and a **Meteor Client build for 1.21.11**.

- **Baritone** is required for automatic dungeon farming, pathfinding and travel. We recommend [HunterBuddy Baritone](https://github.com/Likon69/hunterbuddy-baritone), our fork for Minecraft 1.21.11. The elytra flight and regear helpers use fixes and APIs from this fork.
- **Xaero's Minimap, Xaero's World Map and XaeroPlus** are required for map and waypoint features, including `waypoint-follower`, chunk-radar's waypoint marks and the `Travel` HUD. Install the Fabric versions for Minecraft 1.21.11.

These mods are not included in the HunterBuddy JAR. Put them and `hunterbuddy-1.0.0.jar` in your Minecraft instance's `mods` folder, then launch with Fabric.

## Files and network

- `chunk-radar` persists its trail memory to `hunterbuddy/trails.json` (plus a backup) under the Meteor folder; `rocket-boost` can append a per-tick trace to `hunterbuddy/rocketboost-trace.csv` when its trace setting is on.
- AreaLoader routes and the lifetime odometer stats are also stored as JSON.
- `old-chunk-notifier` and `visual-range-notifier` post to a Discord webhook if one is configured.

## Build

Java 21 is required. Use the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

The built addon JAR is written to `build/libs/hunterbuddy-1.0.0.jar`.

## Credits and provenance

Several modules started as ports from other addons rather than being written from scratch, credited where the code itself says so:

- **Baritone** (and this project's own fork of it) — the pathfinding underneath `auto-portal`, the elytra flight processes, and the regear/travel helpers.
- **mlep** (globalelitehooper) — `ElytraTakeoff`, and the modules named after it (`MlepScaffold`, `MlepAirPlace`). Its code is used under the MIT License, see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
- **[Jeff mod](https://github.com/miles352/meteor-stashhunting-addon)** (miles352) — the firework logic `Pitch40Classic` and `elytra-auto-fly` are ported from, and the reason `VanityESP` carries that name instead of its original one.
- **riths** — Hunting Utilities ([Tim](https://github.com/rithsgit/Tim)).
- **tilley** — the polar-spiral area-loader mode, ported from `polar-spiral-efly`.
- **0xTas** — [Stardust](https://github.com/0xTas/stardust): the firework hooks of `rocket-boost` (stronger push, rocket kept alive) are ported from its `RocketMan` (GPL-3.0); the speed control built on them is our own.

## License

HunterBuddy is licensed under the [GNU General Public License v3.0](LICENSE).

Copyright (C) 2026 Likon69 — https://github.com/Likon69/HunterBuddy

You may use, study and change this code. If you distribute it, or a client or addon that contains it, you must keep this notice and the license and publish your complete source code under the GPL-3.0 as well. Closed-source clients may not include it.

Additional term under section 7(b) of the GPL-3.0: any work that contains code from HunterBuddy, modified or not, must keep the author attribution "HunterBuddy by Likon69 - https://github.com/Likon69/HunterBuddy" in its source code and in the credits or about information it shows to its users.

Versions published before 12 October 2026 were dedicated to the public domain under CC0 1.0; that dedication still applies to those versions.
