package com.hunterbuddy.modules.messenger.ui;

import com.hunterbuddy.modules.messenger.Conversation;
import com.hunterbuddy.modules.messenger.PrivateMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class MessageLayout {
    enum Kind {
        TEXT,
        TIME,
        GAP
    }

    record Row(Kind kind, String text, boolean incoming, double bubbleWidth, boolean first, boolean last,
               int message, int offset) {
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final String HIDDEN = "[hidden]";

    private final List<Row> rows = new ArrayList<>();
    private Conversation conversation;
    private int revision = -1;
    private double width;
    private double scale;
    private double padding;
    private double fraction;
    private boolean timestamps;
    private boolean stream;
    private boolean shadow;

    List<Row> rows() {
        return rows;
    }

    void update(Painter painter, Conversation source, double areaWidth, double padX, double textScale,
                   boolean showTimes, boolean streamMode, boolean withShadow, double messageFraction) {
        int sourceRevision = source == null ? -1 : source.revision();
        if (source == conversation && sourceRevision == revision && areaWidth == width && textScale == scale
            && showTimes == timestamps && streamMode == stream && withShadow == shadow
            && padX == padding && messageFraction == fraction) {
            return;
        }

        conversation = source;
        revision = sourceRevision;
        width = areaWidth;
        scale = textScale;
        padding = padX;
        fraction = messageFraction;
        timestamps = showTimes;
        stream = streamMode;
        shadow = withShadow;

        rows.clear();
        if (source == null) return;

        double maxText = Math.max(1.0, areaWidth * messageFraction - padX * 2.0);
        boolean firstMessage = true;
        int index = 0;

        for (PrivateMessage message : source.messages()) {
            if (!firstMessage) rows.add(new Row(Kind.GAP, "", false, 0.0, false, false, -1, -1));
            firstMessage = false;

            String body = stream ? HIDDEN : message.text();
            List<int[]> lines = wrap(painter, body, maxText);
            String time = showTimes ? CLOCK.format(Instant.ofEpochMilli(message.epochMillis())) : null;

            double widest = time == null ? 0.0 : painter.width(time);
            for (int[] line : lines) widest = Math.max(widest, painter.width(body.substring(line[0], line[1])));
            double bubble = widest + padX * 2.0;

            for (int i = 0; i < lines.size(); i++) {
                int[] line = lines.get(i);
                boolean last = time == null && i == lines.size() - 1;
                rows.add(new Row(Kind.TEXT, body.substring(line[0], line[1]), message.incoming(), bubble,
                    i == 0, last, index, line[0]));
            }
            if (time != null) rows.add(new Row(Kind.TIME, time, message.incoming(), bubble, false, true, index, body.length()));
            index++;
        }
    }

    private static List<int[]> wrap(Painter painter, String text, double maxWidth) {
        List<int[]> lines = new ArrayList<>();
        int lineStart = -1;
        int lineEnd = 0;
        int position = 0;

        for (String word : text.split(" ", -1)) {
            int wordStart = position;
            int wordEnd = position + word.length();
            position = wordEnd + 1;

            int candidateStart = lineStart < 0 ? wordStart : lineStart;
            if (painter.width(text.substring(candidateStart, wordEnd)) <= maxWidth) {
                lineStart = candidateStart;
                lineEnd = wordEnd;
                continue;
            }

            if (lineStart >= 0) {
                lines.add(new int[] {lineStart, lineEnd});
                lineStart = -1;
            }

            int restStart = wordStart;
            while (painter.width(text.substring(restStart, wordEnd)) > maxWidth && wordEnd - restStart > 1) {
                int cut = wordEnd - 1;
                while (cut - restStart > 1 && painter.width(text.substring(restStart, cut)) > maxWidth) cut--;
                if (Character.isLowSurrogate(text.charAt(cut))) cut = cut - 1 > restStart ? cut - 1 : cut + 1;
                lines.add(new int[] {restStart, cut});
                restStart = cut;
            }
            lineStart = restStart;
            lineEnd = wordEnd;
        }

        lines.add(new int[] {lineStart, lineEnd});
        return lines;
    }
}
