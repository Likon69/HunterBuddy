package com.hunterbuddy;


import com.hunterbuddy.hud.DimensionCoords;
import com.hunterbuddy.hud.DubCounterHud;
import com.hunterbuddy.hud.ElytraHelperHud;
import com.hunterbuddy.hud.ElytraStatusHud;
import com.hunterbuddy.hud.EntityList;
import com.hunterbuddy.hud.HuntTallyHud;
import com.hunterbuddy.hud.ItemCounterHud;
import com.hunterbuddy.hud.MobInfo;
import com.hunterbuddy.hud.MovementStatusHud;
import com.hunterbuddy.hud.RegearStatusHud;
import com.hunterbuddy.hud.SpeedKMH;
import com.hunterbuddy.hud.SystemStatsHud;
import com.hunterbuddy.hud.TimerSpeedHud;
import com.hunterbuddy.hud.TravelHud;
import com.hunterbuddy.commands.logistics.SetClear;
import com.hunterbuddy.commands.logistics.SetInput;
import com.hunterbuddy.commands.logistics.SetOutput;
import com.hunterbuddy.commands.logistics.StashStatus;
import com.hunterbuddy.modules.AFKVanillaFly;
import com.hunterbuddy.modules.AngleCalculator;
import com.hunterbuddy.modules.AutoEXPPlus;
import com.hunterbuddy.modules.AutoLogPlus;
import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.modules.AutoPortal;
import com.hunterbuddy.modules.CaveAirESP;
import com.hunterbuddy.modules.ChunkRadar;
import com.hunterbuddy.modules.chesttracker.ChestTrackerModule;
import com.hunterbuddy.modules.ClientSideTime;
import com.hunterbuddy.modules.ContainerTooltips;
import com.hunterbuddy.modules.ControlFly;
import com.hunterbuddy.modules.DisconnectSound;
import com.hunterbuddy.modules.ElytraAutoFly;
import com.hunterbuddy.modules.ElytraBounce;
import com.hunterbuddy.modules.ElytraRecast;
import com.hunterbuddy.modules.ElytraSwap;
import com.hunterbuddy.modules.EntityView;
import com.hunterbuddy.modules.FlowESP;
import com.hunterbuddy.modules.GhostContainer;
import com.hunterbuddy.modules.MlepAirPlace;
import com.hunterbuddy.modules.FutureTotem;
import com.hunterbuddy.modules.KillAuraPlus;
import com.hunterbuddy.modules.MlepMine;
import com.hunterbuddy.modules.MlepScaffold;
import com.hunterbuddy.modules.logistics.PearlLoader;
import com.hunterbuddy.modules.logistics.StashMover;
import com.hunterbuddy.modules.logistics.StashMoverSelectionHandler;
import com.hunterbuddy.modules.NoHurtCam;
import com.hunterbuddy.modules.OldChunkNotifier;
import com.hunterbuddy.modules.NoJumpDelay;
import com.hunterbuddy.modules.Phase;
import com.hunterbuddy.modules.Pitch40;
import com.hunterbuddy.modules.Pitch40Classic;
import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.Replenish;
import com.hunterbuddy.modules.RocketBoost;
import com.hunterbuddy.modules.RocketFly;
import com.hunterbuddy.modules.ItemSearchBar;
import com.hunterbuddy.modules.RotationDetector;
import com.hunterbuddy.modules.ShulkerOverviewModule;
import com.hunterbuddy.modules.SignRender;
import com.hunterbuddy.modules.SpawnerDetector;
import com.hunterbuddy.modules.StashFinder;
import com.hunterbuddy.modules.TrailFollower;
import com.hunterbuddy.modules.UnfocusedFpsLimiter;
import com.hunterbuddy.modules.VanityESP;
import com.hunterbuddy.modules.LoreLocator;
import com.hunterbuddy.modules.VisualRangeNotifier;
import com.hunterbuddy.modules.WaypointFollower;
import com.hunterbuddy.modules.YawLock;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderStarterModule;
import com.hunterbuddy.render.HbGlowShader;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;

public class HunterBuddyAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category HUNT_CATEGORY = new Category("Hunt", Items.ENDER_CHEST.getDefaultStack());
    public static final Category VISUALS_CATEGORY = new Category("Visuals", Items.ENDER_EYE.getDefaultStack());
    public static final Category LOGISTICS_CATEGORY = new Category("Logistics", Items.SHULKER_BOX.getDefaultStack());
    public static final Category UTILITY_CATEGORY = new Category("Utility", Items.NETHER_STAR.getDefaultStack());
    public static final HudGroup HUD_GROUP = new HudGroup("HunterBuddy");

    @Override
    public void onInitialize() {
        LOG.info("Initializing HunterBuddy Addon");
        StashMoverSelectionHandler.init();
        MeteorClient.EVENT_BUS.subscribe(HbGlowShader.class);
        com.hunterbuddy.util.SessionStats.init();
        com.hunterbuddy.util.HuntFeed.init();
        com.hunterbuddy.util.ActivityTracker.init();
        com.hunterbuddy.util.TpsSampler.init();
        com.hunterbuddy.util.ChunkStreamSampler.init();
        com.hunterbuddy.util.PingSampler.init();
        com.hunterbuddy.util.LifetimeStats.init();
        MeteorClient.EVENT_BUS.subscribe(new com.hunterbuddy.modules.VisualRangeNotifier.Hooks());
        // Clears RocketBoost's Baritone settings on join when the module is off, so a value left
        // behind by a crash mid-flight cannot make Baritone plan for a boost nothing is applying.
        MeteorClient.EVENT_BUS.subscribe(new com.hunterbuddy.modules.RocketBoost.Hooks());

        // HUDs
        Hud.get().register(ElytraHelperHud.INFO);
        Hud.get().register(SpeedKMH.INFO);
        Hud.get().register(MovementStatusHud.INFO);
        Hud.get().register(DubCounterHud.INFO);
        Hud.get().register(MobInfo.INFO);
        Hud.get().register(ItemCounterHud.INFO);
        Hud.get().register(EntityList.INFO);
        Hud.get().register(DimensionCoords.INFO);
        Hud.get().register(TimerSpeedHud.INFO);
        Hud.get().register(SystemStatsHud.INFO);
        Hud.get().register(ElytraStatusHud.INFO);
        Hud.get().register(RegearStatusHud.INFO);
        Hud.get().register(TravelHud.INFO);
        Hud.get().register(HuntTallyHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.Pitch40CycleHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.FindsTickerHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.SpiralCoverageHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.ThreatBoardHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.TpsCardiogramHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.PingMeterHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.OdometerHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.SessionTimelineHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.SpeedSpectrumHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.DimensionBannerHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.ChunkRadarHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.FollowerCockpitHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.FollowerHeadingHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.FollowerRouteHud.INFO);
        Hud.get().register(com.hunterbuddy.hud.PerformanceHud.INFO);

        // HunterBuddy modules
        // Modules.get().add(new AFKVanillaFly());
        Modules.get().add(new AutoLogPlus());
        Modules.get().add(new AutoEXPPlus());
        Modules.get().add(new ClientSideTime());
        Modules.get().add(new UnfocusedFpsLimiter());
        Modules.get().add(new ShulkerOverviewModule());
        Modules.get().add(new ElytraSwap());
        Modules.get().add(new GhostContainer());
        Modules.get().add(new NoHurtCam());
        Modules.get().add(new NoJumpDelay());
        Modules.get().add(new OldChunkNotifier());
        Modules.get().add(new ChunkRadar());
        Modules.get().add(new ElytraBounce());
        Modules.get().add(new ElytraRecast());
        Modules.get().add(new ControlFly());
        Modules.get().add(new AutoPortal());
        Modules.get().add(new AutoFlyingRegear());
        Modules.get().add(new PearlLoader());
        Modules.get().add(new StashMover());
        Modules.get().add(new WaypointFollower());
        Modules.get().add(new TrailFollower());
        Modules.get().add(new MlepMine());
        Modules.get().add(new KillAuraPlus());
        Modules.get().add(new FutureTotem());
        Modules.get().add(new MlepScaffold());
        Modules.get().add(new Replenish());
        Modules.get().add(new StashFinder());
        Modules.get().add(new SpawnerDetector());
        Modules.get().add(new Pitch40());
        Modules.get().add(new Pitch40Classic());
        Modules.get().add(new RocketFly());
        Modules.get().add(new com.hunterbuddy.bephax.BepBoost());
        Modules.get().add(new com.hunterbuddy.bephax.BepRocketFly());
        Modules.get().add(new YawLock());
        Modules.get().add(new AreaLoader());
        Modules.get().add(new MlepAirPlace());
        Modules.get().add(new AngleCalculator());
        Modules.get().add(new RotationDetector());
        Modules.get().add(new SignRender());
        Modules.get().add(new FlowESP());
        Modules.get().add(new CaveAirESP());
        Modules.get().add(new VanityESP());
        Modules.get().add(new EntityView());

        // HunterBuddy-original modules
        Modules.get().add(new ElytraAutoFly());
        Modules.get().add(new com.hunterbuddy.modules.Shader());
        Modules.get().add(new RocketBoost());
        Modules.get().add(new com.hunterbuddy.modules.chesttracker.ChestTrackerModule());
        Modules.get().add(new ItemSearchBar());
        Modules.get().add(new ContainerTooltips());
        Modules.get().add(new Phase());
        Modules.get().add(new DisconnectSound());
        Modules.get().add(new LoreLocator());
        Modules.get().add(new VisualRangeNotifier());
        Commands.add(new SetInput());
        Commands.add(new SetOutput());
        Commands.add(new StashStatus());
        Commands.add(new SetClear());
        Commands.add(new com.hunterbuddy.commands.AreaLoaderReset());
        Commands.add(new com.hunterbuddy.commands.Trails());
        // ExperienceTraderModule and ExperienceTraderStarterModule are hidden for now
        // (files kept, but not registered in module list).
        // ExperienceTraderModule module = new ExperienceTraderModule(HUNTER_BUDDY_CATEGORY);
        // Modules.get().add(module);
        // Modules.get().add(new ExperienceTraderStarterModule(HUNTER_BUDDY_CATEGORY, module));
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(HUNT_CATEGORY);
        Modules.registerCategory(VISUALS_CATEGORY);
        Modules.registerCategory(LOGISTICS_CATEGORY);
        Modules.registerCategory(UTILITY_CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.hunterbuddy";
    }
}
