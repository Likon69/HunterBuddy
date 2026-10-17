package com.hunterbuddy.modules.messenger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

public final class PrivateMessageParser {
    private static final String INCOMING_KEY = "commands.message.display.incoming";
    private static final String OUTGOING_KEY = "commands.message.display.outgoing";

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern PREFIX = Pattern.compile("^(?:\\s*(?:\\[[^\\]]*\\]|\\([^)]*\\)))+");

    private final Map<String, Pattern> compiled = new HashMap<>();

    public String describe(Text message) {
        String key = message.getContent() instanceof TranslatableTextContent translatable ? translatable.getKey() : "-";
        return "key=" + key + " text=\"" + message.getString() + "\"";
    }

    public Optional<PrivateMessage> parse(Text message, MessengerSettings cfg) {
        long now = System.currentTimeMillis();

        if (cfg.useVanillaKeys.get() && message.getContent() instanceof TranslatableTextContent translatable) {
            String key = translatable.getKey();
            Object[] args = translatable.getArgs();
            boolean incoming = key.equals(INCOMING_KEY);

            if ((incoming || key.equals(OUTGOING_KEY)) && args.length >= 2) {
                String peer = validName(plain(args[0]));
                if (peer != null) return Optional.of(new PrivateMessage(peer, incoming, plain(args[1]), now));
            }
        }

        String raw = message.getString();

        Optional<PrivateMessage> hit = match(cfg.incomingPattern.get(), raw, true, now);
        if (hit.isPresent()) return hit;

        return match(cfg.outgoingPattern.get(), raw, false, now);
    }

    private Optional<PrivateMessage> match(String regex, String raw, boolean incoming, long now) {
        Pattern pattern = pattern(regex);
        if (pattern == null) return Optional.empty();

        Matcher matcher = pattern.matcher(raw);
        if (!matcher.matches() || matcher.groupCount() < 2) return Optional.empty();

        String peer = matcher.group(1);
        String text = matcher.group(2);
        if (peer == null || text == null || !NAME.matcher(peer).matches()) return Optional.empty();

        return Optional.of(new PrivateMessage(peer, incoming, text, now));
    }

    private Pattern pattern(String regex) {
        if (compiled.containsKey(regex)) return compiled.get(regex);

        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            pattern = null;
        }

        compiled.put(regex, pattern);
        return pattern;
    }

    private static String validName(String raw) {
        String trimmed = raw.trim();
        if (NAME.matcher(trimmed).matches()) return trimmed;

        Matcher prefix = PREFIX.matcher(trimmed);
        if (!prefix.lookingAt()) return null;

        String rest = trimmed.substring(prefix.end()).trim();
        int space = rest.indexOf(' ');
        String word = space < 0 ? rest : rest.substring(0, space);
        return NAME.matcher(word).matches() ? word : null;
    }

    private static String plain(Object argument) {
        return argument instanceof Text text ? text.getString() : String.valueOf(argument);
    }
}
