# HunterBuddy

> **HunterBuddy is built on open-source addons, credited at the bottom of this page with their licenses. If you think something in this repository is yours, open an issue and it will be looked at.**

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
| `300kmfly` | Fast firework flight on a fixed heading. Holds an altitude band with alternating dive and climb legs (or a smooth level mode), turning the pitch a few degrees per tick. Turns `rocket-boost` on by itself and, with `auto-speed` checked, switches on its auto-speed for the flight and puts it back after; unchecked, it leaves `rocket-boost` exactly as you set it. Relights each rocket just before `rocket-boost`'s extension runs out. Takes off from the ground, rises over terrain and flips a leg when terrain is ahead, glides level once the rockets run out, and never lights one in water or lava. Logs its speed, altitude, leg and rockets per minute every second. |
| `arealoader` | Loads chunks along a rectangle, spiral, zigzag, or circle route; routes can be saved and resumed. |
| `auto-portal` | Builds a nether portal frame, lights it, and optionally paths the player into it via Baritone. |
| `chunk-radar` | Follows the 1.12 trail from the blocks in each arriving chunk, fits a line through the hits, and calls out when it is acquired, lost, turning, or thick enough to be a stash. Remembers trails between sessions. Overworld only. |
| `elytra-auto-fly` | Flight cycle driving `pitch40-classic`: climb, then manual-pitch descent, looped. |
| `ElytraBounce` | Bounce-style elytra travel with speed and rotation locks, a highway obstacle passer, and portal-trap avoidance. `solve-pitch` picks the fastest bounce angle for the ceiling above you, and `chestplate-fakefly` bounces with a chest piece worn and the elytra in a hand, swapping the elytra in only for the take-off so it never loses durability. |
| `ElytraRecast` | Flight recovery fallback: watches for lost flight or a low altitude and relaunches. |
| `old-chunk-notifier` | Sends a webhook message, optionally pinging you, when an old chunk is detected. |
| `Pitch40` | Pitch40 helper: syncs bounds with Meteor's ElytraFly and auto-enables on reconnect. Its `chest-swap` glides with a chest piece worn and the elytra in a hand, swapping the elytra in for a moment at each burst so it never loses durability, and keeps it on while a rocket pushes. |
| `pitch40-classic` | Standalone +40/-40 pitch oscillation with its own firework logic; does not use Meteor's ElytraFly. |
| `RocketFly` | Maintains level flight with fireworks and smooth pitch control. |
| `stash-finder` | Detects stashes; chunks identified as Woodland Mansions are ignored. |
| `TrailFollower` | Follows detected trails in all dimensions, with a turn-limited heading (`heading-turn-limit`), chunks far behind ignored (`ignore-behind`), and a lost-trail scan (`lost-trail-scan`: weave, spin or hold) when the trail stops coming. |
| `visual-range-notifier` | Notifies when players enter visual range or selected items appear on the ground; optional Discord webhook alerts. |
| `waypoint-follower` | Follows the Xaero waypoints whose name starts with `waypoint-prefix` (`Hunt_` by default), in numeric order or closest first, with a flight mode per dimension; once the last one is reached it stops where it is, in the air or on the ground, by switching itself off or by leaving the server. It can also lay the waypoints for you: `seed-route` fills a hunt chain along an already-mapped trail between your `Route_1`, `Route_2`… control marks (dropped on Xaero's map with `add-route-key`), one every `seed-spacing` blocks or only at portal patches, and `nether-record-route` drops one every `seed-spacing` blocks along your own Nether flight. Keys add, skip or pause waypoints from the map, and `clear-waypoints` wipes every hunt waypoint and route mark for a fresh start. |
| `yaw-lock` | Locks yaw to the nearest 45-degree increment. |

### Visuals (9)

ESP, overlays, and render helpers.

| Meteor name | Description |
|---|---|
| `cave-air` | Detects portal-shaped disturbances in cave air. |
| `entity-view` | Rescales mobs and players, shows their gear above them through walls, names dropped items on the ground, and outlines nether portals with a tracer to the nearest one. |
| `FlightTrail` | A light two-trail wake behind the elytra while gliding, with speed-reactive flames at the wingtips, instead of dbrighthd's heavier particle trail. |
| `flow-esp` | Detects chunk activity from fluid-spread analysis. |
| `rotation-detector` | Finds horizontally placed blocks that only generate vertically in nature. |
| `shader` | Entity glow/outline shader. |
| `sign-render` | Renders sign text through walls, clustered to stay readable. |
| `SpawnerDetector` | Finds classic monster-room dungeons, with or without their spawner, along with activated trial spawners and shipwrecks. It scores the player traces around each room: placed lights, ender chests, player blocks, a broken spawner, mobs, tunnels and filled shafts. Colored boxes and labels show which rooms are traced and which still hold chests. With `auto-farm` it flies to a traced room on its own, or to any room with a chest with `auto-farm-all`. It digs down, mines the spawner, loots the chests, climbs back out and hands flight back to your flight module. With `detect-shipwrecks` it also loots shipwreck chests in old chunks. It will not leave below a health or rocket minimum, nor while a non-friend player is loaded. |
| `VanityESP` | Unified ESP for decorative items and selected special blocks. |

### Logistics (6)

Restocking and inventory handling tools.

| Meteor name | Description |
|---|---|
| `AutoFlyingRegear` | Lands on solid ground, never over lava, builds a walled box and restocks rockets, elytras and the other supplies it watches from an ender chest when one runs short. When the chest can no longer supply what is missing it never takes off again: it stays in the closed box and switches the modules off, or leaves the server. |
| `ElytraSwap` | Swaps out an elytra when it reaches low durability. |
| `PearlLoader` | Anti-AFK loop with pearl-loading support. |
| `replenish` | Replenishes hotbar items using shift-click packets. |
| `shulker-overview` | Overlays the most common contained item's icon on shulker boxes in inventory screens. |
| `shulker-sorter` | Sorts the shulkers and loose items in your input chests into category chests, walking between them with Baritone. At a station it keeps only the dominant category in mixed shulkers and tops up partial pure shulkers with loose items. It fills an empty shulker once a category has at least 27 loose stacks; an optional `shulkerColor` in the JSON prefers its dye color. Withdrawals take only 100% pure shulkers, or empty ones for `shulkers`. It pauses while another player is in sight (friends allowed with `ignore-friends`), while SpawnerDetector is on and while the regear runs, and it can resume after a reconnect. Register chests with `tag-key` and set their roles with the buttons in its settings; `dry-run` reports where inputs would go without moving them. |

### Utility (14)

| Meteor name | Description |
|---|---|
| `auto-eat-sync` | Eats golden apples only, in a safe window while flying, fighting or surviving a regear, without ever pausing Baritone; always eats below an emergency health, and as soon as health is dropping fast enough, however much is left. |
| `auto-exp-plus` | Repairs armor and tools with experience bottles, restocking bottles into a chosen hotbar slot. |
| `auto-log-plus` | Additional logout triggers. |
| `client-side-time` | Sets the displayed client-side time of day; server time and mob spawning are unaffected. |
| `disconnect-sound` | Plays a sound when the disconnect screen appears (e.g. when kicked). |
| `Experience Trader Starter` | Switches Villager Trading on once per in-game day, at the in-game time you choose (13:00 by default), with a fresh list of clerics to visit. |
| `f-totem` | Keeps a totem in your off hand, replaced the moment it is used. |
| `h-mine` | Fast block mining, with a configurable swing animation. |
| `kill-aura-plus` | Attacks what you choose, with the weapon that hurts it most, on the beat that does full damage. |
| `mlep-air-place` | Places a block in air where your crosshair is pointing. |
| `mlep-scaffold` | Places blocks beneath you as you move. |
| `NoJumpDelay` | Removes the delay between jumps. |
| `rocket-boost` | Extends each firework's boost window with a fixed or automatically computed speed multiplier; can trace every boosted tick to a CSV for tuning. |
| `Villager Trading` | Buys experience bottles from clerics with emeralds. Each round it visits every cleric within `villagers-searching-radius`, nearest first, and walks next to it with Baritone. It buys the emerald-for-bottles trade as long as the price stays at or under `experience-max-price`, until that trade sells out. When emeralds run low, it drops its bottles in the experience chest, takes emeralds from the emerald chest and crafts emerald blocks back into emeralds. Once no cleric is left, it switches off and walks back to its home position. It waits while AutoEat is eating. |

## Commands (3)

Commands use the configured Meteor command prefix.

| Command | Description |
|---|---|
| `arealoaderreset` | Delete every AreaLoader saved profile, not just the selected one. |
| `sorter` | Fallback setup and withdrawal for the shulker sorter (`input`, `dest`, `reserve`, `station`, `remove`, `list`, `get`…). |
| `trails` | List, forget, or clear the trails chunk-radar remembers. |

## HUD elements (21)

All HUD elements are registered in the `HunterBuddy` HUD group.

| HUD name | Description |
|---|---|
| `ChunkRadar` | Top-down view of the 1.12 trail the chunk-radar module is following. |
| `elytra-helper` | Central flight panel: elytra durability, flight time estimates, session stats, and hunt module status. |
| `ElytraStatus` | Compact one-line elytra status: speed, pitch, rockets, durability. |
| `FindsTicker` | Recent finds and sightings as a fading feed. |
| `FollowerHeading` | A heading tape for the waypoint follower: the target as a sliding diamond, the next turns as ghosts. |
| `FollowerRoute` | The follower's route as a line of milestones: reach halo on the current one, +N for the rest. |
| `HuntTally` | Session tally: portals, ender chests, shulkers, rockets, stashes, blocks. |
| `movement-status` | Current sneaking and sprinting status. |
| `Odometer` | Session distance on mechanical drums, lifetime total below. |
| `Performance` | FPS, ping and server TPS in one line, coloured by level and flashing on spikes and drops. |
| `PingMeter` | Ping with sparkline, jitter, and keepalive freshness. |
| `Pitch40Cycle` | The pitch40 climb/dive cycle as a wave, with your position on it. |
| `RegearStatus` | The regear's identity badge, its station on its line, stocks and gains, counters, next-regear estimate and last event. |
| `SorterStatus` | Shulker sorter state, target, input progress, confirmed deliveries, purified and packed shulkers, capacity and alerts. |
| `SpeedSpectrum` | Equalizer bars driven by your speed, spiking on rocket boosts. |
| `SpiralCoverage` | AreaLoader coverage for any mode: flown legs, current leg, planned turns. |
| `SystemStats` | RAM and CPU usage with line graphs. |
| `ThreatBoard` | Players in range: distance, approach vector, gear summary. |
| `TimerSpeed` | Meteor Timer's current multiplier. |
| `TpsCardiogram` | Server TPS as a scrolling cardiogram, flatlining on lag. |
| `Travel` | Distance, ETA, and speed toward a named waypoint, checked against elytra range. |

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
- **mlep** (globalelitehooper) — `ElytraTakeoff`, and the modules named after it (`MlepMine`, `MlepScaffold`, `MlepAirPlace`). Its code is used under the MIT License, see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
- **[Jeff mod](https://github.com/miles352/meteor-stashhunting-addon)** (miles352) — the firework logic `Pitch40Classic` and `elytra-auto-fly` are ported from, and the reason `VanityESP` carries that name instead of its original one.
- **riths** — Hunting Utilities ([Tim](https://github.com/rithsgit/Tim)).
- **tilley** — the polar-spiral area-loader mode, ported from `polar-spiral-efly`.
- **0xTas** — [Stardust](https://github.com/0xTas/stardust): the firework hooks of `rocket-boost` (stronger push, rocket kept alive) are ported from its `RocketMan`; the speed control built on them is our own.
