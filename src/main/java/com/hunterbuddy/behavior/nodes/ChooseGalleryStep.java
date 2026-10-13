package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.GallerySelection;
import com.hunterbuddy.behavior.mc.MineSiteContext;

public final class ChooseGalleryStep extends Node {
    private final MineSiteContext mine;

    public ChooseGalleryStep(MineSiteContext mine) {
        super("choose-connected-two-block-high-gallery");
        this.mine = mine;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!GallerySelection.choose(mine)) {
            mine.facts.siteState = "inaccessible";
            mine.facts.outcome = "aucune nouvelle galerie sûre dans la tache; appuis de retour conservés";
        }
        return Success.INSTANCE;
    }
}
