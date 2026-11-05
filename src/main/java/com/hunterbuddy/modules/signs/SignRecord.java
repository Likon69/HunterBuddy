package com.hunterbuddy.modules.signs;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;

public record SignRecord(SignKey key, List<String> front, List<String> back, SignStyle frontStyle, SignStyle backStyle) {
    public static final int LINES = 4;
    private static final String FACE_SEPARATOR = "-";

    public static SignRecord of(String dimension, SignBlockEntity sign) {
        return new SignRecord(
            SignKey.of(dimension, sign.getPos()),
            lines(sign.getFrontText()),
            lines(sign.getBackText()),
            SignStyle.of(sign.getFrontText()),
            SignStyle.of(sign.getBackText())
        );
    }

    public boolean isValid() {
        return key != null && front != null && back != null;
    }

    public boolean isBlank() {
        return front.stream().allMatch(String::isBlank) && back.stream().allMatch(String::isBlank);
    }

    public boolean sameText(SignRecord other) {
        return front.equals(other.front) && back.equals(other.back);
    }

    public SignRecord withFace(boolean frontFace, List<String> lines) {
        return frontFace
            ? new SignRecord(key, lines, back, frontStyle, backStyle)
            : new SignRecord(key, front, lines, frontStyle, backStyle);
    }

    public SignStyle style(boolean frontFace) {
        return frontFace ? frontStyle : backStyle;
    }

    public String line(boolean frontFace, int index) {
        List<String> face = frontFace ? front : back;
        return index < face.size() ? face.get(index) : "";
    }

    public List<String> visibleLines() {
        List<String> visible = new ArrayList<>(nonBlank(front));
        List<String> backLines = nonBlank(back);
        if (!visible.isEmpty() && !backLines.isEmpty()) visible.add(FACE_SEPARATOR);
        visible.addAll(backLines);
        return visible;
    }

    private static List<String> nonBlank(List<String> face) {
        List<String> lines = new ArrayList<>();
        for (String line : face) if (!line.isBlank()) lines.add(line);
        return lines;
    }

    private static List<String> lines(SignText face) {
        List<String> lines = new ArrayList<>(LINES);
        for (Text message : face.getMessages(false)) lines.add(message.getString());
        return lines;
    }
}
