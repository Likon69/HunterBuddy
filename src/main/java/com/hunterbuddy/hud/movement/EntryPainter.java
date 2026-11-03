package com.hunterbuddy.hud.movement;

import com.hunterbuddy.hud.MovementStatusHud.Layout;

interface EntryPainter {
    double width(StatusEntry entry);

    double height(StatusEntry entry);

    double gap(Layout layout);

    void draw(StatusEntry entry, double x, double y);

    Size placeholder(Layout layout);
}
