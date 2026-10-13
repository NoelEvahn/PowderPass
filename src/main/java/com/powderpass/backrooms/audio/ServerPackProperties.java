package com.powderpass.backrooms.audio;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads and edits the resource-pack keys of {@code server.properties}. Free of Bukkit so it can be tested on its own.
 * Every other line of the file is kept byte for byte (the file is read and written as ISO-8859-1, which is what
 * {@link Properties} uses, so nothing is re-encoded); only the five keys are replaced or appended.
 */
final class ServerPackProperties {
    static final String URL = "resource-pack", SHA1 = "resource-pack-sha1", ID = "resource-pack-id",
            PROMPT = "resource-pack-prompt", REQUIRED = "require-resource-pack";
    static final List<String> KEYS = List.of(URL, SHA1, ID, PROMPT, REQUIRED);

    /**
     * What a sync found and did.
     *
     * @param live    the file already had exactly the wanted values, so the server (which reads it at startup) is
     *                using them
     * @param changed the file was rewritten, so Minecraft will only use the values after a restart
     * @param current the values found in the file before any edit (empty string for a missing key)
     * @param backup  the copy of the original file, or null
     */
    record Result(boolean live, boolean changed, Map<String, String> current, File backup) {
        boolean packSet() {
            return !current.get(URL).isBlank();
        }
    }

    private ServerPackProperties() {
    }

    /** The wanted values in file order. */
    static Map<String, String> wanted(String url, String sha1, String id, String prompt, boolean required) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(URL, url);
        m.put(SHA1, sha1);
        m.put(ID, id);
        m.put(PROMPT, prompt);
        m.put(REQUIRED, Boolean.toString(required));
        return m;
    }

    /** Current values of the five keys (missing = ""). */
    static Map<String, String> read(File file) throws IOException {
        return parse(Files.readString(file.toPath(), StandardCharsets.ISO_8859_1));
    }

    static Map<String, String> parse(String content) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(content));
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : KEYS) m.put(k, p.getProperty(k, "").trim());
        return m;
    }

    static boolean matches(Map<String, String> have, Map<String, String> want) {
        for (String k : KEYS) {
            String h = have.get(k), w = want.get(k).trim();
            boolean same = k.equals(REQUIRED) ? Boolean.parseBoolean(h) == Boolean.parseBoolean(w)
                    : k.equals(SHA1) ? h.equalsIgnoreCase(w) : h.equals(w);
            if (!same) return false;
        }
        return true;
    }

    /**
     * Makes the file carry {@code want}. Does nothing when it already does. Otherwise writes a copy of the original
     * beside it once ({@code server.properties.backrooms.bak}) and replaces the file atomically.
     */
    static Result sync(File file, Map<String, String> want) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.ISO_8859_1);
        Map<String, String> have = parse(content);
        if (matches(have, want)) return new Result(true, false, have, null);
        File backup = new File(file.getParentFile(), file.getName() + ".backrooms.bak");
        if (!backup.exists()) Files.copy(file.toPath(), backup.toPath());
        Files.writeString(tmp(file).toPath(), rewrite(content, want), StandardCharsets.ISO_8859_1);
        Files.move(tmp(file).toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return new Result(false, true, have, backup);
    }

    private static File tmp(File f) {
        return new File(f.getParentFile(), f.getName() + ".tmp");
    }

    /** The content with the five keys set: existing lines replaced in place, missing ones appended. */
    static String rewrite(String content, Map<String, String> want) {
        String nl = content.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(List.of(content.split("\r?\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1); // trailing newline
        Map<String, Boolean> done = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String key = keyOf(lines.get(i));
            if (key == null || !want.containsKey(key)) continue;
            if (done.putIfAbsent(key, true) != null) {
                lines.remove(i--); // a duplicate of a key we already wrote: the last one would win, so drop it
                continue;
            }
            lines.set(i, key + "=" + escape(want.get(key)));
        }
        for (Map.Entry<String, String> e : want.entrySet()) {
            if (!done.containsKey(e.getKey())) lines.add(e.getKey() + "=" + escape(e.getValue()));
        }
        return String.join(nl, lines) + nl;
    }

    /** The key a properties line sets, or null for blanks and comments. */
    static String keyOf(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        if (i == line.length() || line.charAt(i) == '#' || line.charAt(i) == '!') return null;
        int j = i;
        while (j < line.length() && line.charAt(j) != '=' && line.charAt(j) != ':' && !Character.isWhitespace(line.charAt(j))) j++;
        return line.substring(i, j);
    }

    /** What {@link Properties#store} writes for a value, so Minecraft's own rewrites of the file stay identical. */
    static String escape(String v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case ':' -> b.append("\\:");
                case '=' -> b.append("\\=");
                case '#' -> b.append("\\#");
                case '!' -> b.append("\\!");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case ' ' -> b.append(i == 0 ? "\\ " : " ");
                default -> {
                    if (c < 0x20 || c > 0x7e) b.append(String.format("\\u%04X", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }
}
