package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.regear.arealoader.modes.Rectangle;
import com.hunterbuddy.modules.regear.arealoader.modes.Spiral;
import com.hunterbuddy.modules.regear.arealoader.modes.ZigZag;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class AreaRecoveryScreen extends WindowScreen {
   private final AreaLoader searchArea;
   private WDropdown<AreaLoaderModes> modeDropdown;
   private WTextBox originXEdit;
   private WTextBox originZEdit;
   private WIntEdit gapEdit;
   private WTextBox currentXEdit;
   private WTextBox currentZEdit;
   private WSection spiralSection;
   private WSection rectangleSection;
   private WTextBox rectEndXEdit;
   private WTextBox rectEndZEdit;
   private WSection zigzagSection;
   private WIntEdit zigzagLegLengthEdit;
   private WIntEdit zigzagRowGapEdit;
   private WDropdown<AreaRecoveryScreen.CardinalDirection> zigzagDirectionDropdown;

   public AreaRecoveryScreen(GuiTheme theme, AreaLoader searchArea) {
      super(theme, "Configuration de la reprise de zone");
      this.searchArea = searchArea;
   }

   public void initWidgets() {
      WVerticalList list = (WVerticalList)this.add(this.theme.verticalList()).expandX().widget();
      WHorizontalList modeRow = (WHorizontalList)list.add(this.theme.horizontalList()).expandX().widget();
      modeRow.add(this.theme.label("Mode : "));
      this.modeDropdown = (WDropdown<AreaLoaderModes>)modeRow.add(this.theme.dropdown((AreaLoaderModes)this.searchArea.chunkLoadMode.get())).expandX().widget();
      this.modeDropdown.action = () -> this.rebuildModeSpecificSections();
      list.add(this.theme.horizontalSeparator()).expandX();
      WSection commonSection = (WSection)list.add(this.theme.section("Configuration de la position", true)).expandX().widget();
      commonSection.add(this.theme.label("Entrez les coordonnées où le motif a commencé et où reprendre")).expandX();
      commonSection.add(this.theme.horizontalSeparator()).expandX();
      WHorizontalList originRow = (WHorizontalList)commonSection.add(this.theme.horizontalList()).expandX().widget();
      originRow.add(this.theme.label("Origine X : "));
      this.originXEdit = (WTextBox)originRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
      originRow.add(this.theme.label(" Z : "));
      this.originZEdit = (WTextBox)originRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
      WHorizontalList gapRow = (WHorizontalList)commonSection.add(this.theme.horizontalList()).expandX().widget();
      gapRow.add(this.theme.label("Écart entre passages (chunks) : "));
      this.gapEdit = (WIntEdit)gapRow.add(this.theme.intEdit((Integer)this.searchArea.rowGap.get(), 1, 100, false)).expandX().widget();
      WHorizontalList currentRow = (WHorizontalList)commonSection.add(this.theme.horizontalList()).expandX().widget();
      currentRow.add(this.theme.label("Reprise X : "));
      this.currentXEdit = (WTextBox)currentRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
      currentRow.add(this.theme.label(" Z : "));
      this.currentZEdit = (WTextBox)currentRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
      WButton useCurrentPosBtn = (WButton)commonSection.add(this.theme.button("Utiliser la position actuelle du joueur")).expandX().widget();
      useCurrentPosBtn.action = () -> {
         if (MeteorClient.mc.player != null) {
            this.currentXEdit.set(String.valueOf(MeteorClient.mc.player.getBlockX()));
            this.currentZEdit.set(String.valueOf(MeteorClient.mc.player.getBlockZ()));
            ChatUtils.info(
               "Position de reprise réglée sur celle du joueur : X=%d Z=%d",
               new Object[]{MeteorClient.mc.player.getBlockX(), MeteorClient.mc.player.getBlockZ()}
            );
         }
      };
      list.add(this.theme.horizontalSeparator()).expandX();
      this.spiralSection = (WSection)list.add(this.theme.section("Reprise en spirale - instructions", true)).expandX().widget();
      this.spiralSection.add(this.theme.label("Origine = là où la spirale a COMMENCÉ (premier coin)")).expandX();
      this.spiralSection.add(this.theme.label("Reprise = votre position actuelle ou l'endroit où continuer")).expandX();
      this.spiralSection.add(this.theme.label("Écart = chunks entre chaque bras (DOIT être celui d'origine)")).expandX();
      this.spiralSection.add(this.theme.horizontalSeparator()).expandX();
      this.spiralSection.add(this.theme.label("Reprise rapide :")).expandX();
      this.spiralSection.add(this.theme.label("  1. Remplissez Origine et Reprise, réglez le bon Écart")).expandX();
      this.spiralSection.add(this.theme.label("  2. Cliquez « Aller au coin suivant » - place Reprise sur le coin")).expandX();
      this.spiralSection.add(this.theme.label("  3. Cliquez « Appliquer depuis le coin » - enregistre et ferme")).expandX();
      this.spiralSection.add(this.theme.label("  4. Volez jusqu'aux coordonnées affichées, puis activez le module")).expandX();
      this.spiralSection.add(this.theme.horizontalSeparator()).expandX();
      this.spiralSection.add(this.theme.label("Note : si la spirale a changé depuis la course d'origine, la")).expandX();
      this.spiralSection.add(this.theme.label("position peut être légèrement décalée - c'est normal.")).expandX();
      this.rectangleSection = (WSection)list.add(this.theme.section("Reprise en rectangle - instructions", true)).expandX().widget();
      this.rectangleSection.add(this.theme.label("Origine = coin de départ du rectangle")).expandX();
      this.rectangleSection.add(this.theme.label("Fin = coin opposé du rectangle")).expandX();
      this.rectangleSection.add(this.theme.label("Reprise = votre position actuelle dans le rectangle")).expandX();
      this.rectangleSection.add(this.theme.horizontalSeparator()).expandX();
      WHorizontalList rectEndRow = (WHorizontalList)this.rectangleSection.add(this.theme.horizontalList()).expandX().widget();
      rectEndRow.add(this.theme.label("Fin X : "));
      this.rectEndXEdit = (WTextBox)rectEndRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
      rectEndRow.add(this.theme.label(" Z : "));
      this.rectEndZEdit = (WTextBox)rectEndRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
      this.zigzagSection = (WSection)list.add(this.theme.section("Reprise en zigzag - instructions", true)).expandX().widget();
      this.zigzagSection.add(this.theme.label("Origine = là où le zigzag a commencé")).expandX();
      this.zigzagSection.add(this.theme.label("Reprise = votre position actuelle")).expandX();
      this.zigzagSection.add(this.theme.horizontalSeparator()).expandX();
      WHorizontalList zigzagRow1 = (WHorizontalList)this.zigzagSection.add(this.theme.horizontalList()).expandX().widget();
      zigzagRow1.add(this.theme.label("Longueur de branche : "));
      this.zigzagLegLengthEdit = (WIntEdit)zigzagRow1.add(this.theme.intEdit((Integer)this.searchArea.zigzagLegLength.get(), 100, 100000, false))
         .expandX()
         .widget();
      zigzagRow1.add(this.theme.label(" Écart entre rangées : "));
      this.zigzagRowGapEdit = (WIntEdit)zigzagRow1.add(this.theme.intEdit((Integer)this.searchArea.zigzagRowGap.get(), 16, 1000, false)).expandX().widget();
      WHorizontalList zigzagRow2 = (WHorizontalList)this.zigzagSection.add(this.theme.horizontalList()).expandX().widget();
      zigzagRow2.add(this.theme.label("Direction principale : "));
      this.zigzagDirectionDropdown = (WDropdown<AreaRecoveryScreen.CardinalDirection>)zigzagRow2.add(
            this.theme.dropdown(AreaRecoveryScreen.CardinalDirection.SOUTH)
         )
         .expandX()
         .widget();
      list.add(this.theme.horizontalSeparator()).expandX();
      WSection actionSection = (WSection)list.add(this.theme.section("Actions", true)).expandX().widget();
      actionSection.add(this.theme.label("Valider : vérifie si la position est valable (résultat dans le chat)")).expandX();
      actionSection.add(this.theme.label("Aligner sur le trajet : ramène Reprise sur le point le plus proche")).expandX();
      actionSection.add(this.theme.label("Aller au coin suivant : (spirale) trouve le prochain coin devant")).expandX();
      actionSection.add(this.theme.label("Appliquer depuis le coin : (spirale) enregistre pour reprendre là")).expandX();
      actionSection.add(this.theme.label("Appliquer et enregistrer : enregistre la reprise, tous modes")).expandX();
      actionSection.add(this.theme.horizontalSeparator()).expandX();
      WHorizontalList buttonRow1 = (WHorizontalList)actionSection.add(this.theme.horizontalList()).expandX().widget();
      WButton validateBtn = (WButton)buttonRow1.add(this.theme.button("Valider")).expandX().widget();
      validateBtn.action = this::onValidate;
      WButton snapBtn = (WButton)buttonRow1.add(this.theme.button("Aligner sur le trajet")).expandX().widget();
      snapBtn.action = this::onSnapToNearest;
      WHorizontalList buttonRow2 = (WHorizontalList)actionSection.add(this.theme.horizontalList()).expandX().widget();
      WButton snapCornerBtn = (WButton)buttonRow2.add(this.theme.button("Aller au coin suivant")).expandX().widget();
      snapCornerBtn.action = this::onSnapToNextCorner;
      WButton applyCornerBtn = (WButton)buttonRow2.add(this.theme.button("Appliquer depuis le coin")).expandX().widget();
      applyCornerBtn.action = this::onApplyFromCorner;
      WHorizontalList buttonRow3 = (WHorizontalList)actionSection.add(this.theme.horizontalList()).expandX().widget();
      WButton applyBtn = (WButton)buttonRow3.add(this.theme.button("Appliquer et enregistrer")).expandX().widget();
      applyBtn.action = this::onApplyAndSave;
      WButton cancelBtn = (WButton)buttonRow3.add(this.theme.button("Annuler")).expandX().widget();
      cancelBtn.action = this::close;
      this.rebuildModeSpecificSections();
   }

   private boolean filterNumeric(String text, char c) {
      return Character.isDigit(c) || c == '-' && text.isEmpty();
   }

   private int parseCoord(WTextBox textBox, int defaultValue) {
      try {
         String text = textBox.get().trim();
         return !text.isEmpty() && !text.equals("-") ? Integer.parseInt(text) : defaultValue;
      } catch (NumberFormatException e) {
         return defaultValue;
      }
   }

   private void rebuildModeSpecificSections() {
      AreaLoaderModes mode = (AreaLoaderModes)this.modeDropdown.get();
      this.spiralSection.visible = mode == AreaLoaderModes.Spiral;
      this.rectangleSection.visible = mode == AreaLoaderModes.Rectangle;
      this.zigzagSection.visible = mode == AreaLoaderModes.ZigZag;
   }

   private void onValidate() {
      AreaLoaderModes mode = (AreaLoaderModes)this.modeDropdown.get();
      int originX = this.parseCoord(this.originXEdit, 0);
      int originZ = this.parseCoord(this.originZEdit, 0);
      int gap = this.gapEdit.get();
      int currentX = this.parseCoord(this.currentXEdit, 0);
      int currentZ = this.parseCoord(this.currentZEdit, 0);
      int blockGap = 16 * gap;
      switch (mode) {
         case Spiral:
            this.validateSpiral(originX, originZ, currentX, currentZ, blockGap);
            break;
         case Rectangle:
            this.validateRectangle(originX, originZ, currentX, currentZ, blockGap);
            break;
         case ZigZag:
            this.validateZigZag(originX, originZ, currentX, currentZ);
      }
   }

   private void onSnapToNearest() {
      AreaLoaderModes mode = (AreaLoaderModes)this.modeDropdown.get();
      int originX = this.parseCoord(this.originXEdit, 0);
      int originZ = this.parseCoord(this.originZEdit, 0);
      int gap = this.gapEdit.get();
      int currentX = this.parseCoord(this.currentXEdit, 0);
      int currentZ = this.parseCoord(this.currentZEdit, 0);
      int blockGap = 16 * gap;
      switch (mode) {
         case Spiral:
            this.snapSpiralToNearest(originX, originZ, currentX, currentZ, blockGap);
            break;
         case Rectangle:
            this.snapRectangleToNearest(originX, originZ, currentX, currentZ, blockGap);
            break;
         case ZigZag:
            this.snapZigZagToNearest(originX, originZ, currentX, currentZ);
      }
   }

   private void onSnapToNextCorner() {
      AreaLoaderModes mode = (AreaLoaderModes)this.modeDropdown.get();
      if (mode != AreaLoaderModes.Spiral) {
         ChatUtils.info("« Aller au coin suivant » n'existe qu'en mode spirale.", new Object[0]);
      } else {
         int originX = this.parseCoord(this.originXEdit, 0);
         int originZ = this.parseCoord(this.originZEdit, 0);
         int gap = this.gapEdit.get();
         int currentX = this.parseCoord(this.currentXEdit, 0);
         int currentZ = this.parseCoord(this.currentZEdit, 0);
         int blockGap = 16 * gap;
         Spiral spiral = new Spiral();
         int[] corner = spiral.snapToNextCorner(originX, originZ, currentX, currentZ, blockGap);
         if (corner != null) {
            this.currentXEdit.set(String.valueOf(corner[0]));
            this.currentZEdit.set(String.valueOf(corner[1]));
            ChatUtils.info("Coordonnées de reprise placées sur le coin suivant : %s", new Object[]{this.searchArea.coords(corner[0], corner[1])});
         }
      }
   }

   private void onApplyFromCorner() {
      AreaLoaderModes mode = (AreaLoaderModes)this.modeDropdown.get();
      if (mode != AreaLoaderModes.Spiral) {
         ChatUtils.info("« Appliquer depuis le coin » n'existe qu'en mode spirale.", new Object[0]);
      } else {
         int originX = this.parseCoord(this.originXEdit, 0);
         int originZ = this.parseCoord(this.originZEdit, 0);
         int gap = this.gapEdit.get();
         int currentX = this.parseCoord(this.currentXEdit, 0);
         int currentZ = this.parseCoord(this.currentZEdit, 0);
         int blockGap = 16 * gap;
         Spiral spiral = new Spiral();
         boolean success = spiral.applyFromNextCorner(originX, originZ, currentX, currentZ, blockGap);
         if (success) {
            this.searchArea.rowGap.set(gap);
            ChatUtils.info("Reprise enregistrée. Rendez-vous au coin, puis activez le module.", new Object[0]);
            this.close();
         }
      }
   }

   private void onApplyAndSave() {
      AreaLoaderModes mode = (AreaLoaderModes)this.modeDropdown.get();
      int originX = this.parseCoord(this.originXEdit, 0);
      int originZ = this.parseCoord(this.originZEdit, 0);
      int gap = this.gapEdit.get();
      int currentX = this.parseCoord(this.currentXEdit, 0);
      int currentZ = this.parseCoord(this.currentZEdit, 0);
      int blockGap = 16 * gap;

      boolean success = switch (mode) {
         case Spiral -> this.applySpiralRecovery(originX, originZ, currentX, currentZ, blockGap);
         case Rectangle -> this.applyRectangleRecovery(originX, originZ, currentX, currentZ, blockGap);
         case ZigZag -> this.applyZigZagRecovery(originX, originZ, currentX, currentZ);
         case Circle -> {
            ChatUtils.error("Le mode cercle ne gère pas la reprise (il boucle sans fin). Désactivez-le à la main.", new Object[0]);
            yield false;
         }
      };
      if (success) {
         this.searchArea.rowGap.set(gap);
         if (this.searchArea.chunkLoadMode.get() != mode) {
            this.searchArea.chunkLoadMode.set(mode);
         }
         ChatUtils.info("Reprise enregistrée. Activez le module pour continuer.", new Object[0]);
         this.close();
      }
   }

   private void validateSpiral(int originX, int originZ, int targetX, int targetZ, int blockGap) {
      AreaLoader sa = (AreaLoader)Modules.get().get(AreaLoader.class);
      if (sa != null) {
         Spiral spiral = new Spiral();
         spiral.validateManualCoordinates(originX, originZ, targetX, targetZ, blockGap, false);
      }
   }

   private void snapSpiralToNearest(int originX, int originZ, int targetX, int targetZ, int blockGap) {
      Spiral spiral = new Spiral();
      int[] snapped = spiral.snapToNearestCorner(originX, originZ, targetX, targetZ, blockGap);
      if (snapped != null) {
         this.currentXEdit.set(String.valueOf(snapped[0]));
         this.currentZEdit.set(String.valueOf(snapped[1]));
         ChatUtils.info("Coordonnées de reprise alignées.", new Object[0]);
      }
   }

   private boolean applySpiralRecovery(int originX, int originZ, int targetX, int targetZ, int blockGap) {
      Spiral spiral = new Spiral();
      return spiral.validateManualCoordinates(originX, originZ, targetX, targetZ, blockGap, true);
   }

   private void validateRectangle(int originX, int originZ, int currentX, int currentZ, int blockGap) {
      int endX = this.parseCoord(this.rectEndXEdit, 0);
      int endZ = this.parseCoord(this.rectEndZEdit, 0);
      int minX = Math.min(originX, endX);
      int maxX = Math.max(originX, endX);
      int minZ = Math.min(originZ, endZ);
      int maxZ = Math.max(originZ, endZ);
      boolean withinX = currentX >= minX - blockGap && currentX <= maxX + blockGap;
      boolean withinZ = currentZ >= minZ - blockGap && currentZ <= maxZ + blockGap;
      if (withinX && withinZ) {
         boolean goingPositiveZ = originZ < endZ;
         int zProgress = goingPositiveZ ? currentZ - originZ : originZ - currentZ;
         int rowIndex = zProgress / blockGap;
         int rowZ = originZ + (goingPositiveZ ? 1 : -1) * rowIndex * blockGap;
         boolean startEast = originX < endX;
         boolean onEvenRow = rowIndex % 2 == 0;
         boolean headingEast = startEast == onEvenRow;
         ChatUtils.info("VALABLE : la position X=%d Z=%d est dans les limites du rectangle.", new Object[]{currentX, currentZ});
         ChatUtils.info("Rangée %d (Z=%d), cap %s, progression en Z : %d blocs", new Object[]{rowIndex, rowZ, headingEast ? "est (-90)" : "ouest (90)", zProgress});
      } else {
         ChatUtils.error("La position X=%d Z=%d est HORS des limites du rectangle.", new Object[]{currentX, currentZ});
         ChatUtils.info("Rectangle : X[%d à %d], Z[%d à %d]", new Object[]{minX, maxX, minZ, maxZ});
      }
   }

   private void snapRectangleToNearest(int originX, int originZ, int currentX, int currentZ, int blockGap) {
      int endX = this.parseCoord(this.rectEndXEdit, 0);
      int endZ = this.parseCoord(this.rectEndZEdit, 0);
      boolean goingPositiveZ = originZ < endZ;
      int zProgress = goingPositiveZ ? currentZ - originZ : originZ - currentZ;
      int rowIndex = Math.round((float)zProgress / blockGap);
      int snappedZ = originZ + (goingPositiveZ ? 1 : -1) * rowIndex * blockGap;
      int minX = Math.min(originX, endX);
      int maxX = Math.max(originX, endX);
      int snappedX = Math.max(minX, Math.min(maxX, currentX));
      this.currentXEdit.set(String.valueOf(snappedX));
      this.currentZEdit.set(String.valueOf(snappedZ));
      ChatUtils.info("Aligné sur la rangée %d : X=%d Z=%d", new Object[]{rowIndex, snappedX, snappedZ});
   }

   private boolean applyRectangleRecovery(int originX, int originZ, int currentX, int currentZ, int blockGap) {
      int endX = this.parseCoord(this.rectEndXEdit, 0);
      int endZ = this.parseCoord(this.rectEndZEdit, 0);
      boolean goingPositiveZ = originZ < endZ;
      int zProgress = goingPositiveZ ? currentZ - originZ : originZ - currentZ;
      int rowIndex = Math.round((float)zProgress / blockGap);
      int lastCompleteRowZ = originZ + (goingPositiveZ ? 1 : -1) * rowIndex * blockGap;
      boolean startEast = originX < endX;
      boolean onEvenRow = rowIndex % 2 == 0;
      boolean headingEast = startEast == onEvenRow;
      int currentRowEndX = headingEast ? Math.max(originX, endX) : Math.min(originX, endX);
      boolean atRowEnd = Math.abs(currentX - currentRowEndX) < blockGap / 2;
      boolean mainPath;
      float yaw;
      if (atRowEnd) {
         mainPath = false;
         yaw = goingPositiveZ ? 0.0F : 180.0F;
      } else {
         mainPath = true;
         yaw = headingEast ? -90.0F : 90.0F;
      }

      this.searchArea.startPos.set(new BlockPos(originX, 64, originZ));
      this.searchArea.targetPos.set(new BlockPos(endX, 64, endZ));
      Rectangle.PathingDataRectangle pd = new Rectangle.PathingDataRectangle(new BlockPos(originX, 64, originZ), new BlockPos(endX, 64, endZ), new BlockPos(currentX, 64, currentZ), yaw, mainPath, lastCompleteRowZ
      );
      ChatUtils.info("Reprise rectangle : rangée=%d, yaw=%.0f, trajet principal=%b, dernière rangée Z=%d", new Object[]{rowIndex, yaw, mainPath, lastCompleteRowZ});
      return this.saveRectangleData(pd);
   }

   private boolean saveRectangleData(Rectangle.PathingDataRectangle pd) {
      try {
         File file = this.getRectangleJsonFile();
         if (file == null) {
            ChatUtils.error("Impossible de déterminer le chemin du fichier de sauvegarde.", new Object[0]);
            return false;
         }

         if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
         }

         Gson gson = new GsonBuilder().setPrettyPrinting().create();
         Writer writer = new FileWriter(file);
         gson.toJson(pd, writer);
         writer.flush();
         writer.close();
         ChatUtils.info("Reprise rectangle enregistrée dans : " + file.getName(), new Object[0]);
         return true;
      } catch (Exception e) {
         ChatUtils.error("Échec de l'enregistrement : " + e.getMessage(), new Object[0]);
         return false;
      }
   }

   private File getRectangleJsonFile() {
      String saveName = (String)this.searchArea.saveLocation.get();
      if (saveName == null || saveName.trim().isEmpty()) {
         saveName = "default";
      }

      File baseDir = new File(MeteorClient.FOLDER, "arealoader");
      File saveDir = new File(baseDir, saveName);
      String dimensionSuffix = this.getDimensionSuffix();
      if (dimensionSuffix == null) {
         return null;
      }

      return new File(saveDir, "Rectangle" + dimensionSuffix + ".json");
   }

   private void validateZigZag(int originX, int originZ, int currentX, int currentZ) {
      int legLength = this.zigzagLegLengthEdit.get();
      int rowGap = this.zigzagRowGapEdit.get();
      AreaRecoveryScreen.CardinalDirection dir = (AreaRecoveryScreen.CardinalDirection)this.zigzagDirectionDropdown.get();
      float mainYaw = dir.yaw;
      float normYaw = this.normalizeYaw(mainYaw);
      boolean mainIsNS = normYaw >= 315.0F || normYaw < 45.0F || normYaw >= 135.0F && normYaw < 225.0F;
      int mainAxisProgress;
      int sideAxisProgress;
      if (mainIsNS) {
         mainAxisProgress = Math.abs(currentZ - originZ);
         sideAxisProgress = Math.abs(currentX - originX);
      } else {
         mainAxisProgress = Math.abs(currentX - originX);
         sideAxisProgress = Math.abs(currentZ - originZ);
      }

      int legsCompleted = sideAxisProgress / rowGap;
      int positionInCurrentLeg = mainAxisProgress % legLength;
      boolean likelyOnMainLeg = positionInCurrentLeg > rowGap / 2;
      ChatUtils.info("Vérification du zigzag :", new Object[0]);
      ChatUtils.info("  Direction principale : %s (yaw=%.0f)", new Object[]{dir.name(), mainYaw});
      ChatUtils.info("  Progression : principale=%d, latérale=%d blocs depuis l'origine", new Object[]{mainAxisProgress, sideAxisProgress});
      ChatUtils.info("  ~%d branches terminées, position dans la branche : %d/%d blocs", new Object[]{legsCompleted, positionInCurrentLeg, legLength});
      ChatUtils.info("  Probablement sur la branche %s", new Object[]{likelyOnMainLeg ? "principale" : "latérale"});
   }

   private void snapZigZagToNearest(int originX, int originZ, int currentX, int currentZ) {
      int legLength = this.zigzagLegLengthEdit.get();
      int rowGap = this.zigzagRowGapEdit.get();
      AreaRecoveryScreen.CardinalDirection dir = (AreaRecoveryScreen.CardinalDirection)this.zigzagDirectionDropdown.get();
      float normYaw = this.normalizeYaw(dir.yaw);
      boolean mainIsNS = normYaw >= 315.0F || normYaw < 45.0F || normYaw >= 135.0F && normYaw < 225.0F;
      int snappedX;
      int snappedZ;
      if (mainIsNS) {
         int xDiff = currentX - originX;
         int rowsCompleted = Math.round((float)xDiff / rowGap);
         snappedX = originX + rowsCompleted * rowGap;
         snappedZ = currentZ;
      } else {
         int zDiff = currentZ - originZ;
         int rowsCompleted = Math.round((float)zDiff / rowGap);
         snappedZ = originZ + rowsCompleted * rowGap;
         snappedX = currentX;
      }

      this.currentXEdit.set(String.valueOf(snappedX));
      this.currentZEdit.set(String.valueOf(snappedZ));
      ChatUtils.info("Aligné sur : X=%d Z=%d", new Object[]{snappedX, snappedZ});
   }

   private boolean applyZigZagRecovery(int originX, int originZ, int currentX, int currentZ) {
      int legLength = this.zigzagLegLengthEdit.get();
      int rowGap = this.zigzagRowGapEdit.get();
      AreaRecoveryScreen.CardinalDirection dir = (AreaRecoveryScreen.CardinalDirection)this.zigzagDirectionDropdown.get();
      float mainYaw = dir.yaw;
      float sideYaw = this.normalizeYaw(mainYaw + 90.0F);
      float normYaw = this.normalizeYaw(mainYaw);
      boolean mainIsNS = normYaw >= 315.0F || normYaw < 45.0F || normYaw >= 135.0F && normYaw < 225.0F;
      int sideAxisProgress;
      if (mainIsNS) {
         int mainAxisProgress = Math.abs(currentZ - originZ);
         sideAxisProgress = Math.abs(currentX - originX);
         int sideAxisDiff = currentX - originX;
      } else {
         int mainAxisProgress = Math.abs(currentX - originX);
         sideAxisProgress = Math.abs(currentZ - originZ);
         int sideAxisDiff = currentZ - originZ;
      }

      int legsCompleted = sideAxisProgress / rowGap;
      boolean goingForward = legsCompleted % 2 == 0;
      float currentYaw;
      if (goingForward) {
         currentYaw = mainYaw;
      } else {
         currentYaw = this.normalizeYaw(mainYaw + 180.0F);
      }

      boolean onMainLeg = true;
      this.searchArea.zigzagLegLength.set(legLength);
      this.searchArea.zigzagRowGap.set(rowGap);
      ZigZag.PathingDataZigZag pd = new ZigZag.PathingDataZigZag(new BlockPos(originX, 64, originZ), new BlockPos(currentX, 64, currentZ), currentYaw, mainYaw, sideYaw, goingForward, onMainLeg, legsCompleted
      );
      pd.legStartPos = new BlockPos(currentX, 64, currentZ);
      ChatUtils.info(
         "Reprise zigzag : branches=%d, aller=%b, yaw=%.0f, yaw principal=%.0f, yaw latéral=%.0f",
         new Object[]{legsCompleted, goingForward, currentYaw, mainYaw, sideYaw}
      );
      return this.saveZigZagData(pd);
   }

   private boolean saveZigZagData(ZigZag.PathingDataZigZag pd) {
      try {
         File file = this.getZigZagJsonFile();
         if (file == null) {
            ChatUtils.error("Impossible de déterminer le chemin du fichier de sauvegarde.", new Object[0]);
            return false;
         }

         if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
         }

         Gson gson = new GsonBuilder().setPrettyPrinting().create();
         Writer writer = new FileWriter(file);
         gson.toJson(pd, writer);
         writer.flush();
         writer.close();
         ChatUtils.info("Reprise zigzag enregistrée dans : " + file.getName(), new Object[0]);
         return true;
      } catch (Exception e) {
         ChatUtils.error("Échec de l'enregistrement : " + e.getMessage(), new Object[0]);
         return false;
      }
   }

   private File getZigZagJsonFile() {
      String saveName = (String)this.searchArea.saveLocation.get();
      if (saveName == null || saveName.trim().isEmpty()) {
         saveName = "default";
      }

      File baseDir = new File(MeteorClient.FOLDER, "arealoader");
      File saveDir = new File(baseDir, saveName);
      String dimensionSuffix = this.getDimensionSuffix();
      if (dimensionSuffix == null) {
         return null;
      }

      return new File(saveDir, "ZigZag" + dimensionSuffix + ".json");
   }

   private String getDimensionSuffix() {
      if (MeteorClient.mc.world == null) {
         return null;
      }

      try {
         if (MeteorClient.mc.world.getRegistryKey().equals(World.NETHER)) {
            return "_nether";
         } else {
            return MeteorClient.mc.world.getRegistryKey().equals(World.END) ? "_end" : "_overworld";
         }
      } catch (Exception e) {
         return null;
      }
   }

   private float normalizeYaw(float yaw) {
      yaw %= 360.0F;
      if (yaw < 0.0F) {
         yaw += 360.0F;
      }

      return yaw;
   }

   public enum CardinalDirection {
      NORTH(180.0F),
      SOUTH(0.0F),
      EAST(270.0F),
      WEST(90.0F);

      public final float yaw;

      CardinalDirection(float yaw) {
         this.yaw = yaw;
      }
   }
}
