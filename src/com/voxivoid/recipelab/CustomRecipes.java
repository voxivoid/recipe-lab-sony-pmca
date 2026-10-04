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
 * <h3>The file</h3>
 * {@code key = value} lines, {@code #} starts a comment, English whatever the menu shows — people diff, edit and trade
 * these. {@link #encode} writes every key with its allowed range beside it. The values are the ones the chips show:
 * style and effect by their runtime names, exposure in thirds as "+0.7", white balance as "auto", "keep" or "5600K".
 *
 * <h3>Versions</h3>
 * {@code format} says which reader understands a file; {@link #FORMAT} is the newest this build writes. A file without
 * it is format 1. A key a reader does not know is ignored, so adding one never needs a new format; changing what an
 * existing key means does, and then {@link #parse} gains a case that reads the old format — a file once written keeps
 * loading. A file from a newer format is skipped, never half-read: it ends up in the camera's settings store.
 *
 * <h3>Untrusted input</h3>
 * A file may come from anyone. Every value must be one the store accepts ({@link #problem}); a file with one that is not
 * is skipped with the reason, not clamped. Two files never share a name: the second is skipped, so nothing a card
 * brings in overwrites a recipe.
 *
 * <h3>The card's file system</h3>
 * Apps reach the card through Sony's FUSE layer (libInfraFuFsys, mounted at /mnt/sdcard), which takes DOS 8.3 names
 * only: a longer one is ENAMETOOLONG, letters are upper case, and there is no fsync. So the folder is {@link #DIR}, the
 * app writes {@code GOLDENHO.TXT}-style names ({@link #fileName}), and the recipe's real name lives inside the file.
 *
 * MainActivity finds the card and owns the list ({@link Library}); this class decides, reads and writes. No android.*
 * import may appear here (tools/test.sh).
 */
final class CustomRecipes {
    private CustomRecipes() {}

    /** the folder at the root of the memory card: eight characters at most, as the card's file system takes no longer name */
    static final String DIR = "RECIPES";
    static final String EXT = ".TXT";
    /** the file a save writes before it takes the recipe's name; 8.3 like every name on the card, and never read as a recipe */
    static final String TMP = "SAVING.TMP";
    /** the newest file format this build reads and the one it writes */
    static final int FORMAT = 1;
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

    private static boolean in(int v, int lo, int hi) { return v >= lo && v <= hi; }
    private static boolean kelvinOk(int k) { return k % 100 == 0 && in(k / 100, ROW_MIN[R_KELVIN], ROW_MAX[R_KELVIN]); }
    private static String bad(String key, Object value) { return Lang.t("custom_bad_value", key, String.valueOf(value)); }

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
     * the name in capitals plus {@link #EXT} — "Golden Hour" → GOLDENHO.TXT. When another file has it, the end of the
     * eight gives way to a number: GOLDENH2.TXT, GOLDENH3.TXT … {@code takenFiles} are compared ignoring case.
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

    // ------------------------------------------------------------ the file
    /** the file text of a recipe; {@code madeOn} is the camera model it was saved on, null when unknown */
    static String encode(Recipes.Recipe r, String madeOn) {
        StringBuilder s = new StringBuilder();
        s.append("# Recipe Lab custom recipe. Keep it in the ").append(DIR).append(" folder of a memory card, under a name of\n");
        s.append("# up to eight letters or digits and .TXT: the camera reads no longer file names. Its own name is below.\n");
        s.append("# Edit with care: a value outside the range beside it makes the app skip this file.\n");
        line(s, "format", String.valueOf(FORMAT), null);
        line(s, "name", r.name, null);
        if (madeOn != null && !madeOn.trim().isEmpty()) line(s, "made-on", madeOn.trim().replace('#', ' '), "the camera it was saved on");
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
        line(s, "exposure", r.ev == 0 ? "0" : evText(r.ev), "-5.0 .. +5.0 in thirds: .0 .3 .7");
        line(s, "dro", r.dro == Recipes.DRO_AUTO ? "auto" : r.dro == 0 ? "off" : String.valueOf(r.dro), "off, auto, 1 .. 5");
        return s.toString();
    }

    private static void line(StringBuilder s, String key, String value, String comment) {
        int at = s.length();
        s.append(key).append(" = ").append(value);
        if (comment != null) { while (s.length() - at < 28) s.append(' '); s.append(" # ").append(comment); }
        s.append('\n');
    }

    private static String signed(int v) { return v > 0 ? "+" + v : String.valueOf(v); }
    /** exposure in thirds as the file spells it, "+0.7" — Recipes.evLabel, which the chip shows too */
    private static String evText(int ev) { return Recipes.evLabel(ev); }

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

    /**
     * Reads one file's text. {@code fileName} names a recipe whose file has no name line. The format line picks the
     * reader; there is one so far.
     */
    static Parsed parse(String text, String fileName) {
        Map<String, String> kv = new HashMap<String, String>();
        if (text.startsWith("﻿")) text = text.substring(1);       // the byte-order mark some Windows editors add
        for (String raw : text.split("\r\n|\r|\n")) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;                                    // not a setting: a later format may know what it is
            String key = line.substring(0, eq).trim().toLowerCase(Locale.US), value = line.substring(eq + 1).trim();
            if (kv.put(key, value) != null) return fail(Lang.t("custom_twice", key));
        }
        String f = kv.get("format");
        int format;
        if (f == null) format = 1;
        else if (f.matches("\\d{1,4}")) format = Integer.parseInt(f);
        else return fail(bad("format", f));
        if (format > FORMAT) return fail(Lang.t("custom_newer", format));
        switch (format) {
            case 1: return v1(kv, fileName);
            default: return fail(bad("format", f));
        }
    }

    /** format 1: the keys {@link #encode} writes; a missing one takes the factory look's value */
    private static Parsed v1(Map<String, String> kv, String fileName) {
        String name = kv.get("name");
        if (name == null || name.isEmpty()) name = stem(fileName);
        String np = nameProblem(name, Collections.<String>emptyList(), null);
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
        String p = problem(r);                                        // the reader above and the store's ranges must agree
        return p != null ? fail(p) : new Parsed(r, null);
    }

    private static Parsed fail(String why) { return new Parsed(null, why); }

    private static String stem(String fileName) {
        if (fileName == null) return "";
        return fileName.toLowerCase(Locale.US).endsWith(EXT.toLowerCase(Locale.US)) ? fileName.substring(0, fileName.length() - EXT.length()) : fileName;
    }

    /** the position of {@code v} in a table of runtime names, ignoring case; -1 for a name it does not hold */
    private static int indexOf(String[] names, String v) {
        for (int i = 0; i < names.length; i++) if (names[i] != null && names[i].equalsIgnoreCase(v)) return i;
        return -1;
    }

    private static Integer integer(String v) { return v.matches("[+-]?\\d{1,3}") ? Integer.parseInt(v.startsWith("+") ? v.substring(1) : v) : null; }

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
            if (f.length() > MAX_BYTES) p = fail(Lang.t("custom_too_large"));
            else {
                try { p = parse(new String(read(f), UTF8), fn); }
                catch (IOException e) { p = fail(Lang.t("custom_unreadable", String.valueOf(e.getMessage()))); }
            }
            if (p.error == null) {
                String other = byName.get(p.recipe.name.toLowerCase(Locale.US));
                if (other != null) p = fail(Lang.t("custom_duplicate", p.recipe.name, other));
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
            out.write(encode(r, madeOn).getBytes(UTF8));
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

    /** where a recipe's file is, as the screen names it: RECIPES/GOLDENHO.TXT */
    static String path(String file) { return DIR + "/" + file; }

    /**
     * The button row under the chips while a recipe is edited: what can be done with the edits. Every recipe gets Save
     * (a built-in one as a new custom recipe, under a name; a custom one over itself), Apply (write them to the camera,
     * keep nothing — left out once the camera has them) and Restore (the recipe's own values again); a custom one also
     * Copy (the edits as a new custom recipe, under a name).
     */
    static final int EDIT_SAVE = 0, EDIT_COPY = 1, EDIT_APPLY = 2, EDIT_RESTORE = 3;
    static int[] editActions(boolean custom, boolean applied) {
        List<Integer> a = new ArrayList<Integer>();
        a.add(EDIT_SAVE);
        if (custom) a.add(EDIT_COPY);
        if (!applied) a.add(EDIT_APPLY);
        a.add(EDIT_RESTORE);
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i);
        return out;
    }
    static String editLabel(int action) {
        switch (action) {
            case EDIT_SAVE: return Lang.t("button_save");
            case EDIT_COPY: return Lang.t("button_copy");
            case EDIT_APPLY: return Lang.t("button_apply");
            default: return Lang.t("button_restore");
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

    /** the name a copy starts with: "Golden Hour 2", then 3 …, the name shortened to leave room within NAME_MAX */
    static String copyName(String name, Collection<String> taken) {
        Set<String> lower = lower(taken);
        for (int n = 2; ; n++) {
            String suffix = " " + n, base = name.trim();
            if (base.length() + suffix.length() > NAME_MAX) base = base.substring(0, NAME_MAX - suffix.length()).trim();
            String c = base + suffix;
            if (!lower.contains(c.toLowerCase(Locale.US))) return c;
        }
    }

    /** a hold on a custom recipe: what can be done with it. Cancel is highlighted, so a stray centre press does nothing */
    static String optionsBody(String file) { return Lang.t("custom_options_body", path(file)); }
    static String[] options(boolean favourite) {
        return new String[] { Lang.t(favourite ? "button_unfavourite" : "button_favourite"), Lang.t("button_rename"), Lang.t("button_delete"), Lang.t("button_cancel") };
    }
    static final int OPT_FAVOURITE = 0, OPT_RENAME = 1, OPT_DELETE = 2, OPT_DEFAULT = 3;

    /** the second question before a delete, Cancel highlighted */
    static String deleteTitle(String name) { return Lang.t("custom_delete_title", name); }
    static String deleteBody(String file) { return Lang.t("custom_delete_body", path(file)); }
    static String[] deleteOptions() { return new String[] { Lang.t("button_delete"), Lang.t("button_cancel") }; }
    static final int DELETE_DEFAULT = 1;
}
