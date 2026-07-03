# HunterBuddy

A personal Meteor Client addon that gathers, in one place, the modules we actually want to use.

This is **not** a public release project. It is a private toolbox built for ourselves, cherry-picking and adapting patterns from Meteor Client addons we like (`.reference/` is vendored locally for reading only — never built, never synced, never committed).

---

## Authors

- **Texy**
- **DragonFooD**

---

## Modules

| Module | Class | Purpose |
|--------|-------|---------|
| `aire-force` | `AireForce` | Spiral chunk exploration |
| `Anvil Auto Rename` | `AnvilRename` | Auto-rename items in an Anvil |
| `auto-farm` | `AutoFarm` | All-in-one crop farming (till / harvest / plant / bonemeal) |
| `Items Sucker` | `ItemsSucker` | Pathfind and collect dropped items via Baritone |
| `sand-mine-addon` | `SandMineAddon` | Baritone-based block miner, stops when inventory is full |
| `shulker-color` | `ShulkerColor` | Auto-dye Shulker Boxes with Lime Dye |
| `elytra-auto-fly` | `ElytraAutoFly` | Auto elytra cycle: climb to `high-altitude` via Meteor's Pitch40 + auto-bound-adjust + auto-firework, then descend to `low-altitude` via Vanilla + manual pitch, repeats |
| `elytra-boost` | `ElytraBoost` | Velocity-injection elytra fly (PlayerMoveEvent). Optional auto-takeoff + auto-redeploy. |
| `Villager Trading` | `ExperienceTraderModule` | Full XP-bottle trading bot (Cleric villagers, chest dump/steal, stage pipeline) |
| `Experience Trader Starter` | `ExperienceTraderStarterModule` | Scheduler — auto-enables `Villager Trading` at a configured time |

---

## Build

Requires Java 21+ and Gradle (wrapper included).

```bash
./gradlew build
```

Output JAR is in `build/libs/`. Drop it into `.minecraft/mods/` alongside Meteor Client and Baritone.

---

## Notes

- `agent.md` (and similar AI instruction files) are intentionally **gitignored** — this is a personal project, not a public one.
- `.reference/` is intentionally **gitignored** — vendored addons, for reading patterns only.
- See `LICENSE` for license terms.