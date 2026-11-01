package com.voxivoid.recipelab;

/**
 * A custom recipe as a link, for QR codes: {@code https://recipelab.io/r#<code>}. Recipe Lab Studio reads and writes
 * the same links (its {@code RecipeShare}): a phone's camera opens Studio with it, and Read Recipe QR Code here adds it.
 * The code is base64url, without padding, of these bytes (version 1; docs/DEVELOPMENT.md "Recipe share code"):
 *
 * <pre>
 *  0      1, the version
 *  1      camera family: 0 {@link RecipeFormatV2#FAMILY}, the only one this app reads; another is refused
 *  2      style, index in {@link #STYLES} (runtime names, not the stored enum)
 *  3 4 5  saturation, contrast, sharpness, plus 3
 *  6      white balance: 0 auto, 1 keep, 25..99 the kelvin / 100
 *  7 8    amber-blue, green-magenta, plus 7
 *  9      exposure in thirds, plus 15
 *  10     DRO: 0 off, 1..5, 6 auto
 *  11     effect, index in {@link Recipes#PE_KEYS}
 *  12     effect option, index in {@link Recipes#subValues}; 0 for an effect without one
 *  13..   the name, ASCII, as {@link CustomRecipes#nameChar} allows, 1..24 characters
 * </pre>
 *
 * Bytes 2..12 are this family's; a later family has its own after byte 1.
 *
 * The tables are frozen: a new style or effect goes at the end. test/fixtures/recipe-share-vectors.tsv is the same
 * file in both apps.
 */
public final class RecipeShare {
    private RecipeShare() {}

    public static final int VERSION = 1;
    public static final String PREFIX = "https://recipelab.io/r#";
    private static final String WWW_PREFIX = "https://www.recipelab.io/r#";
    /** this family's code in byte 1 */
    static final int FAMILY_CODE = 0;
    private static final int HEAD = 13;

    /** Style codes, by runtime name: frozen. */
    static final String[] STYLES = { "standard", "vivid", "neutral", "portrait", "landscape", "mono", "clear", "deep",
            "light", "sunset", "night", "red-leaves", "sepia" };

    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    /** The link for {@code r}; null when its name or a value is one a recipe cannot hold. */
    public static String encode(Recipes.Recipe r) {
        String name = r.name == null ? "" : r.name.trim();
        if (CustomRecipes.problem(r) != null || !nameOk(name)) return null;
        int style = styleCode(r.style);
        if (style < 0) return null;
        byte[] b = new byte[HEAD + name.length()];
        b[0] = VERSION;
        b[1] = FAMILY_CODE;
        b[2] = (byte) style;
        b[3] = (byte) (r.sat + 3);
        b[4] = (byte) (r.con + 3);
        b[5] = (byte) (r.sharp + 3);
        b[6] = (byte) (r.wbMode == Params.WB_AUTO ? 0 : r.wbMode == Params.WB_KELVIN ? r.kelvin / 100 : 1);
        b[7] = (byte) (r.ab + 7);
        b[8] = (byte) (r.gm + 7);
        b[9] = (byte) (r.ev + 15);
        b[10] = (byte) r.dro;
        b[11] = (byte) r.pe;
        b[12] = (byte) (Recipes.subValues(r.pe) == null ? 0 : r.sub);
        for (int i = 0; i < name.length(); i++) b[HEAD + i] = (byte) name.charAt(i);
        return PREFIX + base64Url(b);
    }

    /** Whether {@code text} (what a QR code holds) is a recipe link at all, readable or not. */
    public static boolean isLink(String text) {
        return text != null && (text.startsWith(PREFIX) || text.startsWith(WWW_PREFIX));
    }

    /** The custom recipe a link carries; null when it is not a recipe link or its code cannot be read. */
    public static Recipes.Recipe decode(String link) {
        if (link == null) return null;
        if (link.startsWith(PREFIX)) return decodeCode(link.substring(PREFIX.length()));
        if (link.startsWith(WWW_PREFIX)) return decodeCode(link.substring(WWW_PREFIX.length()));
        return null;
    }

    /** The recipe a bare code carries (after the #); null when it cannot be read. */
    static Recipes.Recipe decodeCode(String code) {
        int[] b = unBase64Url(code);
        if (b == null || b.length < 2 || b[0] != VERSION) return null;
        if (b[1] != FAMILY_CODE || b.length <= HEAD) return null;   // made for other cameras
        if (b[2] >= STYLES.length || b[11] >= Recipes.PE_KEYS.length) return null;
        int pe = b[11];
        String[] sv = Recipes.subValues(pe);
        if (sv == null ? b[12] != 0 : b[12] >= sv.length) return null;
        StringBuilder name = new StringBuilder();
        for (int i = HEAD; i < b.length; i++) {
            if (b[i] > 0x7f) return null;
            name.append((char) b[i]);
        }
        String n = name.toString();
        if (!nameOk(n)) return null;
        int wbMode, kelvin = 0;
        if (b[6] == 0) wbMode = Params.WB_AUTO;
        else if (b[6] == 1) wbMode = 0;
        else { wbMode = Params.WB_KELVIN; kelvin = b[6] * 100; }
        Recipes.Recipe r = new Recipes.Recipe(Recipes.CUSTOM, n, styleEnum(STYLES[b[2]]), b[3] - 3, b[4] - 3, b[5] - 3,
                wbMode, kelvin, b[7] - 7, b[8] - 7, pe, b[9] - 15, b[10], b[12]);
        return CustomRecipes.problem(r) == null ? r : null;
    }

    /** A name the link can carry: trimmed, 1..24 characters a name may hold. */
    private static boolean nameOk(String n) {
        if (n.isEmpty() || n.length() > CustomRecipes.NAME_MAX || !n.equals(n.trim())) return false;
        for (int i = 0; i < n.length(); i++) if (!CustomRecipes.nameChar(n.charAt(i))) return false;
        return true;
    }

    private static int styleCode(int styleEnum) {
        if (!Recipes.styleKnown(styleEnum)) return -1;
        String runtime = Recipes.STYLE_NAMES[styleEnum];
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(runtime)) return i;
        return -1;
    }

    private static int styleEnum(String runtime) {
        for (int i = 0; i < Recipes.STYLE_NAMES.length; i++) if (runtime.equals(Recipes.STYLE_NAMES[i])) return i;
        return -1;
    }

    static String base64Url(byte[] b) {
        StringBuilder s = new StringBuilder((b.length * 4 + 2) / 3);
        for (int i = 0; i < b.length; i += 3) {
            int n = (b[i] & 0xff) << 16 | (i + 1 < b.length ? (b[i + 1] & 0xff) << 8 : 0) | (i + 2 < b.length ? b[i + 2] & 0xff : 0);
            s.append(B64.charAt(n >> 18 & 63)).append(B64.charAt(n >> 12 & 63));
            if (i + 1 < b.length) s.append(B64.charAt(n >> 6 & 63));
            if (i + 2 < b.length) s.append(B64.charAt(n & 63));
        }
        return s.toString();
    }

    /** Bytes (0..255) of an unpadded base64url string; null for any other character or a length no encoding has. */
    static int[] unBase64Url(String s) {
        if (s == null || s.isEmpty() || s.length() % 4 == 1) return null;
        int[] out = new int[s.length() * 3 / 4];
        int bits = 0, have = 0, o = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = B64.indexOf(s.charAt(i));
            if (v < 0) return null;
            bits = bits << 6 | v;
            have += 6;
            if (have >= 8) { have -= 8; out[o++] = bits >> have & 0xff; }
        }
        // The bits left over must be zero, or another string would encode the same bytes.
        if ((bits & ((1 << have) - 1)) != 0) return null;
        return out;
    }
}
