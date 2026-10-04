package com.voxivoid.recipelab;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.voxivoid.recipelab.Params.*;

/**
 * Custom recipes (issues #14 and #15): the user's own looks, one text file each in {@link #DIR} on the memory card, so
 * they survive an uninstall, show up when the camera is mounted over USB, and can be copied from one card to another.
 *
 * <h3>The file and its versions</h3>
 * Reading and writing the file is {@link RecipeFormats}' job: one {@link RecipeFormat} per version of the format, each
 * with its own reader and writer, the newest the one the app writes. This class keeps what does not change with the
 * format — names, file names, the folder, and the ranges every format's values must fall in.
 *
 * <h3>Untrusted input</h3>
 * A file may come from anyone. Every value must be one the store accepts ({@link #problem}); a file with one that is not
 * is skipped with the reason, not clamped. Two files never share a name: the second is skipped, so nothing a card
 * brings in overwrites a recipe.
 *
 * <h3>The card's file system</h3>
 * Apps reach the card through Sony's FUSE layer (libInfraFuFsys, mounted at /mnt/sdcard), which takes DOS 8.3 names
 * only: a longer one is ENAMETOOLONG, letters are upper case, and there is no fsync. So the folder is {@link #DIR}, the
 * app writes {@code GOLDENHO.YML}-style names ({@link #fileName}), and the recipe's real name lives inside the file.
 *
 * MainActivity finds the card and owns the list ({@link Library}); this class decides, reads and writes. No android.*
 * import may appear here (tools/test.sh).
 */
final class CustomRecipes {
    private CustomRecipes() {}

    /** the folder at the root of the memory card: eight characters at most, as the card's file system takes no longer name */
    static final String DIR = "RECIPES";
    /** YAML, in the 8.3 the card takes: the file says what it is, and any editor that knows YAML colours it */
    static final String EXT = ".YML";
    /** the file a save writes before it takes the recipe's name; 8.3 like every name on the card, and never read as a recipe */
    static final String TMP = "SAVING.TMP";
    /** a recipe file is a few hundred bytes; anything far bigger is not one */
    static final int MAX_BYTES = 8192;
    /** the longest name the name editor takes, and that a file may carry */
    static final int NAME_MAX = 24;
    /** what a name may hold besides ASCII letters and digits: safe in a FAT file name, drawn by every font the app has */
    static final String NAME_PUNCT = " -.'()&+";
    /** the default name of a new recipe, numbered from 2 when taken. Data, like a recipe name: it ends up in a file */
    static final String UNTITLED = "Untitled";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** a recipe as it was read from the card, and the file it lives in */
    static final class Entry {
        final Recipes.Recipe recipe;
        final String file;
        Entry(Recipes.Recipe recipe, String file) { this.recipe = recipe; this.file = file; }
    }

    /** what a folder held: the recipes, A to Z, and one "file: reason" line per file that was skipped */
    static final class Loaded {
        final List<Entry> entries;
        final List<String> skipped;
        Loaded(List<Entry> entries, List<String> skipped) { this.entries = entries; this.skipped = skipped; }
    }

    /** one file read: the recipe, or why there is none */
    static final class Parsed {
        final Recipes.Recipe recipe;
        final String error;
        private Parsed(Recipes.Recipe recipe, String error) { this.recipe = recipe; this.error = error; }
        static Parsed ok(Recipes.Recipe r) { return new Parsed(r, null); }
        static Parsed fail(String why) { return new Parsed(null, why); }
    }

    // ------------------------------------------------------------ recipes from rows
    /**
     * The staged rows as a custom recipe. A white balance other than auto or kelvin — a preset set in the camera's own
     * menu — is kept as "keep", which leaves the camera's alone; the sub-setting only counts for an effect that has one.
     */
    static Recipes.Recipe recipe(String name, int[] rows) {
        int wb = rows[R_WBMODE] == WB_AUTO || rows[R_WBMODE] == WB_KELVIN ? rows[R_WBMODE] : 0, pe = rows[R_PE];
        return new Recipes.Recipe(Recipes.CUSTOM, name, rows[R_STYLE], rows[R_SAT], rows[R_CON], rows[R_SHARP],
                wb, wb == WB_KELVIN ? rows[R_KELVIN] * 100 : 0, rows[R_AB], rows[R_GM], pe, rows[R_EV], rows[R_DRO],
                Recipes.subValues(pe) != null ? rows[R_SUB] : 0);
    }

    /** the same recipe under another name */
    static Recipes.Recipe renamed(Recipes.Recipe r, String name) {
        return new Recipes.Recipe(Recipes.CUSTOM, name, r.style, r.sat, r.con, r.sharp, r.wbMode, r.kelvin, r.ab, r.gm, r.pe, r.ev, r.dro, r.sub);
    }

    /** null when every value is one the settings store takes; else the first that is not, as the file would spell it */
    static String problem(Recipes.Recipe r) {
        if (!Recipes.styleKnown(r.style)) return bad("style", "?" + r.style);
        if (!in(r.sat, -3, 3)) return bad("saturation", r.sat);
        if (!in(r.con, -3, 3)) return bad("contrast", r.con);
        if (!in(r.sharp, -3, 3)) return bad("sharpness", r.sharp);
        if (!in(r.pe, 0, Recipes.PE_KEYS.length - 1)) return bad("effect", "?" + r.pe);
        String[] sv = Recipes.subValues(r.pe);
        if (sv != null ? !in(r.sub, 0, sv.length - 1) : r.sub != 0) return bad("effect-option", "?" + r.sub);
        if (r.wbMode != 0 && r.wbMode != WB_AUTO && r.wbMode != WB_KELVIN) return bad("white-balance", "?" + r.wbMode);
        if (r.wbMode == WB_KELVIN && !kelvinOk(r.kelvin)) return bad("white-balance", r.kelvin + "K");
        if (!in(r.ab, -7, 7)) return bad("amber-blue", r.ab);
        if (!in(r.gm, -7, 7)) return bad("green-magenta", r.gm);
        if (!in(r.ev, ROW_MIN[R_EV], ROW_MAX[R_EV])) return bad("exposure", r.ev);
        if (!in(r.dro, 0, Recipes.DRO_AUTO)) return bad("dro", r.dro);
        return null;
    }

    static boolean in(int v, int lo, int hi) { return v >= lo && v <= hi; }
    static boolean kelvinOk(int k) { return k % 100 == 0 && in(k / 100, ROW_MIN[R_KELVIN], ROW_MAX[R_KELVIN]); }
    static String bad(String key, Object value) { return Lang.t("custom_bad_value", key, String.valueOf(value)); }

    // ------------------------------------------------------------ names
    /** whether a character may be part of a name */
    static boolean nameChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || NAME_PUNCT.indexOf(c) >= 0;
    }

    /**
     * Why {@code name} cannot be a custom recipe's name, or null when it can. {@code taken} are the names already used;
     * {@code self} is the recipe being renamed, which may keep its own name in another case.
     */
    static String nameProblem(String name, Collection<String> taken, String self) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return Lang.t("name_empty");
        if (n.length() > NAME_MAX) return Lang.t("name_too_long", NAME_MAX);
        for (char c : n.toCharArray()) if (!nameChar(c)) return Lang.t("name_bad_chars");
        for (String t : taken) if (t.equalsIgnoreCase(n) && (self == null || !self.equalsIgnoreCase(n))) return Lang.t("name_taken", t);
        return null;
    }

    /** the name a new recipe starts with: Untitled, then Untitled 2, 3 … — the first nobody has */
    static String defaultName(Collection<String> taken) {
        Set<String> lower = lower(taken);
        if (!lower.contains(UNTITLED.toLowerCase(Locale.US))) return UNTITLED;
        for (int n = 2; ; n++) if (!lower.contains((UNTITLED + " " + n).toLowerCase(Locale.US))) return UNTITLED + " " + n;
    }

    /**
     * The file a name is kept in, as the card's file system takes it: an 8.3 name, the first eight letters and digits of
     * the name in capitals plus {@link #EXT} — "Golden Hour" → GOLDENHO.YML. When another file has it, the end of the
     * eight gives way to a number: GOLDENH2.YML, GOLDENH3.YML … {@code takenFiles} are compared ignoring case.
     */
    static String fileName(String name, Collection<String> takenFiles) {
        StringBuilder b = new StringBuilder();
        for (char c : name.toUpperCase(Locale.US).toCharArray()) if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) b.append(c);
        String base = b.length() == 0 ? "RECIPE" : b.substring(0, Math.min(8, b.length()));
        Set<String> lower = lower(takenFiles);
        String f = base + EXT;
        for (int n = 2; lower.contains(f.toLowerCase(Locale.US)); n++) {
            String num = String.valueOf(n);
            f = base.substring(0, Math.min(base.length(), 8 - num.length())) + num + EXT;
        }
        return f;
    }

    private static Set<String> lower(Collection<String> s) {
        Set<String> out = new HashSet<String>();
        for (String x : s) out.add(x.toLowerCase(Locale.US));
        return out;
    }

    // ------------------------------------------------------------ helpers the file formats share (RecipeFormat*)
    static String stem(String fileName) {
        if (fileName == null) return "";
        return fileName.toLowerCase(Locale.US).endsWith(EXT.toLowerCase(Locale.US)) ? fileName.substring(0, fileName.length() - EXT.length()) : fileName;
    }

    /** the position of {@code v} in a table of runtime names, ignoring case; -1 for a name it does not hold */
    static int indexOf(String[] names, String v) {
        for (int i = 0; i < names.length; i++) if (names[i] != null && names[i].equalsIgnoreCase(v)) return i;
        return -1;
    }

    /** a signed whole number of up to three digits, "+2" or "-3"; null for anything else */
    static Integer integer(String v) { return v.matches("[+-]?\\d{1,3}") ? Integer.parseInt(v.startsWith("+") ? v.substring(1) : v) : null; }

    /** "+0.7" → 2, "-1" → -3, "1.3" → 4: whole stops and a third digit of 0, 3 or 7; null for anything else */
    static Integer thirds(String v) {
        if (!v.matches("[+-]?\\d{1,2}(\\.\\d)?")) return null;
        boolean neg = v.startsWith("-");
        String a = v.replace("+", "").replace("-", "");
        int dot = a.indexOf('.'), whole = Integer.parseInt(dot < 0 ? a : a.substring(0, dot)), frac;
        char fd = dot < 0 ? '0' : a.charAt(dot + 1);
        if (fd == '0') frac = 0; else if (fd == '3') frac = 1; else if (fd == '7') frac = 2; else return null;
        int t = whole * 3 + frac;
        return neg ? -t : t;
    }

    // ------------------------------------------------------------ the folder
    /** every recipe file in {@code dir}, A to Z by name; a folder that does not exist yet holds none */
    static Loaded load(File dir) {
        List<Entry> ok = new ArrayList<Entry>();
        List<String> skipped = new ArrayList<String>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return new Loaded(ok, skipped);
        Arrays.sort(files, new Comparator<File>() { public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); } });
        Map<String, String> byName = new HashMap<String, String>();   // lower-case name → the file that has it
        for (File f : files) {
            String fn = f.getName();
            if (fn.startsWith(".") || !fn.toLowerCase(Locale.US).endsWith(EXT.toLowerCase(Locale.US)) || !f.isFile()) continue;
            Parsed p;
            if (f.length() > MAX_BYTES) p = Parsed.fail(Lang.t("custom_too_large"));
            else {
                try { p = RecipeFormats.parse(new String(read(f), UTF8), fn); }
                catch (IOException e) { p = Parsed.fail(Lang.t("custom_unreadable", String.valueOf(e.getMessage()))); }
            }
            if (p.error == null) {
                String other = byName.get(p.recipe.name.toLowerCase(Locale.US));
                if (other != null) p = Parsed.fail(Lang.t("custom_duplicate", p.recipe.name, other));
            }
            if (p.error != null) { skipped.add(fn + ": " + p.error); continue; }
            byName.put(p.recipe.name.toLowerCase(Locale.US), fn);
            ok.add(new Entry(p.recipe, fn));
        }
        sort(ok);
        return new Loaded(ok, skipped);
    }

    /** A to Z by name, ignoring case: the order of the Custom group */
    static void sort(List<Entry> entries) {
        Collections.sort(entries, new Comparator<Entry>() { public int compare(Entry a, Entry b) { return a.recipe.name.compareToIgnoreCase(b.recipe.name); } });
    }

    private static byte[] read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally { in.close(); }
    }

    /**
     * Writes a recipe into {@code dir}, creating the folder on first use; {@code oldFile} is the file it replaces (an
     * edit or a rename), null for a new recipe. The text goes to {@link #TMP} first and only then takes the recipe's
     * name, so a camera switched off mid-write leaves the old file or the new one, never half of one. It is synced where
     * the file system can; the card's cannot, and a sync it refuses is not a failed save.
     */
    static Entry save(File dir, Recipes.Recipe r, String madeOn, String oldFile) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        List<String> taken = new ArrayList<String>();
        String[] names = dir.list();
        if (names != null) for (String n : names) if (oldFile == null || !n.equalsIgnoreCase(oldFile)) taken.add(n);
        String file = fileName(r.name, taken);
        File tmp = new File(dir, TMP), target = new File(dir, file);
        if (tmp.exists()) remove(tmp);                                  // left by a save the camera was switched off in
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(RecipeFormats.write(r, madeOn).getBytes(UTF8));
            out.flush();
            try { out.getFD().sync(); } catch (IOException noSync) {}   // Sony's FUSE layer has no fsync
        } catch (IOException e) {
            out.close(); tmp.delete(); throw e;
        }
        out.close();
        boolean same = oldFile != null && oldFile.equalsIgnoreCase(file);   // an edit, or a rename that only changes case
        if (same) remove(new File(dir, oldFile));                       // on FAT it is the target itself
        if (target.exists()) remove(target);
        if (!tmp.renameTo(target)) { tmp.delete(); throw new IOException("cannot write " + target); }
        if (oldFile != null && !same) new File(dir, oldFile).delete();
        return new Entry(r, file);
    }

    /** removes a recipe's file */
    static void delete(File dir, String file) throws IOException { remove(new File(dir, file)); }

    private static void remove(File f) throws IOException {
        if (!f.delete() && f.exists()) throw new IOException("cannot delete " + f);
    }

    // ------------------------------------------------------------ what the screen says
    /** the files a load skipped, as one toast */
    static String skippedMessage(List<String> skipped) {
        return skipped.size() == 1 ? Lang.t("custom_skipped_one", skipped.get(0)) : Lang.t("custom_skipped_many", skipped.size(), skipped.get(0));
    }

    /** where a recipe's file is, as the screen names it: RECIPES/GOLDENHO.YML */
    static String path(String file) { return DIR + "/" + file; }

    /**
     * The button row under the chips while a recipe is edited: what can be done with the edits. Every recipe gets Copy
     * (the edits as a new custom recipe, under a name, then stored in the camera — the original untouched), Apply (write
     * them to the camera, keep nothing — left out once the camera has them) and Discard (the recipe's own values again,
     * after asking);
     * a custom one also Save (the edits over itself, then stored in the camera). A built-in recipe never changes, so it
     * has no Save.
     */
    static final int EDIT_SAVE = 0, EDIT_COPY = 1, EDIT_APPLY = 2, EDIT_DISCARD = 3;
    static int[] editActions(boolean custom, boolean applied) {
        List<Integer> a = new ArrayList<Integer>();
        if (custom) a.add(EDIT_SAVE);                       // in this order: Save · Apply · Copy · Discard
        if (!applied) a.add(EDIT_APPLY);
        a.add(EDIT_COPY);
        a.add(EDIT_DISCARD);
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i);
        return out;
    }
    static String editLabel(int action) {
        switch (action) {
            case EDIT_SAVE: return Lang.t("button_save");
            case EDIT_COPY: return Lang.t("button_copy");
            case EDIT_APPLY: return Lang.t("button_apply");
            default: return Lang.t("button_discard");
        }
    }

    /**
     * Leaving an edited recipe — the wheel to another one, the brand list, leaving the app — or picking it again with
     * centre: the edits would be lost, so ask. Discard is the recipe's own values again (picked, when it was centre);
     * Cancel, highlighted, keeps the edits on screen.
     */
    static String discardTitle(String recipeName) { return Lang.t("custom_discard_title", recipeName); }
    static String discardBody() { return Lang.t("custom_discard_body"); }
    static String[] discardOptions() { return new String[] { Lang.t("button_discard"), Lang.t("button_cancel") }; }
    static final int DISCARD = 0, DISCARD_DEFAULT = 1;

    /**
     * The name a copy starts with: "Golden Hour 2", then 3 … — what a name may not hold left out ("Polaroid / Instax" →
     * "Polaroid Instax 2"), shortened to leave room within NAME_MAX.
     */
    static String copyName(String name, Collection<String> taken) {
        Set<String> lower = lower(taken);
        StringBuilder b = new StringBuilder();
        for (char c : name.toCharArray()) if (nameChar(c) && !(c == ' ' && (b.length() == 0 || b.charAt(b.length() - 1) == ' '))) b.append(c);
        String clean = b.toString().trim();
        if (clean.isEmpty()) clean = UNTITLED;
        for (int n = 2; ; n++) {
            String suffix = " " + n, base = clean;
            if (base.length() + suffix.length() > NAME_MAX) base = base.substring(0, NAME_MAX - suffix.length()).trim();
            String c = base + suffix;
            if (!lower.contains(c.toLowerCase(Locale.US))) return c;
        }
    }

    /** a MENU hold on a custom recipe in the brand list: what can be done with it. Cancel is highlighted, so a stray centre press does nothing */
    static String optionsBody(String file) { return Lang.t("custom_options_body", path(file)); }
    static String[] options() {
        return new String[] { Lang.t("button_rename"), Lang.t("button_delete"), Lang.t("button_cancel") };
    }
    static final int OPT_RENAME = 0, OPT_DELETE = 1, OPT_DEFAULT = 2;

    /** the second question before a delete, Cancel highlighted */
    static String deleteTitle(String name) { return Lang.t("custom_delete_title", name); }
    static String deleteBody(String file) { return Lang.t("custom_delete_body", path(file)); }
    static String[] deleteOptions() { return new String[] { Lang.t("button_delete"), Lang.t("button_cancel") }; }
    static final int DELETE_DEFAULT = 1;
}
