# HunterBuddy

Private Meteor Client addon for hunting, flight, inventory logistics, and utility tools.

Target: **Minecraft 1.21.11** (Yarn `1.21.11+build.3`, Fabric Loader `0.18.2`, Meteor `1.21.11-SNAPSHOT`).

## Categories and modules

Only modules registered by `HunterBuddyAddon` are listed below. Source files that are present but deliberately not registered are not documented as available modules.

### Hunt (23)

Stash hunting, trail and waypoint travel, elytra flight, and world reconnaissance.

| Meteor name | Description |
|---|---|
| `angle-calculator` | Locks yaw toward a target coordinate. |
| `auto-portal` | Automates portal-related travel actions. |
| `cave-air` | Detects portal-shaped disturbances in cave air. |
| `chest-tracker` | Tracks scanned container contents; includes browser, Discord webhook, and JSON storage. |
| `container-tooltips` | Shows contents of tracked containers and item-frame shulkers. |
| `control-fly` | Elytra flight control using WASD. |
| `elytra-auto-fly` | Cycles between a `Pitch40Classic` climb and manual-pitch descent. |
| `ElytraBounce` | Elytra flight module with additional controls. |
| `ElytraRecast` | Attempts flight recovery after landing or dropping too low. |
| `flow-esp` | Detects chunk activity from fluid-spread changes. |
| `ItemSearchBar` | Searches and highlights items in inventories and containers. |
| `old-chunk-notifier` | Notifies, optionally by Discord webhook, when an old-chunk cluster is detected. |
| `phase` | Phase module. |
| `Pitch40` | Pitch40 helper synchronized with Meteor ElytraFly. |
| `pitch40-classic` | Standalone classic `+40/-40` pitch oscillation with optional fireworks. |
| `RocketFly` | Maintains level flight with fireworks and pitch control. |
| `area-loader` | Walks a rectangle, spiral, or zigzag chunk-loading route. |
| `sign-render` | Renders sign text through walls. |
| `stash-finder` | Detects stashes; chunks identified as Woodland Mansions are ignored. |
| `TrailFollower` | Follows detected trails in all dimensions. |
| `VanityESP` | ESP for decorative items and selected special blocks. |
| `visual-range-notifier` | Notifies when players enter visual range or selected items are dropped nearby. |
| `waypoint-follower` | Follows Xaero waypoints with multi-dimensional travel support. |

### Logistics (6)

Restocking, inventory handling, and stash transfer tools.

| Meteor name | Description |
|---|---|
| `AutoFlyingRegear` | Creates a temporary platform and restocks rockets or elytras from an ender chest. |
| `ElytraSwap` | Replaces a low-durability elytra. |
| `PearlLoader` | Anti-AFK loop with pearl-loading support. |
| `replenish` | Replenishes items with shift-click packets. |
| `shulker-overview` | Shows the most common item icon on shulkers in inventory screens. |
| `stash-mover` | Moves items between selected input and output stash areas using pearl loading. |

StashMover commands use the configured Meteor command prefix:

| Command | Description |
|---|---|
| `setinput` | Select the input area with two left-clicked corners. |
| `setoutput` | Select the output area with two left-clicked corners. |
| `stashstatus` | Shows the selected areas and current StashMover status. |
| `setclear` | Clears both selected areas. |

### Utility (12)

| Meteor name | Description |
|---|---|
| `auto-exp-plus` | Repairs armor and tools with experience bottles. |
| `auto-log-plus` | Adds logout triggers. |
| `client-side-time` | Changes the displayed client-side time only. |
| `ghost-container` | Closes a container screen without sending its close packet. |
| `mlep-air-place` | Places a block in air at the crosshair target. |
| `mlep-mine` | Fast block-mining module. |
| `mlep-scaffold` | Places blocks beneath the player. |
| `NoHurtCam` | Removes the hurt-camera effect. |
| `NoJumpDelay` | Removes the delay between jumps. |
| `rotation-detector` | Finds horizontal blocks that normally generate vertically. |
| `unfocused-fps` | Limits FPS while Minecraft is unfocused. |
| `yaw-lock` | Locks yaw to the nearest 45-degree increment. |

### Lab (1)

Experimental modules. Their behavior is not guaranteed across servers or anti-cheat versions.

| Meteor name | Description |
|---|---|
| `BoatFlyLab` | Experimental boat-flight behavior; the module declares singleplayer-only use. |

### Future (3)

Ports from external references. Experimental and not guaranteed across servers or anti-cheat versions.

| Meteor name | Description |
|---|---|
| `shader` | Applies an entity outline shader. |
| `lambda-packet-mine` | Port of lambda's PacketMine: targeted block-mining with rebreak mode, queue, double-break, break radius, flatten, and render settings. |
| `lambda-nuker` | Port of lambda's Nuker: configurable flatten modes, dimensions, on-ground requirement, floor fill, fluid fill, and Baritone selection. |

## HUD elements (11)

All HUD elements are registered in the `HunterBuddy` HUD group.

| HUD name | Description |
|---|---|
| `DimensionCoords` | Coordinates with Overworld/Nether conversion. |
| `DubCounter` | Counts nearby containers and estimates storage slots. |
| `ElytraHelperHud` | Detailed elytra, rocket, speed, distance, and module status panel. |
| `ElytraStatus` | Compact elytra, rockets, pitch, durability, and speed line. |
| `EntityList` | Lists nearby entities. |
| `item-counter` | Counts configured inventory items. |
| `MobInfo` | Tracks mob spawns and density. |
| `movement-status` | Shows sneak and sprint status. |
| `SpeedKMH` | Displays movement speed in km/h. |
| `SystemStats` | Displays RAM and process CPU usage with history graphs. |
| `TimerSpeed` | Displays Meteor Timer's active multiplier. |

## Build

Java 21 is required. Use the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

The built addon JAR is written to `build/libs/hunterbuddy-0.1.0.jar`.

## Project notes

- `.reference/` is gitignored and used locally as read-only source material; it is not built with HunterBuddy.
- AI instruction files such as `agent.md`, `AGENTS.md`, and `CLAUDE.md` are gitignored.
- See `LICENSE` for licensing information.
