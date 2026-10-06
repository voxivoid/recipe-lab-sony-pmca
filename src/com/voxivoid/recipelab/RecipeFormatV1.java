package com.voxivoid.recipelab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.voxivoid.recipelab.CustomRecipes.bad;
import static com.voxivoid.recipelab.CustomRecipes.in;
import static com.voxivoid.recipelab.CustomRecipes.indexOf;
import static com.voxivoid.recipelab.CustomRecipes.integer;
import static com.voxivoid.recipelab.CustomRecipes.kelvinOk;
import static com.voxivoid.recipelab.CustomRecipes.thirds;
import static com.voxivoid.recipelab.Params.*;

/**
 * Format 1 of the custom recipe file: YAML, one key per value the chips show, English, hand-editable. Style and effect by
 * their runtime names, exposure in thirds as "+0.7", white balance as "auto", "keep" or "5600K"; every line written
 * with its allowed range beside it. A key left out takes the factory look's value.
 *
 * Once released, this class does not change: files written with it must keep loading. A change to what a key means is
 * a new version (see {@link RecipeFormats}).
 *
 * No android.* import may appear here (tools/test.sh).
 */
final class RecipeFormatV1 implements RecipeFormat {
    public int version() { return 1; }

    // ------------------------------------------------------------ writing
    public String write(Recipes.Recipe r, String madeOn) {
        StringBuilder s = new StringBuilder();
        s.append("# Recipe Lab custom recipe. Keep it in the ").append(CustomRecipes.DIR).append(" folder of a memory card, under a name of\n");
        s.append("# up to eight letters or digits and .YML: the camera reads no longer file names. Its own name is below.\n");
        s.append("# Edit with care: a value outside the range beside it makes the app skip this file.\n");
        line(s, "format", String.valueOf(version()), null);
        line(s, "name", quoted(r.name), null);                     // quoted: to any YAML reader a name is text
        if (madeOn != null && !madeOn.trim().isEmpty()) line(s, "made-on", quoted(madeOn.trim()), "the camera it was saved on");
        line(s, "style", Recipes.STYLE_NAMES[r.style], knownStyles());
        line(s, "saturation", signed(r.sat), "-3 .. +3");
        line(s, "contrast", signed(r.con), "-3 .. +3");
        line(s, "sharpness", signed(r.sharp), "-3 .. +3");
        line(s, "effect", Recipes.PE_KEYS[r.pe], join(Recipes.PE_KEYS));
        String[] sv = Recipes.subValues(r.pe);
        if (sv != null) line(s, "effect-option", sv[r.sub], join(sv));
        line(s, "white-balance", r.wbMode == WB_KELVIN ? r.kelvin + "K" : r.wbMode == WB_AUTO ? "auto" : "keep", "auto, keep, or 2500K .. 9900K in 100K steps");
        line(s, "amber-blue", signed(r.ab), "-7 (blue) .. +7 (amber)");
        line(s, "green-magenta", signed(r.gm), "-7 (magenta) .. +7 (green)");
        line(s, "exposure", r.ev == 0 ? "0" : Recipes.evLabel(r.ev), "-5.0 .. +5.0 in thirds: .0 .3 .7");
        line(s, "dro", r.dro == Recipes.DRO_AUTO ? "auto" : r.dro == 0 ? "off" : String.valueOf(r.dro), "off, auto, 1 .. 5");
        return s.toString();
    }

    private static void line(StringBuilder s, String key, String value, String comment) {
        int at = s.length();
        s.append(key).append(": ").append(value);
        if (comment != null) { while (s.length() - at < 28) s.append(' '); s.append(" # ").append(comment); }
        s.append('\n');
    }

    /** a YAML double-quoted scalar: the quote and the backslash escaped */
    private static String quoted(String v) { return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    private static String signed(int v) { return v > 0 ? "+" + v : String.valueOf(v); }

    private static String knownStyles() {
        List<String> k = new ArrayList<String>();
        for (int i = 0; i < Recipes.STYLE_NAMES.length; i++) if (Recipes.styleKnown(i)) k.add(Recipes.STYLE_NAMES[i]);
        return join(k.toArray(new String[0]));
    }

    private static String join(String[] v) {
        StringBuilder b = new StringBuilder();
        for (String x : v) { if (b.length() > 0) b.append(' '); b.append(x); }
        return b.toString();
    }

    // ------------------------------------------------------------ reading
    public CustomRecipes.Parsed read(Map<String, String> kv, String fileName) {
        String name = kv.get("name");
        if (name == null || name.isEmpty()) name = CustomRecipes.stem(fileName);
        String np = CustomRecipes.nameProblem(name, Collections.<String>emptyList(), null);
        if (np != null) return fail(np);

        String v;
        int style = Recipes.STD;
        if ((v = kv.get("style")) != null && (style = indexOf(Recipes.STYLE_NAMES, v)) < 0) return fail(bad("style", v));
        int[] adj = new int[3];
        String[] adjKeys = { "saturation", "contrast", "sharpness" };
        for (int i = 0; i < 3; i++) {
            if ((v = kv.get(adjKeys[i])) == null) continue;
            Integer n = integer(v);
            if (n == null || !in(n, -3, 3)) return fail(bad(adjKeys[i], v));
            adj[i] = n;
        }
        int pe = 0;
        if ((v = kv.get("effect")) != null && (pe = indexOf(Recipes.PE_KEYS, v)) < 0) return fail(bad("effect", v));
        int sub = 0;
        String[] sv = Recipes.subValues(pe);
        if (sv != null && (v = kv.get("effect-option")) != null && (sub = indexOf(sv, v)) < 0) return fail(bad("effect-option", v));
        int wb = WB_AUTO, kelvin = 0;
        if ((v = kv.get("white-balance")) != null) {
            String w = v.toLowerCase(Locale.US).replace(" ", "");
            if (w.equals("auto")) wb = WB_AUTO;
            else if (w.equals("keep")) wb = 0;
            else if (w.matches("\\d{4}k?") && kelvinOk(Integer.parseInt(w.replace("k", "")))) { wb = WB_KELVIN; kelvin = Integer.parseInt(w.replace("k", "")); }
            else return fail(bad("white-balance", v));
        }
        int[] fine = new int[2];
        String[] fineKeys = { "amber-blue", "green-magenta" };
        for (int i = 0; i < 2; i++) {
            if ((v = kv.get(fineKeys[i])) == null) continue;
            Integer n = integer(v);
            if (n == null || !in(n, -7, 7)) return fail(bad(fineKeys[i], v));
            fine[i] = n;
        }
        int ev = 0;
        if ((v = kv.get("exposure")) != null) {
            Integer t = thirds(v);
            if (t == null || !in(t, ROW_MIN[R_EV], ROW_MAX[R_EV])) return fail(bad("exposure", v));
            ev = t;
        }
        int dro = Recipes.DRO_AUTO;
        if ((v = kv.get("dro")) != null) {
            String d = v.toLowerCase(Locale.US);
            if (d.equals("auto")) dro = Recipes.DRO_AUTO;
            else if (d.equals("off")) dro = 0;
            else if (d.matches("[1-5]")) dro = Integer.parseInt(d);
            else return fail(bad("dro", v));
        }
        Recipes.Recipe r = new Recipes.Recipe(Recipes.CUSTOM, name.trim(), style, adj[0], adj[1], adj[2], wb, kelvin, fine[0], fine[1], pe, ev, dro, sub);
        String p = CustomRecipes.problem(r);                          // the reader above and the store's ranges must agree
        return p != null ? fail(p) : CustomRecipes.Parsed.ok(r);
    }

    private static CustomRecipes.Parsed fail(String why) { return CustomRecipes.Parsed.fail(why); }
}
