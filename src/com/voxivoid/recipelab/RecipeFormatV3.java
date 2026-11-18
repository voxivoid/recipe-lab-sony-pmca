package com.voxivoid.recipelab;

import java.util.Map;

import static com.voxivoid.recipelab.CustomRecipes.bad;
import static com.voxivoid.recipelab.CustomRecipes.integer;

/**
 * Format 3 of the custom recipe file: format 2's keys, plus the picture profile ({@link IspProfiles.Look}) — {@code gamma}
 * and the adjustments after it. Only a recipe with a profile is written in it: one without stays a format 2 file
 * ({@link RecipeFormats#write}), so apps and Recipe Lab Studio versions that do not know profiles keep reading it. A
 * format 3 file is skipped by them as made by a newer version, rather than read without its profile — a different look.
 *
 * A format 3 file without {@code gamma}, or with {@code gamma: off}, is a recipe without a profile.
 *
 * Once released, this class does not change. No android.* import may appear here (tools/test.sh).
 */
final class RecipeFormatV3 implements RecipeFormat {
    private static final RecipeFormatV2 V2 = new RecipeFormatV2();

    public int version() { return 3; }

    public String write(Recipes.Recipe r, String madeOn) {
        // format 2's file with its format line swapped for this version's and the profile at the end
        String v2 = V2.write(r, madeOn), from = "\nformat: 2\n";
        int at = v2.indexOf(from);
        if (at < 0) throw new IllegalStateException("format 2 wrote no format line");
        StringBuilder s = new StringBuilder(v2.substring(0, at)).append("\nformat: 3\n").append(v2.substring(at + from.length()));
        IspProfiles.Look l = r.look == null ? null : r.look.normal();
        if (l == null) { line(s, "gamma", "off", gammas()); return s.toString(); }
        line(s, "gamma", IspProfiles.CURVE_CODES[l.curve], gammas());
        line(s, "color-mode", IspProfiles.GAMUT_CODES[l.gamut], join(IspProfiles.GAMUT_CODES, 0));
        line(s, "black-level", signed(l.black), range(IspProfiles.BLACK_MAX));
        line(s, "black-gamma", signed(l.blackGamma), range(IspProfiles.BGAMMA_MAX));
        line(s, "black-gamma-range", IspProfiles.RANGE_CODES[l.range], join(IspProfiles.RANGE_CODES, 0));
        line(s, "knee", l.knee == IspProfiles.KNEE_OFF ? "off" : String.valueOf(l.knee), "off, or the point in percent: " + IspProfiles.KNEE_MIN + " .. " + IspProfiles.KNEE_MAX);
        line(s, "knee-slope", signed(l.slope), range(IspProfiles.SLOPE_MAX));
        line(s, "color-saturation", signed(l.sat), range(IspProfiles.SAT_MAX));
        line(s, "color-phase", signed(l.phase), range(IspProfiles.PHASE_MAX));
        line(s, "s-curve", signed(l.contrast), range(IspProfiles.CONTRAST_MAX));
        return s.toString();
    }

    public CustomRecipes.Parsed read(Map<String, String> kv, String fileName) {
        CustomRecipes.Parsed p = V2.read(kv, fileName);
        if (p.recipe == null) return p;
        String v = kv.get("gamma");
        if (v == null || v.equals("off")) return p;
        int curve = IspProfiles.indexOf(IspProfiles.CURVE_CODES, v);
        if (curve < 1) return fail(bad("gamma", v));
        int gamut = IspProfiles.CAMERA;
        if ((v = kv.get("color-mode")) != null && (gamut = IspProfiles.indexOf(IspProfiles.GAMUT_CODES, v)) < 0) return fail(bad("color-mode", v));
        int range = IspProfiles.MIDDLE;
        if ((v = kv.get("black-gamma-range")) != null && (range = IspProfiles.indexOf(IspProfiles.RANGE_CODES, v)) < 0) return fail(bad("black-gamma-range", v));
        int knee = IspProfiles.KNEE_OFF;
        if ((v = kv.get("knee")) != null && !v.equals("off")) {
            Integer n = integer(v);
            if (n == null || n < IspProfiles.KNEE_MIN || n > IspProfiles.KNEE_MAX) return fail(bad("knee", v));
            knee = n;
        }
        String[] keys = { "black-level", "black-gamma", "knee-slope", "color-saturation", "color-phase", "s-curve" };
        int[] max = { IspProfiles.BLACK_MAX, IspProfiles.BGAMMA_MAX, IspProfiles.SLOPE_MAX, IspProfiles.SAT_MAX, IspProfiles.PHASE_MAX, IspProfiles.CONTRAST_MAX };
        int[] n = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            if ((v = kv.get(keys[i])) == null) continue;
            Integer x = integer(v);
            if (x == null || Math.abs(x) > max[i]) return fail(bad(keys[i], v));
            n[i] = x;
        }
        IspProfiles.Look look = new IspProfiles.Look(curve, gamut, n[0], n[1], range, knee, n[2], n[3], n[4], n[5]).normal();
        Recipes.Recipe r = p.recipe;
        return CustomRecipes.Parsed.ok(new Recipes.Recipe(r.group, r.name, r.style, r.sat, r.con, r.sharp, r.wbMode, r.kelvin, r.ab, r.gm, r.pe, r.ev, r.dro, r.sub, look));
    }

    private static CustomRecipes.Parsed fail(String why) { return CustomRecipes.Parsed.fail(why); }

    private static void line(StringBuilder s, String key, String value, String comment) {
        int at = s.length();
        s.append(key).append(": ").append(value);
        while (s.length() - at < 28) s.append(' ');
        s.append(" # ").append(comment).append('\n');
    }

    private static String signed(int v) { return v > 0 ? "+" + v : String.valueOf(v); }
    private static String range(int max) { return "-" + max + " .. +" + max; }
    private static String gammas() { return join(IspProfiles.CURVE_CODES, 0); }

    private static String join(String[] v, int from) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < v.length; i++) { if (b.length() > 0) b.append(' '); b.append(v[i]); }
        return b.toString();
    }
}
