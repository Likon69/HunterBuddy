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

| Module | Class | Purpose |
|--------|-------|---------|
| `elytra-auto-fly` | `ElytraAutoFly` | Custom elytra cycle: Pitch40 + auto-bound + auto-firework up to max-altitude, then manual-pitch descend to min-altitude, loop. |
| `ElytraBounce` | `ElytraBounce` | Elytra fly with extra features. |
| `ElytraRecast` | `ElytraRecast` | Flight recovery fallback. Monitors and recovers when you stop flying or drop too low. |
| `ElytraSwap` | `ElytraSwap` | Automatically swaps elytras when they reach low durability. |
| `control-fly` | `ControlFly` | GrimAC-compatible elytra flight control using WASD keys. |
| `Pitch40` | `Pitch40` | Utility for Pitch40 elytra flying. Syncs bounds with Meteor's ElytraFly and auto-enables on reconnect. |
| `RocketFly` | `RocketFly` | Maintains a level Y-flight with fireworks and smooth pitch control. |
| `AutoFlyingRegear` | `AutoFlyingRegear` | Automatically creates a platform and restocks rockets/elytras from ender chest while flying. |
| `auto-portal` | `AutoPortal` | For the base hunter who has places to be — places obsidian + flint&steel automatically (ported from Stash Hunt Addon by Jeff). |

### ESP / Render

| Module | Class | Purpose |
|--------|-------|---------|
| `cave-air` | `CaveAirESP` | Detects portal-shaped disturbances in cave air. |
| `flow-esp` | `FlowESP` | Chunk activity detector via fluid spread analysis. |
| `sign-render` | `SignRender` | Renders sign text through walls with advanced clustering. |
| `VanityESP` | `VanityESP` | Unified ESP for decorative items and special blocks. |
| `shulker-color` | `ShulkerColor` | Automatically dyes Shulker Boxes with Lime Dye in the crafting grid. |
| `shulker-overview` | `ShulkerOverviewModule` | Overlays the most common item icon on Shulker Boxes in the hotbar/inventory (via InGameHudMixin). |

### Movement

| Module | Class | Purpose |
|--------|-------|---------|
| `NoJumpDelay` | `NoJumpDelay` | Removes the delay between jumps. |
| `NoHurtCam` | `NoHurtCam` | Removes the hurt camera tilt and shake effect when taking damage. |
| `mlep-mine` | `MlepMine` | Mines blocks faster (NCP / Grim bypass). |
| `mlep-scaffold` | `MlepScaffold` | Places blocks under you using GrimAC bypass (sneak-at-ledge mixin). |
| `replenish` | `Replenish` | Advanced auto-replenish using shift-click packets. |
| `Items Sucker` | `ItemsSucker` | Automatically picks up dropped items on the ground using Baritone pathfinding. |

### Navigation / Pathfinding

| Module | Class | Purpose |
|--------|-------|---------|
| `waypoint-follower` | `WaypointFollower` | Advanced Xaero waypoint-following system with multi-dimensional flight support. |
| `TrailFollower` | `TrailFollower` | Automatically follows trails in all dimensions. |
| `hb-search-area` | `SearchArea` | Walks the player in a chunk-loading pattern (Rectangle / Spiral / PolarSpiral). Useful with stash finder / map mods. |
| `stash-finder` | `StashFinder` | Enhanced stash detection with privacy-focused coordinate management. |
| `sand-mine-addon` | `SandMineAddon` | Baritone-based miner that stops when the inventory is full of specified items. |

### Utility

| Module | Class | Purpose |
|--------|-------|---------|
| `ghost-container` | `GhostContainer` | Adds a button to container GUIs that exits without sending the close packet, leaving the container open server-side (Paper/Folia desync). |
| `auto-farm` | `AutoFarm` | All-in-one crop farming (till / harvest / plant / bonemeal). |
| `Anvil Auto Rename` | `AnvilRename` | Automatically renames items in the Anvil. |

### Villager Trading

| Module | Class | Purpose |
|--------|-------|---------|
| `Villager Trading` | `ExperienceTraderModule` | Full XP-bottle trading bot (Cleric villagers, chest dump/steal, stage pipeline). |
| `Experience Trader Starter` | `ExperienceTraderStarterModule` | Scheduler — auto-enables `Villager Trading` at a configured time. |

---

## HUDs

| HUD | Class | Purpose |
|-----|-------|---------|
| `elytra-helper` | `ElytraHelperHud` | Central flight info panel: elytra durability, flight time estimates, session stats, and hunt module status. |
| `SpeedKMH` | `SpeedKMH` | Displays movement speed in KM/H. |
| `movement-status` | `MovementStatusHud` | Displays your current sneaking and sprinting status. |

---

## Build

Requires Java 21+ and Gradle (wrapper included).

```bash
./gradlew build
```

Output JAR is in `build/libs/`. Drop it into `.minecraft/mods/` alongside:
- Meteor Client (1.21.11-SNAPSHOT)
- Baritone (1.21.11-SNAPSHOT)
- Xaero's Minimap (1.21.11)
- Xaero's World Map (1.21.11)
- XaeroPlus (1.21.11)

---

## Notes

- `agent.md` (and similar AI instruction files) are intentionally **gitignored** — this is a personal project, not a public one.
- `.reference/` is intentionally **gitignored** — vendored addons, for reading patterns only.
- Most modules are adapted from [mlep](https://github.com/) and [Stash Hunt Addon](https://github.com/) (Jeff). Original mlep code is preserved as-is; only the package is renamed (`mlep.*` → `com.hunterbuddy.*`).
- See `LICENSE` for license terms.