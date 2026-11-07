package com.hunterbuddy.pet;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.util.Identifier;

public final class PetLineChoice {
    private final Map<Identifier, Integer> uses = new HashMap<>();
    private final Random random = new Random();

    public PetResources.Line pick(PetResources.Line[] group) {
        PetResources.Line chosen = null;
        int least = Integer.MAX_VALUE;
        int ties = 0;
        for (PetResources.Line line : group) {
            int count = uses.getOrDefault(line.sound, 0);
            if (count < least) {
                least = count;
                ties = 0;
            }
            if (count == least && random.nextInt(++ties) == 0) chosen = line;
        }
        if (chosen != null) uses.merge(chosen.sound, 1, Integer::sum);
        return chosen;
    }

    public void reset() {
        uses.clear();
    }
}
