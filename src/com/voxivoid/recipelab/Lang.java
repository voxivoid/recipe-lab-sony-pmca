package com.voxivoid.recipelab;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The display language: which one the app menu picked, which one that resolves to on this camera, and the text of
 * every message, label and legend in it.
 *
 * Text lives in one table per language ({@link TextEn}, {@link TextZhHans}, {@link TextZhHant}), a key → text pair
 * each. English is the source: a key another table lacks falls back to it. Recipe, brand, style, effect and quality
 * names are not in the English table at all — their canonical English lives in {@link Recipes} and {@link Params},
 * where the manifest, the favourites and the tests read it — so {@link #label} takes that canonical text as its
 * fallback, and only a translation adds the key.
 *
 * What the app writes to files (samples.txt, locks.txt, keys.txt, diff.txt, custom recipes) stays English whatever the menu says:
 * compatibility reports quote those files. The few places that build file text from a label switch to English for
 * the length of it with {@link #use}.
 *
 * No android.* import may appear in this class (tools/test.sh).
 */
final class Lang {
    private Lang() {}

    /** the languages the tables hold, in menu order */
    static final int EN = 0, ZH_HANS = 1, ZH_HANT = 2, COUNT = 3;
    /** the menu choice that follows the camera's own language */
    static final int AUTO = -1;
    /** how a choice is kept in the app's preferences: by code, so a new language never shifts a stored one */
    static final String[] CODES = { "en", "zh-Hans", "zh-Hant" };
    static final String AUTO_CODE = "auto";

    private static final String[][][] TABLES = { TextEn.TEXT, TextZhHans.TEXT, TextZhHant.TEXT };
    private static final Map<String, String>[] TEXT = maps();
    private static int current = EN;

    @SuppressWarnings("unchecked")
    private static Map<String, String>[] maps() {
        Map<String, String>[] m = new Map[TABLES.length];
        for (int l = 0; l < TABLES.length; l++) {
            m[l] = new HashMap<String, String>();
            for (String[] kv : TABLES[l]) m[l].put(kv[0], kv[1]);
        }
        return m;
    }

    // ------------------------------------------------------------ the current language
    static int current() { return current; }

    /**
     * Switches the display language and returns the one it replaces. The menu calls it when the choice changes; file
     * output calls it around the text it writes, and restores the previous language after.
     */
    static int use(int lang) {
        int was = current;
        current = lang >= 0 && lang < COUNT ? lang : EN;
        return was;
    }

    // ------------------------------------------------------------ text
    /**
     * The text of {@code key} in the current language, with {@code args} put into its {@code %1$s}-style
     * placeholders. A key the current table lacks comes from English; a key English lacks comes back as itself, so a
     * missing one shows on screen instead of crashing the camera app (LangTest checks that none is missing).
     */
    static String t(String key, Object... args) {
        String s = TEXT[current].get(key);
        if (s == null) s = TEXT[EN].get(key);
        if (s == null) return key;
        return args.length == 0 ? s : String.format(Locale.US, s, args);   // Locale.US: digits stay ASCII whatever the camera's locale
    }

    /** a name whose canonical text is English data elsewhere: its translation in the current language, else {@code canonical} */
    static String label(String key, String canonical) {
        String s = current == EN ? null : TEXT[current].get(key);
        return s != null ? s : canonical;
    }

    /** whether a language's own table holds a key — for the tests, which check every table is complete */
    static boolean has(int lang, String key) { return TEXT[lang].containsKey(key); }

    /** the keys and texts of a language's table, in the order they are written */
    static String[][] table(int lang) { return TABLES[lang]; }

    /**
     * A key made from canonical text: lower case, every run of anything but a letter or a digit a single underscore.
     * "Kodak Tri-X 400" → "kodak_tri_x_400", "posterization-bw" → "posterization_bw".
     */
    static String slug(String s) {
        StringBuilder b = new StringBuilder();
        boolean gap = false;
        for (char ch : s.toLowerCase(Locale.US).toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) { if (gap && b.length() > 0) b.append('_'); b.append(ch); gap = false; }
            else gap = true;
        }
        return b.toString();
    }

    // ------------------------------------------------------------ the menu choice
    /** the choices the Language row steps through: follow the camera, then each language */
    static final int[] CHOICES = { AUTO, EN, ZH_HANS, ZH_HANT };

    /** the choice left / right lands on, wrapping */
    static int nextChoice(int choice, int dir) {
        int pos = 0;
        for (int i = 0; i < CHOICES.length; i++) if (CHOICES[i] == choice) pos = i;
        return CHOICES[(pos + CHOICES.length + dir) % CHOICES.length];
    }

    /** the Language row's value: "Auto" in the current language, a language by its own name in its own script */
    static String choiceLabel(int choice) {
        return choice == AUTO ? t("lang_auto") : TEXT[choice].get("lang_name");
    }

    /** the language a choice shows its label in: the current one for Auto, the language itself for the others */
    static int choiceScript(int choice) { return choice == AUTO ? current : choice; }

    /** a stored preference back to a choice; anything unknown — nothing stored yet, a dropped language — is Auto */
    static int parseChoice(String code) {
        for (int l = 0; l < CODES.length; l++) if (CODES[l].equals(code)) return l;
        return AUTO;
    }

    static String choiceCode(int choice) { return choice == AUTO ? AUTO_CODE : CODES[choice]; }

    /**
     * The language the camera itself is set to, from its locale: Traditional Chinese for Taiwan, Hong Kong and Macau
     * (or a Hant script tag), Simplified for any other Chinese, English for everything else — there is no table for it.
     */
    static int fromLocale(String language, String country) {
        if (!"zh".equalsIgnoreCase(language)) return EN;
        String c = country == null ? "" : country.toUpperCase(Locale.US);
        return c.equals("TW") || c.equals("HK") || c.equals("MO") || c.equals("HANT") ? ZH_HANT : ZH_HANS;
    }

    /** the language a choice shows: the camera's for Auto, else the one chosen */
    static int resolve(int choice, String language, String country) {
        return choice == AUTO ? fromLocale(language, country) : choice;
    }
}
