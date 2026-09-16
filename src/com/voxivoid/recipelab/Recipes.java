package com.voxivoid.recipelab;

/**
 * Film-look approximations built ONLY from settings the A6000 can store persistently. Names follow the popular
 * film-simulation lists; values are original. No gamma / per-hue colour depth / split-toning exists on this body,
 * so log profiles (S-Log, V-Log, Blackmagic Film) and tinted monochromes (selenium, cyanotype) are not included.
 *
 * style   : Creative Style (stored enum verified on camera: 1 standard, 2 vivid, 3 neutral, 6 mono, 14 sepia; 13 is some style we have not identified, 4..12 still guessed from the runtime list order)
 * sat/con/sharp : Creative Style adjustments (menu range -3..+3; beyond = experimental, camera core accepts sat ±16)
 * matrix  : 1 = PP3 alternate colour matrix (~+45% chroma, blue/green cross-talk)
 * wbMode  : 0 = leave WB as is · 1 = auto · 14 = colour temperature (kelvin)
 * ab / gm : WB fine tune  amber(+)/blue(-)  green(+)/magenta(-)  (-7..+7)
 * pe      : Picture Effect (persistent; when on, Creative Style is ignored by the camera and RAW is disabled)
 * ev      : exposure bias in 1/3 EV steps (persistent)
 * dro     : DRO 0 off, 1..5, 6 auto (persistent)
 * sub     : effect sub-parameter — Soft High-key tint 0 blue / 1 pink / 2 green, Toy tone 0..4, Partial hue 0..3, Posterization 0 colour / 1 b&w
 */
public class Recipes {
    public static class Recipe {
        public final int group; public final String name; public final int style, sat, con, sharp, matrix, wbMode, kelvin, ab, gm, pe, ev, dro, sub;
        Recipe(int group, String name, int style, int sat, int con, int sharp, int matrix, int wbMode, int kelvin, int ab, int gm) {
            this(group, name, style, sat, con, sharp, matrix, wbMode, kelvin, ab, gm, 0, 0, DRO_AUTO);
        }
        Recipe(int group, String name, int style, int sat, int con, int sharp, int matrix, int wbMode, int kelvin, int ab, int gm, int pe, int ev, int dro) {
            this(group, name, style, sat, con, sharp, matrix, wbMode, kelvin, ab, gm, pe, ev, dro, 0);
        }
        Recipe(int group, String name, int style, int sat, int con, int sharp, int matrix, int wbMode, int kelvin, int ab, int gm, int pe, int ev, int dro, int sub) {
            this.sub = sub;
            this.group = group; this.name = name; this.style = style; this.sat = sat; this.con = con; this.sharp = sharp; this.matrix = matrix;
            this.wbMode = wbMode; this.kelvin = kelvin; this.ab = ab; this.gm = gm; this.pe = pe; this.ev = ev; this.dro = dro;
        }
        public boolean isEffect() { return pe != 0; }
        /** one-line summary for lists: "Neutral  -4/-1  A1" */
        public String summary() {
            StringBuilder s = new StringBuilder();
            if (pe != 0) { s.append(PE_LABEL[pe]); String sl = subLabel(pe, sub); if (sl != null) s.append(' ').append(sl); } else s.append(STYLE_LABEL[style]).append("  ").append(sat > 0 ? "+" : "").append(sat).append('/').append(con > 0 ? "+" : "").append(con);
            if (matrix == 1 && pe == 0) s.append("  MTX");
            if (ev != 0) s.append("  ").append(evLabel(ev));
            if (dro != DRO_AUTO) s.append("  DRO ").append(droLabel(dro));
            if (wbMode == 14) s.append("  ").append(kelvin).append('K');
            if (ab != 0) s.append("  ").append(ab > 0 ? "A" + ab : "B" + (-ab));
            if (gm != 0) s.append("  ").append(gm > 0 ? "G" + gm : "M" + (-gm));
            return s.toString();
        }
    }

    public static final int STD = 1, VIVID = 2, NEUTRAL = 3, PORTRAIT = 4, LANDSCAPE = 5, MONO = 6, CLEAR = 7, DEEP = 8, LIGHT = 9, SUNSET = 10, NIGHT = 11, AUTUMN = 12, SEPIA = 14;
    /** index = stored enum; value = runtime color-mode name (API); null = an enum value the camera uses for something we have not identified */
    public static final String[] STYLE_NAMES = { null, "standard", "vivid", "neutral", "portrait", "landscape", "mono", "clear", "deep", "light", "sunset", "night", "red-leaves", null, "sepia" };
    public static final String[] STYLE_LABEL = { "?", "Standard", "Vivid", "Neutral", "Portrait", "Landscape", "B&W", "Clear", "Deep", "Light", "Sunset", "Night", "Autumn", "?", "Sepia" };

    private static final int AUTO = 1, K = 14;

    /** Picture Effect: stored byte = index in the runtime list (verified) */
    public static final String[] PE_KEYS = { "off", "toy-camera", "pop-color", "posterization", "retro-photo", "soft-high-key", "part-color", "rough-mono", "soft-focus", "hdr-art", "richtone-mono", "miniature", "illust", "watercolor" };
    public static final String[] PE_LABEL = { "off", "Toy", "Pop", "Poster", "Retro", "High-key", "Part col", "HC mono", "Soft foc", "HDR art", "Rich mono", "Miniature", "Illust", "Watercol" };
    public static final int PE_OFF = 0, PE_TOY = 1, PE_POP = 2, PE_RETRO = 4, PE_HIGHKEY = 5, PE_HCMONO = 7;
    /** a style whose runtime name we know — the others are enum values seen in the store but never identified */
    public static boolean styleKnown(int v) { return v >= 1 && v < STYLE_NAMES.length && STYLE_NAMES[v] != null; }
    /** chip / HUD labels with a "?n" fallback for an unidentified or out-of-table stored value */
    public static String styleLabel(int v) { return styleKnown(v) ? STYLE_LABEL[v] : "?" + v; }
    public static String peLabel(int v) { return v >= 0 && v < PE_LABEL.length ? PE_LABEL[v] : "?" + v; }
    /** effect sub-parameter (tint / tone / hue / mode): runtime key, stored slot, value names — index = stored byte */
    public static String subKey(int pe) { switch (pe) { case 5: return "pe-soft-high-key-effect"; case 1: return "pe-toy-camera-effect"; case 6: return "pe-part-color-effect"; case 3: return "pe-posterization-effect"; default: return null; } }
    public static int subId(int pe) { switch (pe) { case 5: return 0x010709d8; case 1: return 0x010706f3; case 6: return 0x010706ee; case 3: return 0x010706ef; default: return 0; } }
    public static String[] subValues(int pe) {
        switch (pe) {
            case 5: return new String[] { "blue", "pink", "green" };
            case 1: return new String[] { "normal", "cool", "warm", "green", "magenta" };
            case 6: return new String[] { "red", "green", "blue", "yellow" };
            case 3: return new String[] { "posterization-color", "posterization-bw" };
            default: return null;
        }
    }
    public static String subLabel(int pe, int sub) { String[] v = subValues(pe); return v == null ? null : (sub >= 0 && sub < v.length ? v[sub].replace("posterization-", "") : "?" + sub); }
    /** DRO: 0 off, 1..5 level, 6 auto */
    public static final int DRO_OFF = 0, DRO_AUTO = 6;
    public static String droLabel(int v) { return v == DRO_AUTO ? "auto" : v == 0 ? "off" : "Lv" + v; }
    /** exposure bias in 1/3 EV steps -> "+0.7" */
    public static String evLabel(int ev) {
        if (ev == 0) return "0";
        int a = Math.abs(ev); String frac = a % 3 == 0 ? ".0" : a % 3 == 1 ? ".3" : ".7";
        return (ev > 0 ? "+" : "-") + (a / 3) + frac;
    }

    // ---- groups (brands) — recipes below MUST be listed in group order
    public static final String[] GROUPS = { "Sony", "Fuji Sim", "Fuji Film", "Kodak", "Cine", "Ricoh GR", "Leica", "Hasselblad", "Canon / Nikon", "Pana / Olympus", "Other Stocks", "Ilford" };
    private static final int SONY = 0, FSIM = 1, FFILM = 2, KODAK = 3, CINE = 4, RICOH = 5, LEICA = 6, HASSEL = 7, CANIK = 8, PANOLY = 9, OTHER = 10, ILFORD = 11;

    public static final Recipe[] ALL = {
        // ---- Sony (Creative Looks from newer bodies — same pipeline, best fidelity)
        new Recipe(SONY,  "FACTORY (ST)",                        STD,      0,  0,  0, 0, AUTO, 0,     0,  0),
        new Recipe(SONY,  "Sony PT (portrait)",                  PORTRAIT, 0,  0,  0, 0, AUTO, 0,     0,  0),
        new Recipe(SONY,  "Sony NT (neutral)",                   NEUTRAL,  0,  0,  0, 0, AUTO, 0,     0,  0),
        new Recipe(SONY,  "Sony VV (vivid)",                     VIVID,    0,  0,  0, 0, AUTO, 0,     0,  0),
        new Recipe(SONY,  "Sony VV2",                            VIVID,    2,  1,  0, 1, AUTO, 0,     0,  0),
        new Recipe(SONY,  "Sony FL (film-like)",                 NEUTRAL, -4, -1,  0, 0, AUTO, 0,     1,  0),
        new Recipe(SONY,  "Sony IN (instant)",                   NEUTRAL, -6, -3,  0, 0, AUTO, 0,     0, -1),
        new Recipe(SONY,  "Sony SH (soft high-key)",             LIGHT,   -2, -2,  0, 0, AUTO, 0,     1,  0,  5,  3, 6),
        // ---- Fujifilm simulations
        new Recipe(FSIM,  "Provia",                              STD,      1,  0,  0, 0, AUTO, 0,     0,  0,  0,  0, 6),
        new Recipe(FSIM,  "Velvia",                              VIVID,    5,  1,  0, 1, AUTO, 0,     0,  0),
        new Recipe(FSIM,  "Astia",                               PORTRAIT, 0, -1,  0, 0, AUTO, 0,     1,  0,  0,  1, 6),
        new Recipe(FSIM,  "Classic Chrome",                      NEUTRAL, -5,  2,  0, 0, AUTO, 0,    -1,  0,  0, -1, 6),
        new Recipe(FSIM,  "Classic Negative",                    STD,     -3,  3,  1, 0, AUTO, 0,     0,  1,  0,  1, 6),
        new Recipe(FSIM,  "Nostalgic Neg",                       STD,      0,  0,  0, 0, AUTO, 0,     2,  0,  4,  1, 6),   // Retro Photo: amber, faded
        new Recipe(FSIM,  "Reala Ace",                           STD,      0,  1,  0, 0, AUTO, 0,     0,  0),
        new Recipe(FSIM,  "Pro Neg Std",                         PORTRAIT,-2, -1,  0, 0, AUTO, 0,     0,  0),
        new Recipe(FSIM,  "Pro Neg Hi",                          PORTRAIT,-2,  1,  0, 0, AUTO, 0,     0,  0),
        new Recipe(FSIM,  "Eterna",                              NEUTRAL, -6, -2, -1, 0, AUTO, 0,     0,  0,  0, -1, 3),
        new Recipe(FSIM,  "Eterna Bleach Bypass",                NEUTRAL, -9,  3,  0, 0, AUTO, 0,     0,  0,  0, -1, 6),
        new Recipe(FSIM,  "Acros",                               MONO,     0,  1,  1, 0, AUTO, 0,     0,  0),
        new Recipe(FSIM,  "Acros +Ye (yellow filter)",           MONO,     0,  1,  1, 0, K,    4000,  0,  0),
        new Recipe(FSIM,  "Acros +R (red filter)",               MONO,     0,  0,  0, 0, K,    2500,  0,  0,  7,  0, 6),   // HC Mono: deep blacks, dramatic sky
        new Recipe(FSIM,  "Acros +G (green filter)",             MONO,     0,  1,  1, 0, K,    5600,  0,  4),
        new Recipe(FSIM,  "Sepia",                               SEPIA,    0,  0,  0, 0, AUTO, 0,     0,  0),
        // ---- Fujifilm film stocks
        new Recipe(FFILM, "Fuji Pro 400H",                       STD,      0,  0,  0, 0, AUTO, 0,    -1,  1,  5,  2, 6, 2),   // Soft High-key, green tint: airy mint pastel
        new Recipe(FFILM, "Fuji Fortia 50",                      VIVID,    6,  2,  0, 1, AUTO, 0,     0, -1,  0, -1, 6),
        new Recipe(FFILM, "Fuji Superia 400",                    STD,      1,  1,  0, 0, AUTO, 0,     1,  1,  0,  1, 6),
        new Recipe(FFILM, "Fuji C200",                           STD,      0,  0,  0, 0, AUTO, 0,    -1,  1),
        new Recipe(FFILM, "Fuji Natura 1600",                    PORTRAIT,-2, -2,  0, 0, AUTO, 0,     1,  0,  0,  1, 6),
        // ---- Kodak
        new Recipe(KODAK, "Kodak Portra 160",                    PORTRAIT,-2, -1,  0, 0, AUTO, 0,     1,  0,  0,  2, 6),
        new Recipe(KODAK, "Kodak Portra 400",                    PORTRAIT,-1,  0,  0, 0, AUTO, 0,     2,  1,  0,  2, 6),
        new Recipe(KODAK, "Kodak Portra 800",                    STD,     -1,  1,  0, 0, AUTO, 0,     2,  0,  0,  1, 6),
        new Recipe(KODAK, "Kodak Gold 200",                      STD,      2,  1,  0, 0, AUTO, 0,     3,  1,  0,  1, 6),
        new Recipe(KODAK, "Kodak Ultra Max 400",                 STD,      3,  1,  0, 0, AUTO, 0,     2,  0,  0,  1, 6),
        new Recipe(KODAK, "Kodak Color Plus 200",                STD,      1,  1,  0, 0, AUTO, 0,     2,  1,  0,  1, 6),
        new Recipe(KODAK, "Kodak Ektar 100",                     VIVID,    3,  2,  1, 1, AUTO, 0,     1,  0,  0, -1, 6),
        new Recipe(KODAK, "Kodak Ektachrome E100",               CLEAR,    2,  1,  0, 0, AUTO, 0,    -1,  0,  0, -1, 6),
        new Recipe(KODAK, "Kodachrome 64",                       DEEP,     1,  2,  1, 0, AUTO, 0,     1, -1,  0, -1, 6),
        new Recipe(KODAK, "Kodak Vision3 500T (daylight)",       NEUTRAL, -1,  0,  0, 0, K,    3200,  0,  0,  0,  1, 3),
        new Recipe(KODAK, "Kodak Vision 200T (Asteroid City)",   STD,      0,  0,  0, 0, K,    4300,  1,  2,  4,  0, 6),   // Retro Photo + green/warm WB
        new Recipe(KODAK, "Kodak Tri-X 400",                     MONO,     0,  2,  2, 0, AUTO, 0,     0,  0,  0,  1, 6),
        new Recipe(KODAK, "Kodak T-Max",                         MONO,     0,  2,  3, 0, AUTO, 0,     0,  0,  0,  0, 6),
        new Recipe(KODAK, "Kodak Tri-X 1600 (pushed)",           MONO,     0,  0,  0, 0, AUTO, 0,     0,  0,  7,  1, 6),   // HC Mono
        // ---- Cine
        new Recipe(CINE,  "Cinestill 50D (Blue Velvet)",         STD,     -1,  1,  0, 0, K,    5600, -2,  0),
        new Recipe(CINE,  "Cinestill 800T",                      NEUTRAL, -2,  0,  0, 0, K,    3200,  0, -1,  0,  1, 6),
        new Recipe(CINE,  "Classic Cinema",                      NEUTRAL, -4, -2, -1, 0, K,    5000,  0,  0,  0, -1, 3),
        new Recipe(CINE,  "Rec709 Video (flat-ish)",             NEUTRAL, -2, -2,  0, 0, AUTO, 0,     0,  0,  0,  0, 5),
        // ---- Ricoh GR image controls
        new Recipe(RICOH, "GR Positive Film",                    STD,      3,  2,  0, 0, AUTO, 0,     2,  0,  0, -1, 6),
        new Recipe(RICOH, "GR Negative Film",                    NEUTRAL, -2,  1,  0, 0, AUTO, 0,    -1,  1,  0,  1, 6),
        new Recipe(RICOH, "GR Bleach Bypass",                    NEUTRAL, -8,  3,  0, 0, AUTO, 0,     0,  0),
        new Recipe(RICOH, "GR Retro",                            STD,     -3, -1,  0, 0, AUTO, 0,     3, -1,  4,  0, 6),
        new Recipe(RICOH, "GR Cross Process",                    VIVID,    2,  2,  0, 0, AUTO, 0,    -2,  4),
        new Recipe(RICOH, "GR Hi-Contrast B&W",                  MONO,     0,  3,  1, 0, AUTO, 0,     0,  0,  7,  0, 6),
        new Recipe(RICOH, "GR Hard Monotone",                    MONO,     0,  2,  2, 0, AUTO, 0,     0,  0),
        new Recipe(RICOH, "GR Soft Monotone",                    MONO,     0, -2, -1, 0, AUTO, 0,     0,  0),
        // ---- Leica
        new Recipe(LEICA, "Leica Contemporary",                  STD,      1,  1,  0, 0, AUTO, 0,     0,  0),
        new Recipe(LEICA, "Leica Classic",                       STD,     -1,  2,  0, 0, AUTO, 0,     1,  0),
        new Recipe(LEICA, "Leica Eternal",                       NEUTRAL, -3, -1,  0, 0, AUTO, 0,     1,  0,  0,  0, 3),
        new Recipe(LEICA, "Leica Monochrom",                     MONO,     0,  2,  1, 0, AUTO, 0,     0,  0),
        // ---- Hasselblad
        new Recipe(HASSEL,"Hasselblad HNCS Natural",             NEUTRAL, -1, -1,  0, 0, AUTO, 0,     0,  0),
        // ---- Canon / Nikon
        new Recipe(CANIK, "Canon Standard",                      STD,      1,  1,  0, 0, AUTO, 0,     1, -1),
        new Recipe(CANIK, "Canon Portrait",                      PORTRAIT, 0,  0, -1, 0, AUTO, 0,     1, -1),
        new Recipe(CANIK, "Canon Faithful",                      NEUTRAL,  0,  0,  0, 0, AUTO, 0,     0,  0),
        new Recipe(CANIK, "Nikon Flat",                          NEUTRAL, -3, -3, -1, 0, AUTO, 0,     0,  0,  0,  0, 5),
        new Recipe(CANIK, "Nikon Vivid",                         VIVID,    1,  1,  1, 0, AUTO, 0,     0,  0),
        // ---- Panasonic / Olympus
        new Recipe(PANOLY,"Pana L.Monochrome D",                 MONO,     0,  3,  1, 0, AUTO, 0,     0,  0),
        new Recipe(PANOLY,"Pana L.ClassicNeo",                   NEUTRAL, -3, -1,  0, 0, AUTO, 0,     2,  0,  0,  1, 6),
        new Recipe(PANOLY,"Olympus Pop Art",                     VIVID,    8,  2,  0, 1, AUTO, 0,     0,  0),
        new Recipe(PANOLY,"Olympus Pale & Light",                LIGHT,   -3, -2,  0, 0, AUTO, 0,     0,  0,  0,  2, 6),
        // ---- Other stocks
        new Recipe(OTHER, "Agfa Vista 200",                      STD,      2,  1,  0, 0, AUTO, 0,     2, -1,  0,  1, 6),
        new Recipe(OTHER, "Agfa Ultra 100",                      VIVID,    6,  1,  0, 1, AUTO, 0,     0,  0),
        new Recipe(OTHER, "Polaroid / Instax",                   STD,     -3, -2,  0, 0, AUTO, 0,     1, -2,  4,  1, 6),
        // ---- Ilford
        new Recipe(ILFORD,"Ilford HP5",                          MONO,     0,  1,  0, 0, AUTO, 0,     0,  0,  0,  1, 6),
        new Recipe(ILFORD,"Ilford FP4",                          MONO,     0,  1,  1, 0, AUTO, 0,     0,  0),
        new Recipe(ILFORD,"Ilford Delta 100",                    MONO,     0,  1,  1, 0, AUTO, 0,     0,  0),
        new Recipe(ILFORD,"Ilford Delta 3200",                   MONO,     0,  3, -2, 0, AUTO, 0,     0,  0,  0,  2, 6),
        new Recipe(ILFORD,"Ilford Pan F 50",                     MONO,     0,  2,  2, 0, AUTO, 0,     0,  0),
    };

    /** first recipe index of each group */
    public static final int[] GROUP_START = new int[GROUPS.length];
    public static final int[] GROUP_COUNT = new int[GROUPS.length];
    static {
        for (int g = 0; g < GROUPS.length; g++) GROUP_START[g] = -1;
        for (int i = 0; i < ALL.length; i++) {
            int g = ALL[i].group;
            if (GROUP_START[g] < 0) GROUP_START[g] = i;
            GROUP_COUNT[g]++;
        }
    }

    // ---- navigation: every step wraps, dir is +1 / -1
    /** the recipe after / before i over the whole table */
    public static int next(int i, int dir) { return (i + ALL.length + dir) % ALL.length; }
    /** the first recipe of the brand after / before i's */
    public static int nextGroupStart(int i, int dir) { return GROUP_START[(ALL[i].group + GROUPS.length + dir) % GROUPS.length]; }
    /** the recipe after / before i within its brand */
    public static int nextInGroup(int i, int dir) {
        int g = ALL[i].group, start = GROUP_START[g], n = GROUP_COUNT[g];
        return start + ((i - start + n + dir) % n);
    }
}
