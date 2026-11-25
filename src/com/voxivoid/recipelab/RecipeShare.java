package com.voxivoid.recipelab;

/**
 * A custom recipe as a link, for QR codes: {@code https://recipelab.io/r#<code>}. Recipe Lab Studio reads and writes
 * the same links (its {@code RecipeShare}): a phone's camera opens Studio with it, and Read Recipe QR Code here adds it.
 * The code is base64url, without padding, of these bytes (docs/DEVELOPMENT.md "Recipe share code"). The link is the
 * same for every version: byte 0 says how to read the rest.
 *
 * <pre>
 *  0      the version: 1 for a recipe without a picture profile, 2 for one with
 *  1      camera family: 0 {@link RecipeFormatV2#FAMILY}, the only one this app reads; another is refused
 *  2      style, index in {@link #STYLES} (runtime names, not the stored enum)
 *  3 4 5  saturation, contrast, sharpness, plus 3
 *  6      white balance: 0 auto, 1 keep, 25..99 the kelvin / 100
 *  7 8    amber-blue, green-magenta, plus 7
 *  9      exposure in thirds, plus 15
 *  10     DRO: 0 off, 1..5, 6 auto
 *  11     effect, index in {@link Recipes#PE_KEYS}
 *  12     effect option, index in {@link Recipes#subValues}; 0 for an effect without one
 *  13..   version 1: the name, ASCII, as {@link CustomRecipes#nameChar} allows, 1..24 characters
 * </pre>
 *
 * Version 2 is version 1 with the picture profile ({@link IspProfiles.Look}, as {@link IspProfiles.Look#normal} keeps it) between
 * the effect option and the name:
 *
 * <pre>
 *  13     gamma, index in {@link IspProfiles#CURVE_CODES}: 1..CURVES-1 (0, no profile, is a version 1 link)
 *  14     colour mode, index in {@link IspProfiles#GAMUT_CODES}
 *  15     black level, plus 15
 *  16     black gamma, plus 7
 *  17     black gamma range, index in {@link IspProfiles#RANGE_CODES}
 *  18     knee: 0 off, else the point in percent, 75..105
 *  19     knee slope, plus 5
 *  20     colour saturation, plus 32
 *  21     colour phase, plus 7
 *  22     s-curve, plus 7
 *  23..   the name, as version 1's
 * </pre>
 *
 * A recipe is written in the oldest version that can hold it: without a profile, version 1, so apps that predate version
 * 2 read it; they refuse a version 2 link rather than read it without its profile. Bytes 2..12 are this family's; a later
 * family has its own after byte 1.
 *
 * <h3>New versions</h3>
 * Assume this format grows. A version holds a fixed vocabulary (the tables below, the ranges); the tables are frozen, so a
 * new style, effect, gamma or colour mode goes at the end <b>and</b> a recipe that uses one is written in a new version,
 * never in one an older app already reads, which would find a value it does not know. The version byte is how an app that
 * predates a link knows to say so: a version or a family it does not know is {@link #needsNewerApp}, and the person is told
 * to update the app — never "invalid". Anything else wrong with a link (a value out of its range, a broken name, a
 * truncated code) is invalid. IspProfilesTest and RecipeShareTest pin each version's vocabulary and fail when one grows.
 * test/fixtures/recipe-share-vectors.tsv is the same file in all three repos (the apps and recipelab.io).
 */
public final class RecipeShare {
    private RecipeShare() {}

    /** the version of a recipe without a picture profile, and of one with */
    public static final int VERSION = 1, VERSION_PROFILE = 2;
    public static final String PREFIX = "https://recipelab.io/r#";
    private static final String WWW_PREFIX = "https://www.recipelab.io/r#";
    /** this family's code in byte 1 */
    static final int FAMILY_CODE = 0;
    private static final int HEAD = 13, PROFILE_HEAD = 23;

    /** Style codes, by runtime name: frozen. */
    static final String[] STYLES = { "standard", "vivid", "neutral", "portrait", "landscape", "mono", "clear", "deep",
            "light", "sunset", "night", "red-leaves", "sepia" };

    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    /** The link for {@code r}: version 2 with a picture profile, else version 1; null when its name or a value is one a recipe cannot hold. */
    public static String encode(Recipes.Recipe r) {
        String name = r.name == null ? "" : r.name.trim();
        if (CustomRecipes.problem(r) != null || !nameOk(name)) return null;
        int style = styleCode(r.style);
        if (style < 0) return null;
        IspProfiles.Look l = r.hasProfile() ? r.look.normal() : null;
        if (l != null && l.curve == IspProfiles.OFF) l = null;
        int head = l == null ? HEAD : PROFILE_HEAD;
        byte[] b = new byte[head + name.length()];
        b[0] = (byte) (l == null ? VERSION : VERSION_PROFILE);
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
        if (l != null) {
            b[13] = (byte) l.curve;
            b[14] = (byte) l.gamut;
            b[15] = (byte) (l.black + IspProfiles.BLACK_MAX);
            b[16] = (byte) (l.blackGamma + IspProfiles.BGAMMA_MAX);
            b[17] = (byte) l.range;
            b[18] = (byte) (l.knee == IspProfiles.KNEE_OFF ? 0 : l.knee);
            b[19] = (byte) (l.slope + IspProfiles.SLOPE_MAX);
            b[20] = (byte) (l.sat + IspProfiles.SAT_MAX);
            b[21] = (byte) (l.phase + IspProfiles.PHASE_MAX);
            b[22] = (byte) (l.contrast + IspProfiles.CONTRAST_MAX);
        }
        for (int i = 0; i < name.length(); i++) b[head + i] = (byte) name.charAt(i);
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

    /**
     * Whether {@code link} is a recipe link this build cannot read because it was written by a newer app: a version this
     * build does not know, or a camera family it does not. False for a link it reads and for one that is merely damaged.
     */
    public static boolean needsNewerApp(String link) {
        if (link == null) return false;
        if (link.startsWith(PREFIX)) return codeNeedsNewerApp(link.substring(PREFIX.length()));
        if (link.startsWith(WWW_PREFIX)) return codeNeedsNewerApp(link.substring(WWW_PREFIX.length()));
        return false;
    }

    /** {@link #needsNewerApp} for a bare code (after the #) */
    static boolean codeNeedsNewerApp(String code) {
        int[] b = unBase64Url(code);
        if (b == null || b.length < 2) return false;
        if (b[0] == VERSION || b[0] == VERSION_PROFILE) return b[1] != FAMILY_CODE;
        return b[0] > VERSION_PROFILE;
    }

    /** The recipe a bare code carries (after the #), of either version; null when it cannot be read. */
    static Recipes.Recipe decodeCode(String code) {
        int[] b = unBase64Url(code);
        if (b == null || b.length < 2 || (b[0] != VERSION && b[0] != VERSION_PROFILE)) return null;
        int head = b[0] == VERSION ? HEAD : PROFILE_HEAD;
        if (b[1] != FAMILY_CODE || b.length <= head) return null;   // made for other cameras
        if (b[2] >= STYLES.length || b[11] >= Recipes.PE_KEYS.length) return null;
        int pe = b[11];
        String[] sv = Recipes.subValues(pe);
        if (sv == null ? b[12] != 0 : b[12] >= sv.length) return null;
        StringBuilder name = new StringBuilder();
        IspProfiles.Look look = null;
        if (head == PROFILE_HEAD && (look = look(b)) == null) return null;
        for (int i = head; i < b.length; i++) {
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
                wbMode, kelvin, b[7] - 7, b[8] - 7, pe, b[9] - 15, b[10], b[12], look);
        return CustomRecipes.problem(r) == null ? r : null;
    }

    /** version 2's picture profile, bytes 13..22; null when a byte is out of its range */
    private static IspProfiles.Look look(int[] b) {
        int knee = b[18] == 0 ? IspProfiles.KNEE_OFF : b[18];
        if (b[13] < 1 || b[13] >= IspProfiles.CURVES || b[14] >= IspProfiles.GAMUTS || b[17] >= IspProfiles.RANGE_CODES.length
                || b[15] > 2 * IspProfiles.BLACK_MAX || b[16] > 2 * IspProfiles.BGAMMA_MAX
                || (b[18] != 0 && (b[18] < IspProfiles.KNEE_MIN || b[18] > IspProfiles.KNEE_MAX))
                || b[19] > 2 * IspProfiles.SLOPE_MAX || b[20] > 2 * IspProfiles.SAT_MAX
                || b[21] > 2 * IspProfiles.PHASE_MAX || b[22] > 2 * IspProfiles.CONTRAST_MAX) return null;
        return new IspProfiles.Look(b[13], b[14], b[15] - IspProfiles.BLACK_MAX, b[16] - IspProfiles.BGAMMA_MAX, b[17], knee,
                b[19] - IspProfiles.SLOPE_MAX, b[20] - IspProfiles.SAT_MAX, b[21] - IspProfiles.PHASE_MAX,
                b[22] - IspProfiles.CONTRAST_MAX).normal();
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
