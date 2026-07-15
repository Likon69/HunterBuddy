package com.hunterbuddy.modules.chesttracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestTrackerDataV2 {
   private static final Logger LOGGER = LoggerFactory.getLogger("ChestTracker");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int CURRENT_VERSION = 2;
   private final Map<String, Map<BlockPos, TrackedContainer>> containers;
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
   private final MinecraftClient mc;
   private File dataFile;
   private File backupFile;
   private File tempFile;
   private long lastSaveTime = 0L;
   private int saveFailures = 0;

   public ChestTrackerDataV2() {
      this.containers = new ConcurrentHashMap<>();
      this.mc = MinecraftClient.getInstance();
      this.initializeFiles();
   }

   private void initializeFiles() {
      try {
         String serverIdentifier = this.getServerIdentifier();
         File baseFolder = new File(MeteorClient.FOLDER, "ChestTracker");
         if (!baseFolder.exists() && !baseFolder.mkdirs()) {
            LOGGER.error("Failed to create ChestTracker folder");
         }

         File folder = new File(baseFolder, serverIdentifier);
         if (!folder.exists() && !folder.mkdirs()) {
            LOGGER.error("Failed to create server-specific ChestTracker folder: {}", serverIdentifier);
         }

         this.dataFile = new File(folder, "tracked_containers.json");
         this.backupFile = new File(folder, "tracked_containers.backup.json");
         this.tempFile = new File(folder, "tracked_containers.tmp");
      } catch (Exception e) {
         LOGGER.error("Failed to initialize files", e);
      }
   }

   private String getServerIdentifier() {
      if (this.mc == null) {
         return "unknown";
      } else if (this.mc.getCurrentServerEntry() != null) {
         String address = this.mc.getCurrentServerEntry().address;
         return this.sanitizeFileName(address);
      } else if (this.mc.isInSingleplayer() && this.mc.getServer() != null) {
         String worldName = this.mc.getServer().getSaveProperties().getLevelName();
         return "singleplayer_" + this.sanitizeFileName(worldName);
      } else {
         return "unknown";
      }
   }

   private String sanitizeFileName(String name) {
      return name != null && !name.isEmpty() ? name.replaceAll("[<>:\"/\\\\|?*]", "_").replaceAll("\\s+", "_").toLowerCase() : "unknown";
   }

   public void reinitializeForNewServer() {
      this.initializeFiles();
   }

   public void trackContainer(BlockPos pos, String dimension, String containerType, List<ItemStack> contents) {
      this.lock.writeLock().lock();

      try {
         Map<BlockPos, TrackedContainer> dimContainers = this.containers.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
         TrackedContainer container = dimContainers.get(pos);
         if (container == null) {
            container = new TrackedContainer(pos, dimension, containerType);
            dimContainers.put(pos, container);
         }

         container.updateContents(contents);
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   public TrackedContainer getContainer(BlockPos pos, String dimension) {
      this.lock.readLock().lock();

      try {
         Map<BlockPos, TrackedContainer> dimContainers = this.containers.get(dimension);
         return dimContainers != null ? dimContainers.get(pos) : null;
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public List<TrackedContainer> searchItem(Item item) {
      String currentDim = this.getCurrentDimension();
      return this.searchItem(item, currentDim);
   }

   public List<TrackedContainer> searchItem(Item item, String dimension) {
      this.lock.readLock().lock();

      try {
         Map<BlockPos, TrackedContainer> dimContainers = this.containers.get(dimension);
         return dimContainers == null ? new ArrayList<>() : dimContainers.values().stream().filter(c -> c.containsItem(item)).sorted((a, b) -> {
            String itemId = Registries.ITEM.getId(item).toString();
            return Integer.compare(b.getItemCount(itemId), a.getItemCount(itemId));
         }).collect(Collectors.toList());
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public List<TrackedContainer> getAllContainers() {
      return this.getAllContainers(this.getCurrentDimension());
   }

   public List<TrackedContainer> getAllContainers(String dimension) {
      this.lock.readLock().lock();

      try {
         Map<BlockPos, TrackedContainer> dimContainers = this.containers.get(dimension);
         return dimContainers != null ? new ArrayList<>(dimContainers.values()) : new ArrayList<>();
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public int getTotalContainerCount() {
      this.lock.readLock().lock();

      try {
         return this.containers.values().stream().mapToInt(Map::size).sum();
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public int getCurrentDimensionContainerCount() {
      String dimension = this.getCurrentDimension();
      this.lock.readLock().lock();

      try {
         Map<BlockPos, TrackedContainer> dimContainers = this.containers.get(dimension);
         return dimContainers != null ? dimContainers.size() : 0;
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public int removeEmptyContainers() {
      this.lock.writeLock().lock();

      try {
         int removed = 0;

         for (Map<BlockPos, TrackedContainer> dimContainers : this.containers.values()) {
            Iterator<Entry<BlockPos, TrackedContainer>> it = dimContainers.entrySet().iterator();

            while (it.hasNext()) {
               if (it.next().getValue().isEmpty()) {
                  it.remove();
                  removed++;
               }
            }
         }

         return removed;
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   public int removeOldContainers(int days) {
      this.lock.writeLock().lock();

      try {
         long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
         int removed = 0;

         for (Map<BlockPos, TrackedContainer> dimContainers : this.containers.values()) {
            Iterator<Entry<BlockPos, TrackedContainer>> it = dimContainers.entrySet().iterator();

            while (it.hasNext()) {
               if (it.next().getValue().getLastUpdated() < cutoff) {
                  it.remove();
                  removed++;
               }
            }
         }

         return removed;
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   public void clearAll() {
      this.lock.writeLock().lock();

      try {
         this.containers.clear();
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   public void clearCurrentDimension() {
      String dimension = this.getCurrentDimension();
      this.lock.writeLock().lock();

      try {
         this.containers.remove(dimension);
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   public void saveData() {
      this.lock.readLock().lock();

      try {
         JsonObject root = new JsonObject();
         root.addProperty("version", 2);
         root.addProperty("saveTime", System.currentTimeMillis());
         JsonObject dimensions = new JsonObject();

         for (Entry<String, Map<BlockPos, TrackedContainer>> dimEntry : this.containers.entrySet()) {
            JsonArray dimArray = new JsonArray();

            for (TrackedContainer container : dimEntry.getValue().values()) {
               dimArray.add(container.toJson());
            }

            dimensions.add(dimEntry.getKey(), dimArray);
         }

         root.add("dimensions", dimensions);
         Writer writer = new OutputStreamWriter(new FileOutputStream(this.tempFile), StandardCharsets.UTF_8);

         try {
            GSON.toJson(root, writer);
         } catch (Throwable var14) {
            try {
               writer.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }

            throw var14;
         }

         writer.close();
         if (this.dataFile.exists() && this.dataFile.length() > 0L) {
            Files.copy(this.dataFile.toPath(), this.backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }

         Files.move(this.tempFile.toPath(), this.dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         this.lastSaveTime = System.currentTimeMillis();
         this.saveFailures = 0;
      } catch (Exception e) {
         this.saveFailures++;
         LOGGER.error("Failed to save data (attempt {})", this.saveFailures, e);
         if (this.saveFailures > 3) {
            LOGGER.error("Multiple save failures, data may be lost!");
         }
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public void saveBackup() throws IOException {
      this.lock.readLock().lock();

      try {
         JsonObject root = new JsonObject();
         root.addProperty("version", 2);
         root.addProperty("backupTime", System.currentTimeMillis());
         JsonObject dimensions = new JsonObject();

         for (Entry<String, Map<BlockPos, TrackedContainer>> dimEntry : this.containers.entrySet()) {
            JsonArray dimArray = new JsonArray();

            for (TrackedContainer container : dimEntry.getValue().values()) {
               dimArray.add(container.toJson());
            }

            dimensions.add(dimEntry.getKey(), dimArray);
         }

         root.add("dimensions", dimensions);

         try (Writer writer = new OutputStreamWriter(new FileOutputStream(this.backupFile), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
         }
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public void loadData() {
      this.lock.writeLock().lock();

      try {
         this.containers.clear();
         if (this.loadFromFile(this.dataFile)) {
            LOGGER.info("Loaded data from main file");
            return;
         }

         if (this.loadFromFile(this.backupFile)) {
            LOGGER.warn("Main file corrupted, loaded from backup");
            this.saveData();
            return;
         }

         File oldFile = new File(MeteorClient.FOLDER, "ChestTracker/tracked_containers.json");
         if (!oldFile.exists() || !this.loadFromFile(oldFile)) {
            LOGGER.info("No existing data found, starting fresh");
            return;
         }

         LOGGER.info("Migrated data from old format");
         this.saveData();
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   private boolean loadFromFile(File file) {
      if (file.exists() && file.length() != 0L) {
         try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("version")) {
               root.get("version").getAsInt();
            } else {
               int version = 1;
            }

            if (root.has("dimensions")) {
               JsonObject dimensions = root.getAsJsonObject("dimensions");

               for (Entry<String, JsonElement> dimEntry : dimensions.entrySet()) {
                  String dimension = dimEntry.getKey();
                  JsonArray dimArray = dimEntry.getValue().getAsJsonArray();
                  Map<BlockPos, TrackedContainer> dimContainers = new ConcurrentHashMap<>();

                  for (JsonElement element : dimArray) {
                     try {
                        TrackedContainer container = TrackedContainer.fromJson(element.getAsJsonObject());
                        dimContainers.put(container.getPosition(), container);
                     } catch (Exception e) {
                        LOGGER.warn("Skipped corrupted container entry", e);
                     }
                  }

                  if (!dimContainers.isEmpty()) {
                     this.containers.put(dimension, dimContainers);
                  }
               }
            }

            return true;
         } catch (Exception e) {
            LOGGER.error("Failed to load from file: {}", file.getName(), e);
            return false;
         }
      } else {
         return false;
      }
   }

   public void exportData(String filename) throws IOException {
      this.lock.readLock().lock();

      try {
         File exportFile = new File(new File(MeteorClient.FOLDER, "ChestTracker"), filename);
         JsonObject export = new JsonObject();
         export.addProperty("version", 2);
         export.addProperty("exportTime", System.currentTimeMillis());
         export.addProperty("totalContainers", this.getTotalContainerCount());
         JsonObject dimensions = new JsonObject();

         for (Entry<String, Map<BlockPos, TrackedContainer>> dimEntry : this.containers.entrySet()) {
            JsonArray dimArray = new JsonArray();

            for (TrackedContainer container : dimEntry.getValue().values()) {
               dimArray.add(container.toJson());
            }

            dimensions.add(dimEntry.getKey(), dimArray);
         }

         export.add("dimensions", dimensions);

         try (Writer writer = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
            GSON.toJson(export, writer);
         }
      } finally {
         this.lock.readLock().unlock();
      }
   }

   private String getCurrentDimension() {
      if (this.mc.world == null) {
         return "unknown";
      }

      RegistryKey<World> key = this.mc.world.getRegistryKey();
      return key.getValue().toString();
   }
}
