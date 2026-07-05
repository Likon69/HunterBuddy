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

### Elytra / Flight

| Module | Name | Description |
|--------|------|-------------|
| `elytra-auto-fly` | `ElytraAutoFly` | Custom 2-phase elytra cycle: Pitch40Classic climb + auto-bound + manual-pitch descend. Uses our standalone Pitch40Classic (not Meteor ElytraFly). |
| `ElytraBounce` | `ElytraBounce` | Elytra fly with some more features. |
| `ElytraRecast` | `ElytraRecast` | Flight recovery fallback. Monitors and recovers when you stop flying or drop too low. |
| `ElytraSwap` | `ElytraSwap` | Automatically swaps elytras when they reach low durability. |
| `control-fly` | `ControlFly` | GrimAC-compatible elytra flight control using WASD keys. |
| `Pitch40` | `Pitch40` | Utility for Pitch40 elytra flying. Syncs bounds with Meteor ElytraFly and auto-enables on reconnect. (Legacy — activates Meteor's broken Pitch40 mode.) |
| `Pitch40Classic` | `pitch40-classic` | Standalone classic +40/-40 oscillation (0.5.4/0.5.8 behavior). Does NOT activate Meteor ElytraFly. Auto-firework optional. |
| `RocketFly` | `RocketFly` | Maintains a level Y-flight with fireworks and smooth pitch control. |
| `AFKVanillaFly` | `AFKVanillaFly` | Maintains a level Y-flight with fireworks and smooth pitch control. (JEFF mod port.) |
| `AutoFlyingRegear` | `AutoFlyingRegear` | Automatically creates a platform and restocks rockets/elytras from ender chest. |

### ESP / Render

| Module | Name | Description |
|--------|------|-------------|
| `cave-air` | `CaveAirESP` | Detects portal-shaped disturbances in cave air. |
| `flow-esp` | `FlowESP` | Chunk activity detector via fluid spread analysis. |
| `sign-render` | `SignRender` | Renders sign text through walls with advanced clustering. |
| `VanityESP` | `VanityESP` | Unified ESP for decorative items and special blocks. |
| `shulker-overview` | `ShulkerOverviewModule` | Overlays the most common item icon on Shulker Boxes in hotbar/inventory AND in any HandledScreen (chests, ender chests, player inventory E screen). |

### Stash / Navigation

| Module | Name | Description |
|--------|------|-------------|
| `stash-finder` | `StashFinder` | Enhanced stash detection with privacy-focused coordinate management. (Meteor's StashFinder ported from mlep.) |
| `better-stash-finder` | `BetterStashFinder` | Meteor's StashFinder but with more features. (JEFF mod port — uses XaeroPlus events.) |
| `TrailFollower` | `TrailFollower` | Automatically follows trails in all dimensions. (JEFF mod port.) |
| `waypoint-follower` | `WaypointFollower` | Advanced waypoint following system with multi-dimensional flight support. |
| `hb-search-area` | `SearchArea` | Walks the player in a chunk-loading pattern (Rectangle / Spiral / PolarSpiral). Useful with stash finder / map mods. |

### Movement / Combat

| Module | Name | Description |
|--------|------|-------------|
| `NoJumpDelay` | `NoJumpDelay` | Removes the delay between jumps. |
| `NoHurtCam` | `NoHurtCam` | Removes the hurt camera tilt and shake effect when taking damage. |
| `mlep-mine` | `MlepMine` | Mines blocks faster. |
| `mlep-scaffold` | `MlepScaffold` | Places blocks under you using GrimAC bypass. |
| `replenish` | `Replenish` | Advanced auto-replenish using shift-click packets. |
| `ghost-container` | `GhostContainer` | Adds a button to container GUIs that exits without sending the close packet, leaving the container open server-side (Paper/Folia desync). |

### Utility

| Module | Name | Description |
|--------|------|-------------|
| `auto-exp-plus` | `AutoEXPPlus` | Automatically repairs your armor and tools in pvp. (JEFF mod port.) |
| `auto-log-plus` | `AutoLogPlus` | Provides some additional triggers to log out. (JEFF mod port.) |
| `unfocused-fps` | `UnfocusedFpsLimiter` | Limits the FPS when the game is unfocused. (mlep port.) |
| `auto-portal` | `AutoPortal` | For the base hunter who has places to be. (JEFF mod port.) |

---

## HUDs

| HUD | Name | Description |
|-----|------|-------------|
| `elytra-helper` | `ElytraHelperHud` | Central flight info panel: elytra durability, flight time estimates, session stats, and hunt module status. |
| `SpeedKMH` | `SpeedKMH` | Displays movement speed in KM/H. |
| `movement-status` | `MovementStatusHud` | Displays your current sneaking and sprinting status. |

---

## Removed modules (no longer in project)

- `AnvilRename` (auto-rename items in anvil)
- `ShulkerColor` (auto-dye shulker boxes with lime dye)
- `AutoFarm` (all-in-one crop farming)
- `ItemsSucker` (auto-pickup dropped items with Baritone)
- `SandMineAddon` (mine specified items, stop when full)
- `AireForce` (spiral chunk exploration)
- `ElytraBoost` (velocity-injection elytra fly)

---

## Build

Requires Java 21+ and Gradle (wrapper included).

```bash
./gradlew build
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
- Most modules are adapted from [mlep](https://github.com/) (with package rename `mlep.*` → `com.hunterbuddy.*`).
- Some modules (AFKVanillaFly, AutoLogPlus, AutoEXPPlus, AutoPortal, BetterStashFinder, TrailFollower) are ported from [meteor-stashhunting-addon-1.21.1](https://github.com/) (JEFF mod).
- See `LICENSE` for license terms.