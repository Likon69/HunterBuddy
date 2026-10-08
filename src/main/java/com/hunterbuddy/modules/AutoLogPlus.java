package com.hunterbuddy.modules;
import com.hunterbuddy.HunterBuddyAddon;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class AutoLogPlus extends Module
{
    public enum YMode {
        Below,
        Above
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> logOnY = sgGeneral.add(new BoolSetting.Builder()
        .name("log-on-y")
        .description("Logs out if you are below a certain Y level.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> yLevel = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-level")
        .defaultValue(256)
        .min(-128)
        .sliderRange(-128, 320)
        .visible(logOnY::get)
        .build()
    );

    private final Setting<Boolean> logArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("log-armor")
        .description("Logs out if your armor goes below a certain durability amount.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-elytra")
        .description("Ignores the elytra when checking armor durability.")
        .defaultValue(false)
        .visible(logArmor::get)
        .build()
    );

    private final Setting<Double> armorPercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("armor-percent")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 100)
        .visible(logArmor::get)
        .build()
    );

    private final Setting<Boolean> logPortal = sgGeneral.add(new BoolSetting.Builder()
        .name("log-on-portal")
        .description("Logs out if you are in a portal for too long.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> portalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("portal-ticks")
        .description("The amount of ticks in a portal before you get kicked (It takes 80 ticks to go through a portal).")
        .defaultValue(30)
        .min(1)
        .sliderMax(70)
        .visible(logPortal::get)
        .build()
    );

    private final Setting<Boolean> logPosition = sgGeneral.add(new BoolSetting.Builder()
        .name("log-position")
        .description("Logs out if you are within x blocks of this position. Y Position is not included")
        .defaultValue(false)
        .build()
    );

    private final Setting<BlockPos> position = sgGeneral.add(new BlockPosSetting.Builder()
        .name("position")
        .description("The position to log out at. Y position is ignored.")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(logPosition::get)
        .build()
    );

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("The distance from the position to log out at.")
        .defaultValue(100)
        .sliderRange(0, 1000)
        .visible(logPosition::get)
        .build()
    );

    private final Setting<Boolean> serverNotResponding = sgGeneral.add(new BoolSetting.Builder()
        .name("server-not-responding")
        .description("Logs out if the server is not responding.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> serverNotRespondingSecs = sgGeneral.add(new DoubleSetting.Builder()
        .name("seconds-not-responding")
        .description("The amount of seconds the server is not responding before you log out.")
        .defaultValue(10)
        .min(1)
        .sliderMax(60)
        .visible(serverNotResponding::get)
        .build()
    );

    private final Setting<Boolean> reconnectAfterNotResponding = sgGeneral.add(new BoolSetting.Builder()
        .name("reconnect-after-not-responding")
        .description("Reconnects after the server is not responding.")
        .defaultValue(false)
        .visible(serverNotResponding::get)
        .build()
    );

    private final Setting<Double> secondsToReconnect = sgGeneral.add(new DoubleSetting.Builder()
        .name("reconnect-seconds")
        .description("The amount of seconds to wait before reconnecting (Will temporarily overwrite Meteor's AutoReconnect.")
        .defaultValue(60)
        .min(10)
        .sliderMax(60 * 5)
        .visible(() -> reconnectAfterNotResponding.get() && serverNotResponding.get())
        .build()
    );

    private final Setting<Boolean> illegalDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("illegal-disconnect")
        .description("Disconnects by sending a chat message containing the NUL character.")
        .defaultValue(false)
        .build()
    );

    private final SettingGroup sgDisconnect = settings.createGroup("General Options");
    private final SettingGroup sgHealth = settings.createGroup("Health");
    private final SettingGroup sgTriggers = settings.createGroup("Triggers");
    private final SettingGroup sgProximity = settings.createGroup("Proximity");

    private final Setting<Boolean> autoToggle = sgDisconnect.add(new BoolSetting.Builder()
        .name("auto-toggle").description("Disable AutoLogPlus after it disconnects you.")
        .defaultValue(false).build());

    private final Setting<Boolean> stopAutoReconnect = sgDisconnect.add(new BoolSetting.Builder()
        .name("stop-auto-reconnect").description("Disable AutoReconnect when disconnecting, except for requested server-timeout reconnections.")
        .defaultValue(true).build());

    private final Setting<Boolean> timestamp = sgDisconnect.add(new BoolSetting.Builder()
        .name("timestamp").description("Add the local time to the disconnect reason.")
        .defaultValue(false).build());

    private final Setting<Boolean> logCoords = sgDisconnect.add(new BoolSetting.Builder()
        .name("log-coords").description("Add X Y Z and the dimension to the disconnect reason.")
        .defaultValue(false).build());

    private final Setting<Boolean> logOnHealth = sgHealth.add(new BoolSetting.Builder()
        .name("log-on-health").description("Disconnect when health plus absorption reaches the threshold.")
        .defaultValue(false).build());

    private final Setting<Double> healthAmount = sgHealth.add(new DoubleSetting.Builder()
        .name("health-amount").description("Health plus absorption threshold, in health points.")
        .defaultValue(6.0).min(0.0).sliderRange(0.0, 36.0).visible(logOnHealth::get).build());

    private final Setting<Boolean> totemCheck = sgHealth.add(new BoolSetting.Builder()
        .name("totem-check").description("Only disconnect for health when your totem count is at or below the threshold.")
        .defaultValue(false).visible(logOnHealth::get).build());

    private final Setting<Integer> totemThreshold = sgHealth.add(new IntSetting.Builder()
        .name("totem-threshold").description("Maximum number of inventory and offhand totems allowing a health disconnect.")
        .defaultValue(0).min(0).sliderRange(0, 36).visible(() -> logOnHealth.get() && totemCheck.get()).build());

    private final Setting<Boolean> totemPop = sgTriggers.add(new BoolSetting.Builder()
        .name("totem-pop").description("Disconnect when your own totem activates.")
        .defaultValue(false).build());

    private final Setting<Boolean> lethalCrystal = sgTriggers.add(new BoolSetting.Builder()
        .name("lethal-crystal").description("Disconnect when a nearby End crystal could deal lethal damage.")
        .defaultValue(false).build());

    private final Setting<Double> crystalRange = sgTriggers.add(new DoubleSetting.Builder()
        .name("crystal-range").description("Range for lethal End crystal checks.")
        .defaultValue(6.0).min(0.0).sliderRange(0.0, 12.0).visible(lethalCrystal::get).build());

    private final Setting<Boolean> thundering = sgTriggers.add(new BoolSetting.Builder()
        .name("thundering").description("Disconnect when a thunderstorm starts.")
        .defaultValue(false).build());

    private final Setting<YMode> yMode = sgTriggers.add(new EnumSetting.Builder<YMode>()
        .name("y-mode").description("Which side of y-level triggers log-on-y.")
        .defaultValue(YMode.Below).visible(logOnY::get).build());

    private final Setting<Boolean> logElytra = sgTriggers.add(new BoolSetting.Builder()
        .name("log-elytra").description("Disconnect when the equipped elytra has too few uses left.")
        .defaultValue(false).build());

    private final Setting<Integer> elytraDurability = sgTriggers.add(new IntSetting.Builder()
        .name("elytra-durability").description("Minimum remaining elytra uses before disconnecting.")
        .defaultValue(10).min(0).sliderRange(0, 100).visible(logElytra::get).build());

    private final Setting<Boolean> proxPlayers = sgProximity.add(new BoolSetting.Builder()
        .name("prox-players").description("Disconnect when another player is in proximity range.")
        .defaultValue(false).build());

    private final Setting<Boolean> ignoreFriends = sgProximity.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Exclude Meteor friends from player proximity.")
        .defaultValue(true).visible(proxPlayers::get).build());

    private final Setting<Boolean> proxHostiles = sgProximity.add(new BoolSetting.Builder()
        .name("prox-hostiles").description("Disconnect near non-neutral Monster or MONSTER spawn-group mobs.")
        .defaultValue(false).build());

    private final Setting<Boolean> proxNeutrals = sgProximity.add(new BoolSetting.Builder()
        .name("prox-neutrals").description("Disconnect near mobs implementing Minecraft's Angerable interface.")
        .defaultValue(false).build());

    private final Setting<Boolean> proxPassives = sgProximity.add(new BoolSetting.Builder()
        .name("prox-passives").description("Disconnect near other mobs in a peaceful spawn group.")
        .defaultValue(false).build());

    private final Setting<Boolean> proxEndCrystals = sgProximity.add(new BoolSetting.Builder()
        .name("prox-end-crystals").description("Disconnect near End crystals.")
        .defaultValue(false).build());

    private final Setting<Boolean> proxPortal = sgProximity.add(new BoolSetting.Builder()
        .name("prox-portal").description("Disconnect near Nether or End portal blocks, scanned incrementally.")
        .defaultValue(false).build());

    private final Setting<Boolean> proxTntMinecarts = sgProximity.add(new BoolSetting.Builder()
        .name("prox-tnt-minecarts").description("Disconnect near TNT minecarts.")
        .defaultValue(false).build());

    private final Setting<Double> proxRange = sgProximity.add(new DoubleSetting.Builder()
        .name("prox-range").description("Shared proximity range, in blocks.")
        .defaultValue(8.0).range(0.0, 32.0).sliderRange(0.0, 32.0).build());

    public AutoLogPlus()
    {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "auto-log-plus", "Provides some additional triggers to log out.");
    }

    @Override
    public void onActivate() {
        currPortalTicks = 0;
        statusPackets.clear();
        wasThundering = mc.world != null && mc.world.isThundering();
        portalPositions = null;
        portalScanCenter = null;
        if (waitingForReconnection)
        {
            waitingForReconnection = false;
            AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
            Setting<Double> delay = ((Setting<Double>)autoReconnect.settings.get("delay"));
            delay.set(oldDelay);
            if (!autoReconnectEnabled && autoReconnect.isActive())
            {
                autoReconnect.toggle();
            }
        }

    }

    private int currPortalTicks = 0;
    private double oldDelay;
    private boolean autoReconnectEnabled;
    private boolean waitingForReconnection = false;
    private final ConcurrentLinkedQueue<EntityStatusS2CPacket> statusPackets = new ConcurrentLinkedQueue<>();
    private boolean wasThundering;
    private Iterator<BlockPos> portalPositions;
    private BlockPos portalScanCenter;
    private int portalScanRadius;

    @EventHandler
    private void onTick(TickEvent.Post event)
    {
        if (mc.player == null || mc.player.getAbilities().allowFlying) {
            statusPackets.clear();
            portalPositions = null;
            return;
        }
        if (mc.world == null) {
            statusPackets.clear();
            portalPositions = null;
            return;
        }
        boolean storm = mc.world.isThundering();
        boolean stormStarted = storm && !wasThundering;
        wasThundering = storm;

        if (serverNotResponding.get() && !waitingForReconnection)
        {
            if (TickRate.INSTANCE.getTimeSinceLastTick() > serverNotRespondingSecs.get())
            {
                if (reconnectAfterNotResponding.get())
                {
                    AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                    autoReconnectEnabled = autoReconnect.isActive();
                    Setting<Double> delay = ((Setting<Double>)autoReconnect.settings.get("delay"));
                    oldDelay = delay.get();
                    delay.set(secondsToReconnect.get());
                    if (!autoReconnectEnabled)
                    {
                        autoReconnect.toggle();
                    }
                    waitingForReconnection = true;
                }
                logOut("Server was not responding for " + serverNotRespondingSecs.get() + " seconds.", !reconnectAfterNotResponding.get() && stopAutoReconnect.get());
                return;
            }
        }

        if (logPortal.get() && mc.player.portalManager != null)
        {
            if (mc.player.portalManager.isInPortal())
            {
                currPortalTicks++;
                if (currPortalTicks > portalTicks.get())
                {
                    logOut("Player was in a portal for " + currPortalTicks + " ticks.", stopAutoReconnect.get());
                    return;
                }
            }
            else
            {
                currPortalTicks = 0;
            }
        }

        if (logOnY.get() && (yMode.get() == YMode.Above ? mc.player.getY() > yLevel.get() : mc.player.getY() < yLevel.get()))
        {
            logOut("Player was at Y=" + mc.player.getY() + " which is " + yMode.get().name().toLowerCase(java.util.Locale.ROOT) + " your limit of Y=" + yLevel.get(), stopAutoReconnect.get());
            return;
        }
        if (logArmor.get())
        {
            for (int i = 0; i < 4; i++)
            {
                ItemStack armorPiece = mc.player.getInventory().getStack(SlotUtils.ARMOR_START + i);
                if (ignoreElytra.get() && armorPiece.getItem() == Items.ELYTRA) {
                    continue;
                }
                if (armorPiece.isDamageable())
                {
                    int max = armorPiece.getMaxDamage();
                    int current = armorPiece.getDamage();
                    double percentUndamaged = 100 - ((double) current / max) * 100;
                    if (percentUndamaged < armorPercent.get())
                    {
                        logOut("You had low armor", stopAutoReconnect.get());
                        return;
                    }
                }
            }
        }
        if (logPosition.get())
        {
            double distanceToTarget = mc.player.getEntityPos().multiply(1,0,1).distanceTo(position.get().toCenterPos().multiply(1,0,1));
            if (distanceToTarget < distance.get())
            {
                logOut("Player was within " + distanceToTarget + " blocks of the target position.", stopAutoReconnect.get());
                return;
            }
        }
        boolean popped = false;
        EntityStatusS2CPacket status;
        while ((status = statusPackets.poll()) != null) {
            if (status.getEntity(mc.world) == mc.player) {
                popped = true;
            }
        }
        double health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (logOnHealth.get() && health <= healthAmount.get()
            && (!totemCheck.get() || InvUtils.find(Items.TOTEM_OF_UNDYING).count() <= totemThreshold.get())) {
            logOut("Health plus absorption was " + health + ".", stopAutoReconnect.get());
            return;
        }
        if (totemPop.get() && popped) {
            logOut("Your totem activated.", stopAutoReconnect.get());
            return;
        }
        if (lethalCrystal.get() || proxPlayers.get() || proxHostiles.get() || proxNeutrals.get()
            || proxPassives.get() || proxEndCrystals.get() || proxTntMinecarts.get()) {
            double proximitySquared = proxRange.get() * proxRange.get();
            double crystalSquared = crystalRange.get() * crystalRange.get();
            for (Entity entity : mc.world.getEntities()) {
                if (entity == mc.player || entity.isRemoved()) {
                    continue;
                }
                double rangeSquared = mc.player.squaredDistanceTo(entity);
                if (lethalCrystal.get() && entity instanceof EndCrystalEntity && rangeSquared <= crystalSquared
                    && DamageUtils.crystalDamage(mc.player, entity.getEntityPos()) >= health) {
                    logOut("An End crystal in range could deal lethal damage.", stopAutoReconnect.get());
                    return;
                }
                if (rangeSquared <= proximitySquared) {
                    String reason = proximityReason(entity);
                    if (reason != null) {
                        logOut(reason, stopAutoReconnect.get());
                        return;
                    }
                }
            }
        }
        if (thundering.get() && stormStarted) {
            logOut("A thunderstorm started.", stopAutoReconnect.get());
            return;
        }
        if (logElytra.get()) {
            ItemStack elytra = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (elytra.isOf(Items.ELYTRA) && elytra.getMaxDamage() - elytra.getDamage() <= elytraDurability.get()) {
                logOut("Equipped elytra had " + (elytra.getMaxDamage() - elytra.getDamage()) + " uses remaining.", stopAutoReconnect.get());
                return;
            }
        }
        if (proxPortal.get()) {
            if (portalNearby()) {
                logOut("Nether or End portal in proximity range.", stopAutoReconnect.get());
                return;
            }
        } else {
            portalPositions = null;
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (totemPop.get() && event.packet instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
            statusPackets.add(packet);
        }
    }

    private String proximityReason(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            return proxPlayers.get() && (!ignoreFriends.get() || !Friends.get().isFriend(player)) ? "Player in proximity range." : null;
        }
        if (entity instanceof EndCrystalEntity) {
            return proxEndCrystals.get() ? "End crystal in proximity range." : null;
        }
        if (entity instanceof TntMinecartEntity) {
            return proxTntMinecarts.get() ? "TNT minecart in proximity range." : null;
        }
        if (entity instanceof Angerable) {
            return proxNeutrals.get() ? "Neutral mob in proximity range." : null;
        }
        if (entity instanceof MobEntity) {
            SpawnGroup group = entity.getType().getSpawnGroup();
            if (entity instanceof Monster || group == SpawnGroup.MONSTER) {
                return proxHostiles.get() ? "Hostile mob in proximity range." : null;
            }
            if (group.isPeaceful()) {
                return proxPassives.get() ? "Passive mob in proximity range." : null;
            }
        }
        return null;
    }

    private boolean portalNearby() {
        BlockPos center = mc.player.getBlockPos();
        int radius = (int) Math.ceil(proxRange.get()) + 1;
        if (portalPositions == null || !center.equals(portalScanCenter) || radius != portalScanRadius) {
            portalScanCenter = center;
            portalScanRadius = radius;
            portalPositions = BlockPos.iterateOutwards(center, radius, radius, radius).iterator();
        }
        double rangeSquared = proxRange.get() * proxRange.get();
        for (int scanned = 0; scanned < 2048 && portalPositions.hasNext(); scanned++) {
            BlockPos pos = portalPositions.next();
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > rangeSquared) {
                continue;
            }
            var block = mc.world.getBlockState(pos);
            if (block.isOf(Blocks.NETHER_PORTAL) || block.isOf(Blocks.END_PORTAL)) {
                return true;
            }
        }
        if (!portalPositions.hasNext()) {
            portalPositions = null;
        }
        return false;
    }

    private void logOut(String reason, boolean turnOffReconnect)
    {
        if (mc.player == null) {
            return;
        }
        if (turnOffReconnect)
        {
            if (Modules.get().get(AutoReconnect.class).isActive())
            {
                Modules.get().get(AutoReconnect.class).toggle();
            }
        }

        String message = "[AutoLogPlus] ";
        if (timestamp.get()) {
            message += "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] ";
        }
        message += reason;
        if (logCoords.get() && mc.world != null) {
            BlockPos pos = mc.player.getBlockPos();
            message += " [" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + mc.world.getRegistryKey().getValue() + "]";
        }
        HunterBuddyAddon.LOG.info("{}", message);
        if (illegalDisconnect.get())
        {
            mc.player.networkHandler.sendChatMessage(String.valueOf((char)0));
        }
        else
        {
            mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal(message)));
        }
        if (autoToggle.get() && isActive()) {
            toggle();
        }
    }
}
