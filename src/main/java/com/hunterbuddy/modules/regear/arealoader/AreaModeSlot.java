package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.regear.arealoader.modes.Circle;
import com.hunterbuddy.modules.regear.arealoader.modes.Rectangle;
import com.hunterbuddy.modules.regear.arealoader.modes.Spiral;
import com.hunterbuddy.modules.regear.arealoader.modes.ZigZag;

final class AreaModeSlot {
    private AreaLoaderMode current;

    AreaLoaderMode current() {
        if (current == null) current = new Rectangle();
        return current;
    }

    void replaceWith(AreaLoaderModes type) {
        current = create(type);
    }

    private static AreaLoaderMode create(AreaLoaderModes type) {
        return switch (type) {
            case Rectangle -> new Rectangle();
            case Spiral -> new Spiral();
            case ZigZag -> new ZigZag();
            case Circle -> new Circle();
        };
    }
}
