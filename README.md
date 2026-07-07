# HunterBuddy

A personal Meteor Client addon that gathers, in one place, the modules we actually want to use.

This is **not** a public release project. It is a private toolbox built for ourselves, cherry-picking and adapting patterns from Meteor Client addons we like (`.reference/` is vendored locally for reading only — never built, never synced, never committed).

Current target: **Minecraft 1.21.11** (Yarn 1.21.11+build.3, Fabric Loader 0.18.2, Meteor 1.21.11-SNAPSHOT).

---

## Authors

- **Texy**
- **DragonFooD**

---

## Modules

### Hunt (24)

Core stash-hunting and elytra-flight toolkit.

| Meteor name | Java class | Description |
|---|---|---|
| `AFKVanillaFly` | `AFKVanillaFly` | Maintains a level Y-flight with fireworks and smooth pitch control. (JEFF port.) |
| `AutoFlyingRegear` | `AutoFlyingRegear` | Automatically creates a platform and restocks rockets/elytras from ender chest. (mlep port.) |
| `auto-portal` | `AutoPortal` | For the base hunter who has places to be. (mlep port, rewritten in HunterBuddy.) |
| `better-stash-finder` | `BetterStashFinder` | Meteor's StashFinder with more features (storage block list, Discord webhook, JSON+CSV persistence). (HunterBuddy original.) |
| `cave-air` | `CaveAirESP` | Detects portal-shaped disturbances in cave air. (mlep port.) |
| `control-fly` | `ControlFly` | GrimAC-compatible elytra flight control using WASD keys. (mlep port.) |
| `elytra-auto-fly` | `ElytraAutoFly` | 2-phase elytra cycle: Pitch40Classic climb + auto-bound, then manual-pitch descend. Loop. (HunterBuddy original, uses standalone `Pitch40Classic` instead of Meteor ElytraFly.) |
| `ElytraBounce` | `ElytraBounce` | Elytra fly with some more features. (mlep port.) |
| `ElytraRecast` | `ElytraRecast` | Flight recovery fallback. Monitors and recovers when you stop flying or drop too low. (mlep port.) |
| `ElytraSwap` | `ElytraSwap` | Automatically swaps elytras when they reach low durability. (mlep port.) |
| `flow-esp` | `FlowESP` | Chunk activity detector via fluid spread analysis. (mlep port.) |
| `old-chunk-notifier` | `OldChunkNotifier` | Sends a Discord webhook (optional ping) when an old chunk is detected. Cluster-based (only fires when chunk + neighbors >= min cluster size). (JEFF port.) |
| `Pitch40` | `Pitch40` | Legacy Pitch40 utility. Syncs bounds with Meteor ElytraFly and auto-enables on reconnect. (mlep port — activates Meteor's broken Pitch40 mode.) |
| `pitch40-classic` | `Pitch40Classic` | Standalone classic +40/-40 oscillation (0.5.4/0.5.8 behavior). Does NOT activate Meteor ElytraFly. Auto-firework optional. (HunterBuddy original.) |
| `RocketFly` | `RocketFly` | Maintains a level Y-flight with fireworks and smooth pitch control. (mlep port.) |
| `area-loader` | `AreaLoader` | Walks the player in a chunk-loading pattern (Rectangle / Spiral / ZigZag). Integrated with AutoFlyingRegear (paused during regear, JSON save/load). (mlep port.) |
| `yaw-lock` | `YawLock` | Locks yaw to nearest 45° increment. Optional 2b2t anticheat jitter. (mlep port.) |
| `angle-calculator` | `AngleCalculator` | Continuously locks yaw toward a target coordinate (highway trails). (mlep port.) |
| `mlep-air-place` | `MlepAirPlace` | Manual scaffold: right-click with block in hand → places block in air where crosshair points. (mlep port — uses `PlacementUtils.grimPlace`.) |
| `sign-render` | `SignRender` | Renders sign text through walls with advanced clustering. (mlep port.) |
| `stash-finder` | `StashFinder` | Enhanced stash detection with privacy-focused coordinate management. (mlep port.) |
| `TrailFollower` | `TrailFollower` | Automatically follows trails in all dimensions. (mlep port — 805 lines, advanced settings `forwardConeAngle` + `forwardWeightStrength` only present in mlep version.) |
| `VanityESP` | `VanityESP` | Unified ESP for decorative items and special blocks. (mlep port, renamed to avoid conflict with JEFF mod.) |
| `waypoint-follower` | `WaypointFollower` | Advanced waypoint following system with multi-dimensional flight support. (mlep port.) |

### Lab (5)

Experimental modules — bypass research, anti-cheat probing, server-tolerance tests. Use at your own risk. Each module logs to a CSV in `.minecraft/meteor-client/hunterbuddy/<name>-lab/`.

| Meteor name | Java class | Description |
|---|---|---|
| `boost-lab` | `BoostLab` | Controlled elytra boost experiments with correction detection. (HunterBuddy original.) |
| `bounce-lab` | `BounceLab` | Logs elytra bounce/collision events (Floor / Ceiling / Wall) for timing and Y-momentum analysis. (HunterBuddy original.) |
| `rocket-state-lab` | `RocketStateLab` | Logs rocket/start-flying timing, speed gains, and server corrections. Modes: ObserveOnly, NormalUse, SlotSwapUse, OffhandUse, MoveFromInv, StartFlyBefore/After/Pulse. (HunterBuddy original.) |
| `slot-use-desync-lab` | `SlotUseDesyncLab` | Tests slot-swap / use-item orderings for server-side desync. Modes: ObserveOnly, GhostSwap, SwapUseSwap, RapidSwapUse, UseThenSwap. (HunterBuddy original.) |
| `start-flying-spam-lab` | `StartFlyingSpamLab` | Spam-tests `START_FALL_FLYING` timings to probe server tolerance. Modes: ObserveOnly, PulseTick, OnlyIfNotGliding, PulseWhileGliding, WithRocket. (HunterBuddy original.) |

### Utility (10)

| Meteor name | Java class | Description |
|---|---|---|
| `auto-exp-plus` | `AutoEXPPlus` | Automatically repairs your armor and tools in pvp. (JEFF port.) |
| `auto-log-plus` | `AutoLogPlus` | Provides some additional triggers to log out. (JEFF port.) |
| `ghost-container` | `GhostContainer` | Adds a button to container GUIs that exits without sending the close packet, leaving the container open server-side (Paper/Folia desync). (JEFF port.) |
| `mlep-mine` | `MlepMine` | Mines blocks faster. (mlep port.) |
| `mlep-scaffold` | `MlepScaffold` | Places blocks under you using GrimAC bypass. (mlep port.) |
| `NoHurtCam` | `NoHurtCam` | Removes the hurt camera tilt and shake effect when taking damage. (mlep port.) |
| `NoJumpDelay` | `NoJumpDelay` | Removes the delay between jumps. (mlep port.) |
| `replenish` | `Replenish` | Advanced auto-replenish using shift-click packets. (mlep port.) |
| `shulker-overview` | `ShulkerOverviewModule` | Overlays most common item icon on shulker boxes in hotbar/inventory AND in any container screen (chests, ender chests, player E screen). (JEFF port.) |
| `unfocused-fps` | `UnfocusedFpsLimiter` | Limits the FPS when the game is unfocused. (JEFF port.) |

---

## Quick Start — Hunt Scenarios

### Scenario 1: Follow a trail (auto-detection)

Modules to enable manually (only these, the rest cascades):
1. `TrailFollower` — detects the trail + steers yaw
2. `AutoFlyingRegear` — monitors rockets/elytras + auto-replenishes from ender chest
3. `Replenish` — refills hotbar from inventory
4. `ElytraSwap` — swaps damaged elytra automatically

Cascade (auto-activated): `TrailFollower` → `Pitch40` → `ElytraRecast`.

Inventory required: equipped elytra, ≥64 fireworks, ender chest + shulker(s) of rockets in ender chest, optional 2–6 spare elytras.

### Scenario 2: Follow Xaero waypoints

Modules to enable manually (only these, the rest cascades):
1. `WaypointFollower` — follows waypoints prefixed `Hunt_` + triggers takeoff
2. `AutoFlyingRegear` — monitors rockets/elytras + auto-replenishes from ender chest
3. `Replenish` — refills hotbar from inventory
4. `ElytraSwap` — swaps damaged elytra automatically

Cascade (auto-activated): `WaypointFollower` → `ElytraRecast` (1× for takeoff) → `Pitch40` (sustained flight). `ElytraRecast` stays in MONITORING after that.

Inventory required: same as Scenario 1, plus waypoints `Hunt_*` defined in Xaero World Map.

### Cascade summary

`TrailFollower` / `WaypointFollower` → `Pitch40` (Meteor ElytraFly Pitch40 mode) → `ElytraRecast` (recovery, auto).

`AutoFlyingRegear` / `Replenish` / `ElytraSwap` are **NOT** auto-activated (same as original mlep). Enable them manually.

---

## HUDs (11)

### Flight (2)

| Meteor name | Java class | Description |
|---|---|---|
| `ElytraHelperHud` | `ElytraHelperHud` | Central flight info panel: equipped elytra durability (raw + %), estimated flight time (calibrated from mlep), inventory pool, session time/distance, current speed, hunt module status (ElytraSwap, ElytraBounce, ElytraRecast, TrailFollower, AreaLoader). (mlep port.) |
| `ElytraStatus` | `ElytraStatusHud` | Compact one-line elytra status: `BPS: 33.1 \| Pitch: -10.0° \| Rockets: 230 \| Dur: 318/432`. Each field togglable, color-coded by threshold (normal / warn / danger). (HunterBuddy original — complements ElytraHelperHud.) |

### Stats & Info (3)

| Meteor name | Java class | Description |
|---|---|---|
| `SpeedKMH` | `SpeedKMH` | Displays movement speed in KM/H. (mlep port.) |
| `MovementStatusHud` | `MovementStatusHud` | Displays your current sneaking and sprinting status. (mlep port.) |
| `SystemStats` | `SystemStatsHud` | RAM (used/max + %) and CPU (process load %) with line graphs (configurable width/height/points). Configurable warn/danger thresholds. Peak marker + optional average indicator. Threshold lines drawn on graphs. (HunterBuddy original.) |

### Stash / Loot (3)

| Meteor name | Java class | Description |
|---|---|---|
| `DubCounter` | `DubCounterHud` | Counts all containers (chests, trapped chests, barrels, shulkers by color, ender chests, hoppers, droppers, dispensers, furnaces, brewing stands, lecterns, crafters, decorated pots) within render distance. Estimated storage slots. (mlep port.) |
| `MobInfo` | `MobInfo` | Tracks mob spawns (per hour) and density (per chunk) in configurable scan radius. Density alert. Spawn rate graph + mob count graph. (mlep port.) |
| `ItemCounter` | `ItemCounterHud` | Counts selected items in inventory (configurable item list). Vertical / Horizontal / Grid layouts. Low-count warning color. (mlep port.) |

### Spatial (2)

| Meteor name | Java class | Description |
|---|---|---|
| `EntityList` | `EntityList` | Lists nearby entities (players, mobs, dropped items, projectiles, firework rockets). Sortable by distance. Distance display optional. Per-type color. (mlep port.) |
| `DimensionCoords` | `DimensionCoords` | Shows current position with automatic Overworld ↔ Nether coordinate conversion (1:8 ratio). Horizontal or vertical layout. End dimension shown separately. (mlep port.) |

### Other (1)

| Meteor name | Java class | Description |
|---|---|---|
| `TimerSpeed` | `TimerSpeedHud` | Displays the current Timer module multiplier (e.g. `1.00x`, `2.50x`). Shows `1.00x` when Timer is inactive. (mlep port.) |

---

## Build

Requires Java 21+ and Gradle (wrapper included).

```bash
JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" PATH="/c/Program Files/Java/jdk-21.0.11/bin:$PATH" ./gradlew build
```

Output JAR is in `build/libs/hunterbuddy-0.1.0.jar`. Drop it into `.minecraft/mods/` alongside:
- Meteor Client (1.21.11-SNAPSHOT)
- Baritone (1.21.11-SNAPSHOT)
- Xaero's Minimap (1.21.11)
- Xaero's World Map (1.21.11)
- XaeroPlus (1.21.11)
- LambdaEvents (2.4.2) — included in `libs/`

---

## Notes

- `agent.md` (and similar AI instruction files) are intentionally **gitignored** — this is a personal project, not a public one.
- `.reference/` is intentionally **gitignored** — vendored addons, for reading patterns only.
- See `LICENSE` for license terms.