package com.hunterbuddy.modules.sorter.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class SortCategory {
    public String name;

    public int priority;
    public String shulkerColor;
    public Set<String> items = new LinkedHashSet<>();
    public List<String> patterns = new ArrayList<>();

    public SortCategory() {
    }

    public SortCategory(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    public SortCategory items(String... ids) {
        for (String id : ids) {
            items.add(normalize(id));
        }
        return this;
    }

    public SortCategory patterns(String... globs) {
        for (String glob : globs) {
            patterns.add(glob.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    public boolean matchesExact(String itemId) {
        return items.contains(normalize(itemId));
    }

    public boolean matchesPattern(String itemId) {
        String id = normalize(itemId);
        String path = id.substring(id.indexOf(':') + 1);
        for (String glob : patterns) {
            String target = glob.contains(":") ? id : path;
            if (glob(glob, target)) {
                return true;
            }
        }
        return false;
    }


    public static String normalize(String id) {
        String lower = id.trim().toLowerCase(Locale.ROOT);
        return lower.contains(":") ? lower : "minecraft:" + lower;
    }


    static boolean glob(String pattern, String text) {
        int p = 0, t = 0, star = -1, mark = 0;
        while (t < text.length()) {
            if (p < pattern.length() && pattern.charAt(p) == text.charAt(t)) {
                p++;
                t++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                mark = t;
            } else if (star != -1) {
                p = star + 1;
                t = ++mark;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }
}
