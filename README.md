# HunterBuddy

Private Meteor Client addon for hunting, flight, visuals, inventory logistics, and utility tools.

Created by **Texy** and **DragonFood**.

Target: **Minecraft 1.21.11** (Yarn `1.21.11+build.3`, Fabric Loader `0.18.2`, Meteor `1.21.11-SNAPSHOT`).

## Categories and modules

Only modules registered by `HunterBuddyAddon` are listed below. Source files that are present but deliberately not registered are not documented as available modules.

### Hunt (21)

Stash hunting, trail and waypoint travel, elytra flight, and world reconnaissance.

| Meteor name | Description |
|---|---|
| `angle-calculator` | Continuously locks your view toward a target coordinate to travel straight along it (highway trails). |
| `arealoader` | Loads chunks along a rectangle, spiral, zigzag, or circle route; routes can be saved and resumed. |
| `auto-portal` | Builds a nether portal frame, lights it, and optionally paths the player into it via Baritone. |
| `BepBoost` | Reports the highest velocity the server accepts while a firework is lit, instead of the one vanilla would build up to. Never lights fireworks itself — pair it with a flight module. |
| `BepRocketFly` | Holds a target altitude on timed fireworks; its Firework Boost group rides each firework window, alternating climb and dive legs to keep speed on any heading. |
| `chest-tracker` | Records the contents of containers you open, with a browser screen and JSON storage. |
| `chunk-radar` | Follows the 1.12 trail from the blocks in each arriving chunk, fits a line through the hits, and calls out when it is acquired, lost, turning, or thick enough to be a stash. Remembers trails between sessions. Overworld only. |
| `control-fly` | Elytra flight control using the WASD keys. |
| `elytra-auto-fly` | Flight cycle driving `pitch40-classic`: climb, then manual-pitch descent, looped. |
| `ElytraBounce` | Bounce-style elytra travel with speed and rotation locks, a highway obstacle passer, and portal-trap avoidance. |
| `ElytraRecast` | Flight recovery fallback: watches for lost flight or a low altitude and relaunches. |
| `old-chunk-notifier` | Sends a webhook message, optionally pinging you, when an old chunk is detected. |
| `phase` | Phases the player through solid blocks. |
| `Pitch40` | Pitch40 helper: syncs bounds with Meteor's ElytraFly and auto-enables on reconnect. |
| `pitch40-classic` | Standalone +40/-40 pitch oscillation with its own firework logic; does not use Meteor's ElytraFly. |
| `RocketFly` | Maintains level flight with fireworks and smooth pitch control. |
| `stash-finder` | Detects stashes; chunks identified as Woodland Mansions are ignored. |
| `TrailFollower` | Follows detected trails in all dimensions, with a rate-limited heading and a choice of search patterns (spiral, sweep, straight) when the trail is lost. |
| `visual-range-notifier` | Notifies when players enter visual range or selected items appear on the ground; optional Discord webhook alerts. |
| `waypoint-follower` | Follows Xaero waypoints with multi-dimensional travel support. |
| `yaw-lock` | Locks yaw to the nearest 45-degree increment. |

### Visuals (12)

ESP, overlays, and render helpers.

| Meteor name | Description |
|---|---|
| `cave-air` | Detects portal-shaped disturbances in cave air. |
| `container-tooltips` | Shows container contents when looking at tracked containers or shulkers in item frames. |
| `entity-view` | Rescales mobs and players, shows their gear above them through walls, names dropped items on the ground, and outlines nether portals with a tracer to the nearest one. |
| `FlightTrail` | A light two-trail wake behind the elytra while gliding, with speed-reactive flames at the wingtips, instead of dbrighthd's heavier particle trail. |
| `flow-esp` | Detects chunk activity from fluid-spread analysis. |
| `ItemSearchBar` | Searches and highlights items in inventories and containers. |
| `LoreLocator` | Highlights inventory slots holding rare, unique, or anomalous items. |
| `rotation-detector` | Finds horizontally placed blocks that only generate vertically in nature. |
| `shader` | Entity glow/outline shader. |
| `sign-render` | Renders sign text through walls, clustered to stay readable. |
| `SpawnerDetector` | Finds spawners a player has already visited, and whether the loot is still there. |
| `VanityESP` | Unified ESP for decorative items and selected special blocks. |

### Logistics (6)

Restocking, inventory handling, and stash transfer tools.

| Meteor name | Description |
|---|---|
| `AutoFlyingRegear` | Creates a temporary platform and restocks rockets or elytras from an ender chest. |
| `ElytraSwap` | Swaps out an elytra when it reaches low durability. |
| `PearlLoader` | Anti-AFK loop with pearl-loading support. |
| `replenish` | Replenishes hotbar items using shift-click packets. |
| `shulker-overview` | Overlays the most common contained item's icon on shulker boxes in inventory screens. |
| `stash-mover` | Moves items between selected input and output stash areas using pearl loading. |

### Utility (14)

| Meteor name | Description |
|---|---|
| `auto-exp-plus` | Repairs armor and tools with experience bottles, restocking bottles into a chosen hotbar slot. |
| `auto-log-plus` | Additional logout triggers. |
| `client-side-time` | Sets the displayed client-side time of day; server time and mob spawning are unaffected. |
| `disconnect-sound` | Plays a sound when the disconnect screen appears (e.g. when kicked). |
| `f-totem` | Keeps a totem in your off hand, replaced the moment it is used. |
| `ghost-container` | Adds a button that closes a container screen without sending the close packet, leaving it open server-side. |
| `h-mine` | Fast block mining, with a configurable swing animation. |
| `kill-aura-plus` | Attacks what you choose, with the weapon that hurts it most, on the beat that does full damage. |
| `mlep-air-place` | Places a block in air where your crosshair is pointing. |
| `mlep-scaffold` | Places blocks beneath you as you move. |
| `NoHurtCam` | Removes the hurt-camera tilt and shake. |
| `NoJumpDelay` | Removes the delay between jumps. |
| `rocket-boost` | Extends each firework's boost window with a fixed or automatically computed speed multiplier; can trace every boosted tick to a CSV for tuning. |
| `unfocused-fps` | Limits FPS while the game window is unfocused. |

## Commands (6)

Commands use the configured Meteor command prefix.

| Command | Description |
|---|---|
| `setinput` | Select the StashMover input area with two left-clicked corners. |
| `setoutput` | Select the StashMover output area with two left-clicked corners. |
| `stashstatus` | Show the selected areas and current StashMover status. |
| `setclear` | Clear both StashMover selections. |
| `arealoaderreset` | Delete every AreaLoader saved route, not just the selected one. |
| `trails` | List, forget, or clear the trails chunk-radar remembers. |

## HUD elements (29)

All HUD elements are registered in the `HunterBuddy` HUD group.

| HUD name | Description |
|---|---|
| `ChunkRadar` | Top-down view of the 1.12 trail the chunk-radar module is following. |
| `DimensionBanner` | A heraldic banner per dimension, crossfading at portals. |
| `DimensionCoords` | Coordinates for both Overworld and Nether. |
| `DubCounter` | Counts all containers in render distance. |
| `elytra-helper` | Central flight panel: elytra durability, flight time estimates, session stats, and hunt module status. |
| `ElytraStatus` | Compact one-line elytra status: speed, pitch, rockets, durability. |
| `EntityList` | Lists nearby entities. |
| `FindsTicker` | Recent finds and sightings as a fading feed. |
| `FollowerCockpit` | The waypoint follower's state, or its problem, plus route, consumables and session. |
| `FollowerHeading` | A heading tape for the waypoint follower: the target as a sliding diamond, the next turns as ghosts. |
| `FollowerRoute` | The follower's route as a line of milestones: reach halo on the current one, +N for the rest. |
| `HuntTally` | Session tally: portals, ender chests, shulkers, rockets, stashes, blocks. |
| `item-counter` | Selected items and their inventory counts. |
| `MobInfo` | Tracks mob spawns and density. |
| `movement-status` | Current sneaking and sprinting status. |
| `Odometer` | Session distance on mechanical drums, lifetime total below. |
| `Performance` | FPS, ping and server TPS in one line, coloured by level and flashing on spikes and drops. |
| `PingMeter` | Ping with sparkline, jitter, and keepalive freshness. |
| `Pitch40Cycle` | The pitch40 climb/dive cycle as a wave, with your position on it. |
| `RegearStatus` | AutoFlyingRegear phase, supplies, and mending progress. |
| `SessionTimeline` | The session as coloured activity segments with find markers. |
| `SpeedKMH` | Movement speed in km/h. |
| `SpeedSpectrum` | Equalizer bars driven by your speed, spiking on rocket boosts. |
| `SpiralCoverage` | AreaLoader coverage for any mode: flown legs, current leg, planned turns. |
| `SystemStats` | RAM and CPU usage with line graphs. |
| `ThreatBoard` | Players in range: distance, approach vector, gear summary. |
| `TimerSpeed` | Meteor Timer's current multiplier. |
| `TpsCardiogram` | Server TPS as a scrolling cardiogram, flatlining on lag. |
| `Travel` | Distance, ETA, and speed toward a named waypoint, checked against elytra range. |

## Module interactions

- `BepBoost` and `BepRocketFly` work as a pair: `BepRocketFly`'s `firework-boost` setting toggles `BepBoost` itself. `BepBoost` can also run alone under another flight module — it never lights fireworks.
- `elytra-auto-fly` drives `pitch40-classic`; enable the cycle module, not both by hand.
- `Pitch40` synchronizes its bounds with Meteor's own ElytraFly.
- `ElytraRecast` is a watchdog meant to run alongside a flight module, not a flight module itself.
- Run one flight controller at a time (`control-fly`, `ElytraBounce`, `elytra-auto-fly`, `pitch40-classic`, `RocketFly`, `BepRocketFly`), and one boost module at a time (`rocket-boost` or `BepBoost`).
- `stash-mover` is configured entirely through the `setinput` / `setoutput` / `stashstatus` / `setclear` commands.
- The `trails` command manages the memory the `chunk-radar` module builds.

## Dependencies

- **Xaero's Minimap / World Map (with XaeroPlus)** — compile-time libs under `libs/`; must be installed at runtime for `waypoint-follower`, chunk-radar's waypoint marks, and the `Travel` HUD.
- **Baritone** — compiled against [meteordevelopment/baritone](https://github.com/meteordevelopment/baritone) (compile-only), but at runtime the elytra flight, `auto-portal`, and the regear/travel helpers are built for [our own fork](.reference/hunterbuddy-baritone), which carries the fixes those features actually need.

## Files and network

- `chunk-radar` persists its trail memory to `hunterbuddy/trails.json` (plus a backup) under the Meteor folder; `rocket-boost` can append a per-tick trace to `hunterbuddy/rocketboost-trace.csv` when its trace setting is on.
- `chest-tracker`, AreaLoader routes, and the lifetime odometer stats are also stored as JSON.
- `old-chunk-notifier` and `visual-range-notifier` post to a Discord webhook if one is configured.

## Build

Java 21 is required. Use the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

The built addon JAR is written to `build/libs/hunterbuddy-1.0.0.jar`.

## Project notes

- `.reference/` is gitignored and used locally as read-only source material; it is not built with HunterBuddy.
- AI instruction files such as `agent.md`, `AGENTS.md`, and `CLAUDE.md` are gitignored.

## Credits and provenance

Several modules started as ports from other addons rather than being written from scratch, credited where the code itself says so:

- **Baritone** (and this project's own fork of it) — the pathfinding underneath `auto-portal`, the elytra flight processes, and the regear/travel helpers.
- **Dekrom** — the Baritone fork this project builds on, and BepHax, whose rocket boost and rotation-spoofing mechanics `BepBoost`, `BepRocketFly`, `TrailFollower`, and `ElytraBounce` are ported from.
- **mlep** (globalelitehooper) — `ElytraTakeoff`, and the modules named after it (`MlepMine`, `MlepScaffold`, `MlepAirPlace`).
- **riths** — Hunting Utilities ("Tim").
- **tilley** — the polar-spiral area-loader mode, ported from `polar-spiral-efly`.
