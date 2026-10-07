package com.hunterbuddy.modules.sorter.model;

import com.hunterbuddy.modules.sorter.logic.CategoryRules;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


public class SorterProfile {
    public int version = 1;

    public List<Pos> inputs = new ArrayList<>();

    public List<Destination> destinations = new ArrayList<>();

    public Pos unpackStation;
    public CategoryRules rules = CategoryRules.defaults();

    public List<Destination> destinationsFor(String category) {
        List<Destination> out = new ArrayList<>();
        for (Destination d : destinations) {
            if (d.category.equals(category)) {
                out.add(d);
            }
        }
        return out;
    }

    public Destination getOrCreate(String category) {
        for (Destination d : destinations) {
            if (d.category.equals(category)) {
                return d;
            }
        }
        Destination d = new Destination(category);
        destinations.add(d);
        return d;
    }


    public String whatIs(Pos pos) {
        if (inputs.contains(pos)) {
            return "input";
        }
        for (Destination d : destinations) {
            if (d.chests.contains(pos)) {
                return "destination " + d.category;
            }
        }
        if (pos.equals(unpackStation)) {
            return "unpack station";
        }
        return null;
    }

    public static class Destination {
        public String category;
        public List<Pos> chests = new ArrayList<>();

        public Set<Pos> full = new LinkedHashSet<>();

        public Destination() {
        }

        public Destination(String category) {
            this.category = category;
        }
    }


    public static class Pos {
        public int x;
        public int y;
        public int z;

        public Pos() {
        }

        public Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Pos p && p.x == x && p.y == y && p.z == z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }

        @Override
        public String toString() {
            return x + " " + y + " " + z;
        }
    }
}
