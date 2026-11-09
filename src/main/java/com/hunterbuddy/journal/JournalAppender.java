package com.hunterbuddy.journal;

import org.apache.logging.log4j.Level;
import java.io.StringWriter;
import java.io.PrintWriter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

final class JournalAppender extends AbstractAppender {
    JournalAppender() {
        super("HunterBuddyClientJournal", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        JournalContext context = JournalContext.current();
        if (context.appending || context.serverDepth > 0 || !JournalSource.modLogger(event.getLoggerName(), event.getThreadName())) return;
        context.appending = true;
        try {
            JournalEntry.Level level = event.getLevel().isMoreSpecificThan(Level.ERROR) ? JournalEntry.Level.ERROR
                : event.getLevel().isMoreSpecificThan(Level.WARN) ? JournalEntry.Level.WARN : JournalEntry.Level.INFO;
            String message = event.getMessage().getFormattedMessage();
            if (JournalCapture.chatEcho(message)) return;
            if (event.getThrown() != null) {
                StringWriter trace = new StringWriter();
                event.getThrown().printStackTrace(new PrintWriter(trace));
                message += " " + trace;
            }
            JournalCapture.record(event.getTimeMillis(), JournalSource.identify(event.getLoggerName()), level, message);
        } catch (RuntimeException ignored) {
        } finally {
            context.appending = false;
        }
    }
}
