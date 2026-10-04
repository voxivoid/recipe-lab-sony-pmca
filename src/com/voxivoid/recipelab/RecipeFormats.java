package com.voxivoid.recipelab;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every version of the custom recipe file format the app knows, and which one reads or writes a file.
 *
 * <h3>Reading</h3>
 * A file is {@code key = value} lines, {@code #} starting a comment — the envelope every version shares ({@link #keys}).
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

    /**
     * The envelope: {@code text} into key → value, keys lower-cased, values trimmed; {@code #} to the line's end is a
     * comment, a byte-order mark is dropped, a line without {@code =} is skipped (a later version may know it). Returns
     * why the file cannot be read — a key given twice is ambiguous — or null.
     */
    static String keys(String text, Map<String, String> into) {
        if (text.startsWith("﻿")) text = text.substring(1);   // the byte-order mark some Windows editors add
        for (String raw : text.split("\r\n|\r|\n")) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim().toLowerCase(Locale.US), value = line.substring(eq + 1).trim();
            if (into.put(key, value) != null) return Lang.t("custom_twice", key);
        }
        return null;
    }
}
