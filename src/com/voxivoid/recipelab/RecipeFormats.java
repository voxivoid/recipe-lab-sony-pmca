package com.voxivoid.recipelab;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every version of the custom recipe file format the app knows, and which one reads or writes a file.
 *
 * <h3>Reading</h3>
 * A file is YAML: a flat mapping, one {@code key: value} per line, {@code #} comments, a value optionally in quotes — the
 * envelope every version shares ({@link #keys}). Lists, nesting and the rest of what YAML allows are not a recipe.
 * Its {@code format} line picks the reader: the {@link RecipeFormat} of that version, so a file keeps loading with the
 * reader it was written for after newer versions arrive. A file without the line is format 1. A file from a version this
 * build does not know is skipped as a whole, never half-read: it ends up in the camera's settings store.
 *
 * <h3>Writing</h3>
 * The app writes with the newest version ({@link #current}). The older writers stay, with their readers.
 *
 * <h3>Adding a version</h3>
 * A key a reader does not know is ignored, so adding a key needs no new version. Changing what an existing key means
 * does: add a RecipeFormatV2 (reader and writer), append it to {@link #ALL}, and leave V1 alone. Versions are numbered
 * 1, 2, 3 … in order (RecipeFormatsTest).
 *
 * No android.* import may appear here (tools/test.sh).
 */
final class RecipeFormats {
    private RecipeFormats() {}

    /** every version the app reads, oldest first; the last is the one it writes */
    static final RecipeFormat[] ALL = { new RecipeFormatV1() };

    /** the version the app writes: the newest */
    static RecipeFormat current() { return ALL[ALL.length - 1]; }

    /** a recipe as a file of the newest version */
    static String write(Recipes.Recipe r, String madeOn) { return current().write(r, madeOn); }

    /** a file's text read by the version it names; {@code fileName} names a recipe whose file has no name line */
    static CustomRecipes.Parsed parse(String text, String fileName) { return parse(text, fileName, ALL); }

    /** the same against a given set of versions — how the tests stand in a version this build does not have yet */
    static CustomRecipes.Parsed parse(String text, String fileName, RecipeFormat[] formats) {
        Map<String, String> kv = new HashMap<String, String>();
        String why = keys(text, kv);
        if (why != null) return CustomRecipes.Parsed.fail(why);
        String f = kv.get("format");
        int version;
        if (f == null) version = 1;                                 // hand-written files, and the first version
        else if (f.matches("\\d{1,4}")) version = Integer.parseInt(f);
        else return CustomRecipes.Parsed.fail(CustomRecipes.bad("format", f));
        RecipeFormat reader = find(version, formats);
        if (reader != null) return reader.read(kv, fileName);
        int newest = formats[formats.length - 1].version();
        return CustomRecipes.Parsed.fail(version > newest ? Lang.t("custom_newer", version) : CustomRecipes.bad("format", f));
    }

    /** the version numbered {@code version}, or null when there is none */
    static RecipeFormat find(int version, RecipeFormat[] formats) {
        for (RecipeFormat x : formats) if (x.version() == version) return x;
        return null;
    }

    /** a top-level YAML mapping entry: a plain key, a colon, then the value (or nothing) after a space */
    private static final Pattern ENTRY = Pattern.compile("([A-Za-z][A-Za-z0-9_-]*)\\s*:(?:\\s+(.*))?");

    /**
     * The envelope: a YAML flat mapping into key → value, keys lower-cased. Blank lines, {@code #} comments and the
     * {@code ---} / {@code ...} document markers are skipped, as is a byte-order mark. A value is plain — a {@code #}
     * after a space starts a comment — or in single or double quotes. Returns why the file cannot be read, or null: a
     * line that is not a top-level {@code key: value} (a list, nesting, an old {@code key = value} file) names its line,
     * and a key given twice is ambiguous.
     */
    static String keys(String text, Map<String, String> into) {
        if (text.startsWith("﻿")) text = text.substring(1);   // the byte-order mark some Windows editors add
        String[] lines = text.split("\r\n|\r|\n");
        for (int n = 0; n < lines.length; n++) {
            String raw = lines[n], t = raw.trim();
            if (t.isEmpty() || t.startsWith("#") || t.equals("---") || t.equals("...")) continue;
            Matcher m = ENTRY.matcher(raw);
            String value = m.matches() ? scalar(m.group(2) == null ? "" : m.group(2)) : null;
            if (value == null) return Lang.t("custom_not_yaml", n + 1);
            String key = m.group(1).toLowerCase(Locale.US);
            if (into.put(key, value) != null) return Lang.t("custom_twice", key);
        }
        return null;
    }

    /**
     * A YAML scalar on one line: in single quotes ('it''s'), double quotes ("say \"hi\""), or plain up to a " #" comment.
     * Null when it is malformed — a quote that does not close, or something after it other than a comment.
     */
    static String scalar(String v) {
        v = v.trim();
        if (v.startsWith("'") || v.startsWith("\"")) {
            char q = v.charAt(0);
            int end = v.lastIndexOf(q);
            if (end <= 0) return null;
            String rest = v.substring(end + 1).trim();
            if (!rest.isEmpty() && !rest.startsWith("#")) return null;
            String in = v.substring(1, end);
            return q == '\'' ? in.replace("''", "'") : in.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        int hash = v.indexOf(" #");
        return (hash >= 0 ? v.substring(0, hash) : v).trim();
    }
}
