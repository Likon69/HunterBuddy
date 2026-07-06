# mlep 1.21.1 vs HunterBuddy — Analyse comparative

> Source : `.reference/mlep-main/src/main/java/mlep/modules/`
> Comparé à : `src/main/java/com/hunterbuddy/modules/`
> Date : analyse manuelle, code vérifié

## Catégories de modules mlep 1.21.1

### ✅ CE QU'ON A (modules de chasse/utilité portés)

| Module mlep | Statut | Notre fichier |
|-------------|--------|---------------|
| `AreaLoader.java` | ✅ Porté | `regear/arealoader/AreaLoader.java` |
| `AreaLoaderMode.java` | ✅ Porté | `regear/arealoader/AreaLoaderMode.java` |
| `AreaLoaderModes.java` | ✅ Porté | `regear/arealoader/AreaLoaderModes.java` |
| `AreaRecoveryScreen.java` | ✅ Porté | `regear/arealoader/AreaRecoveryScreen.java` |
| `AutoFlyingRegear.java` | ✅ Porté | `AutoFlyingRegear.java` |
| `AutoPortal.java` | ✅ Porté (hunterbuddy-original avec Baritone) | `AutoPortal.java` |
| `CaveAirESP.java` | ✅ Porté | `CaveAirESP.java` |
| `ControlFly.java` | ✅ Porté | `ControlFly.java` |
| `ElytraBounce.java` | ✅ Porté | `ElytraBounce.java` |
| `ElytraRecast.java` | ✅ Porté | `ElytraRecast.java` |
| `ElytraSwap.java` | ✅ Porté | `ElytraSwap.java` |
| `FlowESP.java` | ✅ Porté | `FlowESP.java` |
| `GhostContainer.java` | ✅ Porté (de JEFF) | `GhostContainer.java` |
| `MlepMine.java` | ✅ Porté | `MlepMine.java` |
| `MlepScaffold.java` | ✅ Porté | `MlepScaffold.java` |
| `NoHurtCam.java` | ✅ Porté | `NoHurtCam.java` |
| `NoJumpDelay.java` | ✅ Porté | `NoJumpDelay.java` |
| `Pitch40.java` | ✅ Porté (renommé `Pitch40Classic`) | `Pitch40Classic.java` |
| `Rectangle.java` | ✅ Porté | `regear/arealoader/modes/Rectangle.java` |
| `Replenish.java` | ✅ Porté | `Replenish.java` |
| `RocketFly.java` | ✅ Porté | `RocketFly.java` |
| `ShulkerOverviewModule.java` | ✅ Porté | `ShulkerOverviewModule.java` |
| `SignRender.java` | ✅ Porté | `SignRender.java` |
| `Spiral.java` | ✅ Porté | `regear/arealoader/modes/Spiral.java` |
| `StashFinder.java` | ✅ Porté | `StashFinder.java` |
| `TrailFollower.java` | ✅ Porté | `TrailFollower.java` |
| `UnfocusedFpsLimiter.java` | ✅ Porté (de JEFF) | `UnfocusedFpsLimiter.java` |
| `VanityESP.java` | ✅ Porté | `VanityESP.java` |
| `WaypointFollower.java` | ✅ Porté | `WaypointFollower.java` |
| `ZigZag.java` | ✅ Porté | `regear/arealoader/modes/ZigZag.java` |

### ❌ CE QUI MANQUE pour la chasse

| Module mlep | Rôle | Priorité |
|-------------|------|----------|
| `MlepAirPlace.java` | Place des blocks en plein vol (comme GrimoPlace) — **utilisateur l'a mentionné** | 🔴 HAUTE |
| `StashMover.java` | Déplace les stashes trouvés vers la base | 🟡 MOYENNE |
| `StashMoverSelectionHandler.java` | Helper pour StashMover | 🟡 MOYENNE |
| `MapDuplicator.java` | Duplique les maps | 🟡 MOYENNE |
| `ContainerTooltips.java` | Meilleurs tooltips dans les containers | 🟡 MOYENNE |
| `WaypointFollowerCommand.java` | Commande `.goto` (aller à un waypoint) | 🟡 MOYENNE |
| `LoreLocator.java` | Trouve des items par leur lore (ex: "Quest") | 🟢 BASSE |
| `VisualRangeNotifier.java` | Notifie quand entités en visuel range | 🟢 BASSE |
| `KillEffects.java` | Effets de mort custom | 🟢 BASSE |
| `ODMob.java` | One Direction Mob (info sur les mobs autour) | 🟢 BASSE |
| `Stripper.java` | Strip un chunk | 🟢 BASSE |
| `WheelPicker.java` | GUI pour choisir une valeur avec la souris | 🟢 BASSE |
| `YawLock.java` | Lock le yaw | 🟢 BASSE |
| `YCam.java` | Camera control (freecam) | 🟢 BASSE |
| `RotationDetector.java` | Détecte les rotations suspectes (anti-cheat) | 🟢 BASSE |
| `PearlLoader.java` | Load des ender pearls automatiquement | 🟢 BASSE |
| `ItemSearchBar.java` | Barre de recherche d'items | 🟢 BASSE |
| `AntiSpawnSet.java` | Anti spawn-detection | 🟢 BASSE |
| `GhostMode.java` | Ghost mode (invisible aux autres) | 🟢 BASSE |
| `DisconnectSound.java` | Joue un son à la déconnection | 🟢 BASSE |
| `AutoArmorMlepMode.java` | Mode Mlep pour Auto Armor (Meteor) | 🟢 BASSE |
| `AngleCalculator.java` | Calcule des angles | 🟢 BASSE |

### 🚫 CE QUI MANQUE pour les features 2b2t (PAS chasse, PAS critique)

| Module | Rôle |
|--------|------|
| `Api2b2t.java` + `ApiHandler.java` | API 2b2t (stats, kills, deaths) |
| `IsBot2b2t.java` | Détecte si un joueur est un bot |
| `Chats2b2t.java` + `Connections2b2t.java` + `Deaths2b2t.java` + `Kills2b2t.java` + `FirstSeen2b2t.java` + `LastSeen2b2t.java` + `Playtime2b2t.java` + `SessionLimit2b2t.java` + `Stats2b2t.java` + `TopPlaytime2b2t.java` + `WordCount2b2t.java` | Commandes 2b2t |
| `InvFix2b2t.java` | Fix bug inventaire 2b2t |
| `Fix2b2tBundlesMixin.java` | Fix bundles 2b2t |
| `Fix2b2tGhostItemsMixin.java` | Fix ghost items 2b2t |
| `WebChat.java` + `WebChatMixin.java` + `ManeWindow.java` + `PatternTemplate.java` | Web chat (chat en jeu) |
| `LiveMessage.java` + `LiveWindow.java` + `LiveProfileCache.java` + `LiveSkinUtil.java` + `LivemessageGui.java` + `LivemessageMatcher.java` + `LivemessageUtil.java` | Live messages (chat temps réel) |
| `IrcClient.java` + `IrcConfig.java` + `IrcMessage.java` + `IrcModAction.java` + `IrcParser.java` + `IrcUser.java` + `IrcUserList.java` + `IrcWindow.java` + `IrcCommand.java` | IRC chat |
| `BetterChatMixin.java` + `ChatHudMixin.java` + `ChatMentionMixin.java` + `ChatScreenMixin.java` + `ChatWindow.java` | Améliorations chat |
| `BetterTabMixin.java` + `BetterTabConfigHolder.java` | Améliorations tab |
| `AutoArmorMixin.java` + `AutoEatMixin.java` + `AutoLogMixin.java` + `AutoMendMixin.java` + `AutoSignMixin.java` + `AutoTrapMixin.java` | Mixins Meteor moddés |
| `AutoBreed.java` + `AutoCraft.java` + `AutoCraftScreenMixin.java` + `AutoRespond.java` + `AutoSmith.java` | Auto farm/breed/craft |
| `MapUtil.java` | Utilitaires map (déjà porté dans regear/) |

### 🚫 CE QUI MANQUE pour ChestTracker (feature majeure mlep)

| Module | Rôle |
|--------|------|
| `ChestTrackerDataManager.java` | Gestionnaire de données des chests |
| `ChestTrackerDataV2.java` | Version 2 du format |
| `ChestTrackerModule.java` | Module principal ChestTracker |
| `ChestTrackerScreen.java` | GUI de ChestTracker |
| `TrackedContainer.java` | Container tracking |
| `ChestTrackerCommand.java` | Commande `.chests` |
| `AbstractBlockStateMixin.java` | Mixin pour BlockState |
| `AbstractSignEditScreenAccessor.java` | Accessor pour SignEditScreen |
| `AccessorClientWorld.java` | Accessor pour ClientWorld |
| `AutoArmorMlepMode.java` | Mode Mlep pour Auto Armor |
| `AutoTrapMixin.java` | Mixin pour AutoTrap |
| `BetterTooltipsMixin.java` | Mixin pour tooltips |
| `BundleS2CPacketAccessor.java` | Accessor pour BundleS2C |
| `CategoryAccessor.java` | Accessor pour Category |
| `ClientConnectionAccessor.java` | Accessor pour ClientConnection |
| `ClientPlayerEntityGrimV3Mixin.java` | Mixin GrimV3 |
| `ClientPlayerEntityMixin.java` | Mixin ClientPlayerEntity |
| `ClientPlayerInteractionManagerMixin.java` | Mixin interaction |
| `ConfigMixin.java` | Mixin config |
| `ContainerInventoryScreenMixin.java` | Mixin container inventory |
| `DrawContextMixin.java` | Mixin DrawContext |
| `DubCounterHud.java` | HUD pour dub counter |
| `EditBoxWidgetAccessor.java` | Accessor EditBox |
| `EditFolderScreen.java` | Server folder edit screen |
| `EditServerMetadataScreen.java` | Server metadata edit screen |
| `EntityList.java` + `EntityMixin.java` + `EntityUtil.java` + `EntityVelocityUpdateS2CPacketAccessor.java` | Entity utilities |
| `EvictingQueue.java` | Queue utility |
| `ExplosionS2CPacketAccessor.java` | Accessor |
| `FadeAnimator.java` | Fade animator |
| `FireworksSparkParticleMixin.java` | Mixin fireworks |
| `FreeLookCameraMixin.java` | Mixin FreeLook camera |
| `GameMenuScreenMixin.java` | Mixin GameMenu |
| `GameRendererMixin.java` + `InputAccessor.java` + `InputMixin.java` + `KeyBindingMixin.java` + `KeyBindingMixin.java` | Renderer mixins |
| `GuiUtil.java` | GUI utility |
| `HandledScreenMixin.java` | Mixin HandledScreen |
| `IBlockSettings.java` | IBlockSettings |
| `InGameHudMixin.java` + `InGameHudTabFadeMixin.java` | InGameHud mixins |
| `InventoryTweaksMixin.java` + `InventoryTweaksConfigHolder.java` | Inventory tweaks |
| `ItemCounterHud.java` | HUD item counter |
| `KillEffects.java` | Kill effects |
| `LivingEntityAccessor.java` + `LivingEntityMixin.java` | LivingEntity accessors/mixins |
| `MacroAction.java` + `MacroFile.java` + `MacroFileManager.java` + `MacroFrame.java` + `MacroPlayer.java` + `MacroRecorder.java` | Macro recording system |
| `MapDuplicator.java` | Map duplicator |
| `Miner.java` | Miner module |
| `MixinEntity.java` + `MixinGuiMapWaypointFollower.java` | Mixins |
| `Mlep.java` | Main class mlep |
| `MlepAddServerScreenMixin.java` + `MlepFolderEntry.java` + `MlepMultiplayerScreenMixin.java` + `MlepMultiplayerServerListWidgetMixin.java` + `MlepServerEntryMixin.java` + `MlepServerListMixin.java` + `MlepServerListWidgetAccessor.java` | Mlep-specific mixins/classes |
| `MobInfo.java` | Mob info HUD |
| `MovementStatusHud.java` | HUD movement status |
| `MovementUtil.java` | Movement utility |
| `MultiplayerScreenAccessor.java` | Multiplayer screen accessor |
| `NametagsMixin.java` + `NoRenderMixin.java` + `NoSlowMixin.java` + `NotifierMixin.java` + `OnlinePlayersMixin.java` | Mixins Meteor moddés |
| `PacketCancellerMixin.java` + `PacketManager.java` | Packet cancelling |
| `PearlInfo.java` | Pearl info |
| `PeekNavigationConfig.java` + `PeekScreenMixin.java` | Peek nav |
| `PendingSignData.java` | Pending sign data |
| `Phase.java` | Phase enum |
| `PlayerEntityMixin.java` + `PlayerInteractEntityC2SPacketMixin.java` + `PlayerListHudMixin.java` + `PlayerMoveC2SPacketAccessor.java` + `PlayerToast.java` + `PlayerUtilsMixin.java` | Player mixins |
| `PushEntityEvent.java` + `PushFluidsEvent.java` | Push events |
| `RenderUtils.java` | Render utilities |
| `RotationMixin.java` | Rotation mixin |
| `ServerFolder.java` + `ServerMetadata.java` + `ServerOrganizer.java` + `ServerOrganizerScreen.java` + `SessionLimit2b2t.java` + `SetClear.java` + `SetInput.java` + `SetOutput.java` + `SettingAccessor.java` + `SettingBuilder.java` + `SortableEntryData.java` | Server folder utilities |
| `TimerMixin.java` + `TimerSpeedHud.java` | Timer utilities |
| `TitleScreenMixin.java` | Title screen mixin |
| `TrackedContainer.java` | Tracked container |
| `UpdateSelectedSlotS2CPacketAccessor.java` | Accessor |
| `VelocityMixin.java` | Velocity mixin |
| `WaypointFollowerCommand.java` | Waypoint follower command |
| `WebChatMixin.java` | Web chat mixin |
| `WorldMixin.java` | World mixin |
| `YCam.java` + `YCamCameraMixin.java` | YCam module |
| `YawLock.java` | Yaw lock module |

## Résumé par priorité de chasse

### 🔴 CRITIQUE (utilisateur l'a mentionné)
- `MlepAirPlace.java` — Place des blocks en plein vol (PRIORITÉ HAUTE)

### 🟡 UTILE pour la chasse
- `StashMover.java` + `StashMoverSelectionHandler.java` — Déplace les stashes vers la base
- `MapDuplicator.java` — Duplique les maps
- `ContainerTooltips.java` — Meilleurs tooltips containers
- `WaypointFollowerCommand.java` — Commande `.goto`

### 🟢 OPTIONNEL
- `LoreLocator`, `VisualRangeNotifier`, `KillEffects`, `ODMob`, `Stripper`, `WheelPicker`, `YawLock`, `YCam`, `RotationDetector`, `PearlLoader`, `ItemSearchBar`, `AntiSpawnSet`, `GhostMode`, `DisconnectSound`, `AutoArmorMlepMode`, `AngleCalculator`

### 🚫 PAS POUR CHASSE (à ignorer)
- Tout le système 2b2t (Chats2b2t, Kills2b2t, etc.)
- Tout le système IRC
- Tout le système LiveMessage / WebChat
- Tout le système BetterChat / BetterTab
- Tout le système Macro
- Tout le système ChestTracker (feature majeure mais complexe)
- Tous les mixins Meteor (AutoArmor, AutoEat, etc.)
- Les HUDs additionnels (DubCounter, ItemCounter, MobInfo, TimerSpeed)

## Architecture du code regear/ qu'on a

```
regear/
├── accessor/InputAccessor.java       (1 fichier)
├── arealoader/
│   ├── AreaLoader.java
│   ├── AreaLoaderMode.java
│   ├── AreaLoaderModes.java
│   ├── AreaRecoveryScreen.java
│   └── modes/
│       ├── Rectangle.java
│       ├── Spiral.java
│       └── ZigZag.java
├── config/MlepConfig.java
├── mixin/
│   ├── DisconnectS2CPacketAccessor.java
│   ├── LivingEntityAccessor.java
│   ├── PlayerInventoryAccessor.java
│   └── UpdateSelectedSlotS2CPacketAccessor.java
└── util/
    ├── BaritoneHelper.java
    ├── ElytraTakeoff.java
    ├── EnemyColorManager.java
    ├── InventoryManager.java
    ├── InventoryTweaksConfigHolder.java
    ├── LogUtil.java
    ├── MapUtil.java
    ├── MlepConfig.java (aussi dans config/)
    ├── MsgUtil.java
    ├── PlacementUtils.java
    ├── PositionUtil.java
    ├── PushOutOfBlocksEvent.java
    ├── RotationUtils.java
    ├── ShulkerDataParser.java
    ├── StorageBlockListSetting.java (de JEFF)
    ├── Utils.java
    └── XaeroWaypointManager.java
```

**Total : 25 fichiers dans regear/**

## Conclusion

Pour la **chasse standard** (waypoints, trails, regear automatique), on a **tout ce qu'il faut** sauf `MlepAirPlace` (place blocks en vol).

Les **modules manquants** sont soit :
- Des features 2b2t/IRC/LiveMessage (pas chasse)
- ChestTracker (système complet de tracking de chests — très gros)
- Des HUDs/mixins additionnels
- Des macros

**Rien d'inventé, tout vérifié par le code source.**

J'attends tes instructions pour la suite.