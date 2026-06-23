package org.linytech.trader.modules;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AireForce extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Slider pour ajuster la taille du rayon d'exploration
    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Le rayon de la zone d'exploration en chunks.")
        .defaultValue(5)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Variables d'état pour l'exploration
    private ChunkPos startChunkPos;
    private int currentDistance = 1;
    private int direction = 0; // 0 = droite, 1 = haut, 2 = gauche, 3 = bas
    private int stepCount = 0;
    private int exploredChunks = 0; // Nombre de chunks explorés

    public AireForce(Category category) {
        super(category, "aire-force", "Explore tous les chunks dans une zone définie.");
    }

    @Override
    public void onActivate() {
        // Vérifie que le joueur et le monde sont chargés avant d'initialiser le point de départ
        if (mc.player == null || mc.world == null) return;

        startChunkPos = mc.player.getChunkPos(); // Enregistre le chunk de départ
        currentDistance = 1;
        direction = 0;
        stepCount = 0;
        exploredChunks = 0;

        // Démarre l'exploration
        scheduler.scheduleAtFixedRate(this::exploreNextChunk, 0, 1, TimeUnit.SECONDS);
    }

    @EventHandler
    public void onTick() {
        // Vérifie que le joueur et le monde sont chargés avant de continuer
        if (mc.player == null || mc.world == null) return;

        // Arrête le module si tous les chunks dans le rayon ont été explorés
        if (exploredChunks >= Math.pow(radius.get() * 2 + 1, 2)) {
            toggle();
            return;
        }

        // Calcule le chunk à explorer ensuite
        ChunkPos nextChunk = getNextChunkPos();

        // Vérifie si le chunk est chargé ; s'il ne l'est pas, tourne vers lui pour l'explorer
        if (!mc.world.getChunkManager().isChunkLoaded(nextChunk.x, nextChunk.z)) {
            Vec3d directionToChunk = new Vec3d(
                (nextChunk.getStartPos().getX() - mc.player.getX()),
                0,
                (nextChunk.getStartPos().getZ() - mc.player.getZ())
            ).normalize();

            faceDirection(directionToChunk);
        }
    }

    private ChunkPos getNextChunkPos() {
        // Incrémente le compteur d'étapes, et change de direction au bon moment
        stepCount++;

        if (stepCount >= currentDistance) {
            // Change de direction dans l'ordre : droite -> haut -> gauche -> bas
            direction = (direction + 1) % 4;
            stepCount = 0;

            // Augmente la distance après avoir fait deux virages (horizontale et verticale)
            if (direction == 0 || direction == 2) {
                currentDistance++;
            }
        }

        // Calcule la position du chunk suivant
        int x = startChunkPos.x;
        int z = startChunkPos.z;

        switch (direction) {
            case 0: // Droite
                x += currentDistance;
                break;
            case 1: // Haut
                z -= currentDistance;
                break;
            case 2: // Gauche
                x -= currentDistance;
                break;
            case 3: // Bas
                z += currentDistance;
                break;
        }

        // Incrémente le compteur de chunks explorés
        exploredChunks++;

        return new ChunkPos(x, z);
    }

    private void faceDirection(Vec3d direction) {
        // Vérifie que le joueur n'est pas null avant d'ajuster le yaw
        if (mc.player == null) return;

        // Calcule l'angle en yaw pour regarder dans la direction donnée
        double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
        mc.player.setYaw((float) yaw);
    }

    private void exploreNextChunk() {
        // Logique pour explorer le prochain chunk
        // Met à jour la position actuelle en fonction de la direction et de la distance
        // Appelle cette méthode périodiquement pour continuer l'exploration

        // Exemple de mise à jour de la direction et de la distance
        if (stepCount < currentDistance) {
            stepCount++;
        } else {
            stepCount = 0;
            direction = (direction + 1) % 4;
            if (direction == 0 || direction == 2) {
                currentDistance++;
            }
        }

        // Met à jour la position actuelle en fonction de la direction
        switch (direction) {
            case 0: // droite
                startChunkPos = new ChunkPos(startChunkPos.x + 1, startChunkPos.z);
                break;
            case 1: // haut
                startChunkPos = new ChunkPos(startChunkPos.x, startChunkPos.z - 1);
                break;
            case 2: // gauche
                startChunkPos = new ChunkPos(startChunkPos.x - 1, startChunkPos.z);
                break;
            case 3: // bas
                startChunkPos = new ChunkPos(startChunkPos.x, startChunkPos.z + 1);
                break;
        }

        exploredChunks++;
        // Charge le chunk actuel
        mc.world.getChunk(startChunkPos.x, startChunkPos.z, ChunkStatus.FULL, true);
    }

    @Override
    public void onDeactivate() {
        // Arrête le scheduler lorsque le module est désactivé
        scheduler.shutdownNow();
    }
}
