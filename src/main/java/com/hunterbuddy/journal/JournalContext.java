package com.hunterbuddy.journal;

public final class JournalContext {
    private static final ThreadLocal<JournalContext> LOCAL = ThreadLocal.withInitial(JournalContext::new);
    public int serverDepth;
    public boolean appending;
    public JournalSource origin;

    private JournalContext() {
    }

    public static JournalContext current() {
        return LOCAL.get();
    }

    public Runnable carry(JournalSource source, Runnable action) {
        boolean remote = serverDepth > 0;
        return () -> {
            JournalContext context = current();
            JournalSource previous = context.origin;
            context.origin = source;
            if (remote) context.serverDepth++;
            try {
                action.run();
            } finally {
                if (remote) context.serverDepth--;
                context.origin = previous;
            }
        };
    }
}
