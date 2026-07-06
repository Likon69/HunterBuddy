# mlep Modules — Architecture et flux complet

> Analyse du code original (mlep + JEFF). Rien d'inventé, tout vient du code source.

## Vue d'ensemble

Les modules de chasse (stash hunting) sont entrelacés. Voici le diagramme complet :

```
                        ┌─────────────────────────────────────┐
                        │      AutoFlyingRegear              │  ← ORCHESTRATEUR
                        │   (machine à états de regear)      │
                        └──────────────┬──────────────────────┘
                                       │ active/désactive
              ┌────────────────────────┼────────────────────────┐
              │                        │                        │
              ▼                        ▼                        ▼
    ┌──────────────┐         ┌──────────────┐         ┌──────────────┐
    │   Pitch40    │         │  RocketFly   │         │WaypointFollower│
    │ (oscillation)│         │ (roquettes)  │         │  (waypoints)   │
    └──────────────┘         └──────────────┘         └──────────────┘
              │                        │                        │
              └────────────────────────┼────────────────────────┘
                                       │
                                       ▼
                        ┌──────────────────────────────┐
                        │   ElytraRecast               │  ← récupération
                        │ (takeoff + recovery)        │
                        └──────────────────────────────┘
                                       │
                                       ▼
                        ┌──────────────────────────────┐
                        │   Replenish                  │  ← remplit inventaire
                        └──────────────────────────────┘
```

## Les modules en détail

### 1. `AutoFlyingRegear` — L'ORCHESTRATEUR

**Rôle** : Pilote les autres modules de vol. Fait le cycle "vol → regear → vol".

**Modules qu'il contrôle** (stockés dans `flightModules` map) :
- `Replenish` — remplit inventaire
- `AreaLoader` — chunk walking
- `RocketFly` — vol avec roquettes (default)
- `WaypointFollower` — suit un waypoint
- `ElytraBounce` — vol alternatif
- `Pitch40` — oscillation +40/-40
- `TrailFollower` — suit un trail
- `ElytraRecast` — récupération
- `ElytraFly` (Meteor) — mode pitch40 de Meteor

**⚠️ Note importante** : `ElytraAutoFly` (notre module hunterbuddy) **n'est PAS dans cette liste**. AutoFlyingRegear ne le connaît pas.

**États de la machine à états** (30 états) :
1. `IDLE` — au repos
2. `SWAP_TO_CHESTPLATE` — équipe chestplate
3. `DISABLING_MODULES` — désactive tous les autres modules
4. `DROPPING` — drop items au sol
5. `CENTERING_ON_PLATFORM` — se centre
6. `CREATING_INITIAL_PLATFORM` — crée plateforme
7. `CREATING_WALLS` — crée murs
8. `CLEARING_ECHEST_AREA` — dégage espace
9. `ROTATING_FOR_ECHEST` — se tourne
10. `PLACING_ECHEST` — place ender chest
11. `WAIT_ECHEST_PLACE` — attend
12. `OPENING_ECHEST` — ouvre ender chest
13. `TAKING_SHULKER` — prend shulker (roquettes)
14. `WAIT_SHULKER_TAKEN` — attend
15. `POSITIONING_FOR_SHULKER` — se positionne
16. `ROTATING_FOR_SHULKER` — se tourne
17. `PLACING_SHULKER` — place shulker
18. `WAIT_SHULKER_PLACE` — attend
19. `OPENING_SHULKER` — ouvre shulker
20. `TRANSFERRING_ITEMS` — transfère items
21. `BREAKING_SHULKER` — casse shulker
22. `WAIT_SHULKER_BREAK` — attend
23. `WAIT_SHULKER_PICKUP` — attend pickup
24. `OPENING_ECHEST_RETURN` — ouvre ender chest
25. `RETURNING_SHULKER` — retourne shulker
26. `CHECK_NEXT_SHULKER` — vérifie prochain
27. `BREAKING_ECHEST` — casse ender chest
28. `WAIT_ECHEST_BREAK` — attend
29. `RESTORING_ELYTRA` — remet elytra
30. `CLEANUP` — nettoie

**Modules auxiliaires** :
- `MlepScaffold` — pour se débloquer
- `MlepMine` — pour miner
- `AutoEat` (Meteor) — pour manger

### 2. `ElytraAutoFly` — Cycle climb/descend

**Rôle** : Cycle CLIMB → DESCEND pour gagner/perdre de l'altitude.

**Utilise** :
- `Pitch40Classic` — pour l'oscillation +40/-40 et le bound grab

**⚠️ Ne peut PAS fonctionner depuis le sol** :
```java
if (mc.player.getAbilities().allowFlying) {
    enterClimb();
}
```
Il a besoin que `allowFlying` soit déjà true. **Il ne fait PAS de takeoff**.

### 3. `Pitch40Classic` — Oscillation standalone

**Rôle** : Oscille entre +40° (pitch up) et -40° (pitch down) entre `lowerBound` et `upperBound`.

**API publique** :
- `lowerBound` — public, modifiable depuis l'extérieur
- `upperBound` — public, modifiable
- `pitchRate` — vitesse du pitch
- `autoFirework` — pop feu d'artifice
- `fireworkDelay` — délai entre feux (ms)

### 4. `ElytraRecast` — Récupération + Takeoff

**Rôle** : Surveille le vol, récupère si le joueur tombe. Fait aussi le **takeoff**.

**Composants internes** :
- `ElytraTakeoff` (utility) — gère le décollage (jump + equip elytra + fall flying)
- `State` enum (MONITORING, ASCENDING, etc.)

**Méthodes** :
- `requestTakeoff()` — démarre le takeoff manuellement
- `isAscending()` / `isRecovering()` — état

### 5. `WaypointFollower` — Suit un waypoint

**Utilise** :
- `RocketFly` — désactive quand actif
- `Pitch40` — désactive quand actif
- `ElytraRecast` — désactive quand actif
- `AreaLoader` — vérifie si actif pour steering

**Modes** : `Pitch40`, `RocketFly`, `ElytraBounce`, `ElytraFly`

### 6. `TrailFollower` — Suit un trail

**Utilise** :
- `Pitch40` — vérifie si actif
- `RocketFly` — vérifie si actif

### 7. `RocketFly` — Vol avec roquettes

**Rôle** : Maintient Y-level en utilisant des roquettes (style JEFF AFKVanillaFly).

**Settings** : `fireworkDelay` (ms), `useManualY`, `manualYLevel`

### 8. `Replenish` — Replenish inventaire

**Rôle** : Replenish inventaire avec shift-click packets.

**N'utilise pas d'autres modules** — il fonctionne seul.

## Comment les modules se désactivent entre eux

### `WaypointFollower` désactive :
- `RocketFly` (si actif)
- `Pitch40` (si actif)
- `ElytraRecast` (si actif)

### `AutoFlyingRegear` désactive (dans `DISABLING_MODULES`) :
- **TOUS** les modules dans `flightModules` (Replenish, AreaLoader, RocketFly, WaypointFollower, ElytraBounce, Pitch40, TrailFollower, ElytraRecast, ElytraFly)

### `ElytraRecast` peut désactiver :
- Lui-même quand le joueur revient en vol normal

## Comment "chasser" (workflow utilisateur)

### Option 1 : AutoFlyingRegear + RocketFly (RECOMMANDÉ)
1. Activer `RocketFly` → décolle et monte
2. Activer `AutoFlyingRegear` → quand roquettes vides, fait le regear automatique
3. **Aucun setup supplémentaire** — le tout fonctionne automatiquement

### Option 2 : AutoFlyingRegear + WaypointFollower
1. Placer un waypoint dans Xaero
2. Activer `WaypointFollower` + `AutoFlyingRegear`
3. Le bot vole vers le waypoint, refait le plein en route

### Option 3 : AutoFlyingRegear + TrailFollower
1. Créer/avoir un trail
2. Activer `TrailFollower` + `AutoFlyingRegear`
3. Le bot suit le trail

### Option 4 : AutoElytraFly SEUL (NOTRE module, PAS un mlep standard)
- **⚠️ Ne fonctionne PAS avec AutoFlyingRegear** (AutoFlyingRegear ne le connaît pas)
- Ne fait PAS de takeoff depuis le sol
- Utilise `Pitch40Classic` pour l'oscillation

**Quand l'utiliser** :
- Tu veux faire des cycles climb/descend manuels
- Tu controles quand passer CLIMB → DESCEND avec `max-altitude` / `min-altitude`
- Tu utilises ElytraRecast ou un autre module pour le takeoff initial

## Réponse à ta question

> "j'utilsie auto elytra fly. quand il a plus de feu artifice je peux active aussi en meme temps FlyingRegearState quo va desactive le module dessendre remplire fireworks et recommence a vole?"

**Réponse** : **Non, ça ne marche pas comme ça** :
1. `AutoFlyingRegear` **ne connaît pas** `ElytraAutoFly` (il n'est pas dans sa `flightModules`)
2. Si tu actives les deux, `AutoFlyingRegear` va faire `DISABLING_MODULES` qui désactive **Pitch40Classic** (utilisé par ElytraAutoFly)
3. Après le regear, `AutoFlyingRegear` réactivera le module de vol qu'il **connaît** (RocketFly, WaypointFollower, etc.) — **PAS** ElytraAutoFly
4. **ElytraAutoFly ne sera plus actif** après le regear

**Pour ta situation** (AutoElytraFly + regear automatique) :
- Soit utiliser `ElytraRecast` (qui fait le takeoff et la récupération) au lieu d'AutoElytraFly
- Soit ajouter `ElytraAutoFly` à `AutoFlyingRegear.flightModules` (modification custom)

J'attends tes instructions pour la suite.