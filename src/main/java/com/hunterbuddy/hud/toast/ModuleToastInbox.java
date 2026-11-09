package com.hunterbuddy.hud.toast;

public final class ModuleToastInbox {
    public final ModuleToastTimeline timeline = new ModuleToastTimeline();
    private final ModuleToastHistory history;
    private long cursor;
    private long session;

    public ModuleToastInbox(ModuleToastHistory history) {
        this.history = history;
        cursor = history.latest();
        session = history.session();
    }

    public void read(boolean boundOnly) {
        if (session != history.session()) {
            timeline.clear();
            session = history.session();
            cursor = history.start();
        }
        long latest = history.latest();
        cursor = Math.max(cursor, latest - 64);
        while (cursor < latest) {
            ModuleToastTimeline.Notice notice = history.get(++cursor);
            if (notice != null && (!boundOnly || !notice.bind().equals("Unbound"))) timeline.offer(notice);
        }
    }
}
