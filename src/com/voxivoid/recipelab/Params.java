package com.voxivoid.recipelab;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parameter rows and everything about them that needs no camera: the settings-store slot of each row, how
 * the store encodes a value, the live-preview parameters a staged set translates to, chip navigation, and the
 * HUD strings. Every method is a pure function of its arguments — and, for the text it returns, of the display
 * language ({@link Lang}).
 *
 * MainActivity owns the state (the {@code cur} / {@code edit} arrays, indexed by row) and the camera; this class
 * decides. That split is what lets tools/test.sh compile it — with Recipes — against a plain JDK and run it under
 * JUnit. No android.* import may appear here.
 *
 * Row values are the app's own units (see Recipes): DRO 0 off / 1..5 / 6 auto, G-M green positive, quality
 * 0 RAW / 1 RAW+JPG / 2 JPEG Fine / 3 JPEG Std. The store's units differ, hence the codecs below.
 */
final class Params {
    private Params() {}

    // ---- settings-store slots (found with the C1 snapshot/diff tool; see docs/DEVELOPMENT.md)
    static final int ID_STYLE = 0x01070175, ID_CON = 0x01070178, ID_SAT = 0x01070187, ID_SHARP = 0x0107018a, ID_PP_NO = 0x0107031c,
            ID_WB_MODE = 0x01070019, ID_WB_TEMP = 0x01070018, ID_WB_AB = 0x01070017, ID_WB_GM = 0x01070016,
            ID_WB_AB_AWB = 0x0107067f, ID_WB_GM_AWB = 0x0107067e,   // per-mode copies the camera actually applies (AWB pair)
            ID_WB_AB_K = 0x01070683, ID_WB_GM_K = 0x01070682,       // same for colour-temperature mode; G-M stored magenta positive (menu G1 = 0xff)
            ID_PE = 0x010706f1, ID_EV = 0x010700b8, ID_EV2 = 0x01070c7f /* companion copy the camera applies */,
            ID_DRO = 0x01070104 /* off 0, auto 1, Lv1..5 = 2..6 (verified) */, ID_DRO_LVL = 0x01070775 /* 1 for off/auto, Lv n = n+1 */,
            ID_QFMT = 0x01070013, ID_QJPG = 0x01070014,            // still file format / jpeg quality (verified by menu diff)
            ID_QFMT2 = 0x01070aa9, ID_QJPG2 = 0x01070aaa;          // the camera keeps mirror copies; written too

    // ---- quality: 0 RAW, 1 RAW+JPEG, 2 JPEG Fine, 3 JPEG Std — runtime keys storage-fmt / jpeg-quality
    static final int Q_RAW = 0, Q_RAWJPG = 1, Q_FINE = 2, Q_STD = 3;
    static final String[] Q_LABEL = { "RAW", "RAW+JPG", "JPG Fine", "JPG Std" };
    static final String[] Q_FMT = { "raw", "rawjpeg", "jpeg", "jpeg" };
    static final String[] Q_JPG = { "50", "50", "50", "25" };
    static final int[] Q_FMT_CODE = { 1, 2, 0, 0 }, Q_JPG_CODE = { 1, 1, 1, 0 };   // verified: format raw=1 rawjpeg=2 jpeg=0 · jpeg std=0 fine=1

    // ---- rows
    static final int R_RECIPE = 0, R_STYLE = 1, R_SAT = 2, R_CON = 3, R_SHARP = 4, R_PP = 5, R_PE = 6, R_SUB = 7, R_WBMODE = 8, R_KELVIN = 9, R_AB = 10, R_GM = 11, R_EV = 12, R_DRO = 13, R_QUAL = 14;
    /** ROW_ID markers for rows without a fixed slot */
    static final int NO_SLOT = 0, SUB_SLOT = -1 /* depends on the staged effect */, QUALITY_SLOTS = -2 /* two slots, each mirrored */;
    static final String[] ROW_NAME = { "RECIPE", "STYLE", "SAT", "CON", "SHARP", "PP", "EFFECT", "SUB", "WB", "KELVIN", "A-B", "G-M", "EV", "DRO", "QUALITY" };
    static final int[] ROW_ID = { NO_SLOT, ID_STYLE, ID_SAT, ID_CON, ID_SHARP, ID_PP_NO, ID_PE, SUB_SLOT, ID_WB_MODE, ID_WB_TEMP, ID_WB_AB, ID_WB_GM, ID_EV, ID_DRO, QUALITY_SLOTS };
    static final int[] ROW_MIN = { 0, 1, -3, -3, -3, 0, 0, 0, 0, 25, -7, -7, -15, 0, 0 };
    static final int[] ROW_MAX = { 0, 14, 3, 3, 3, 1, 13, 4, 20, 99, 7, 7, 15, 6, 3 };
    static final int N = ROW_ID.length;

    /** a row's name as its chip shows it, in the display language; ROW_NAME is the English */
    static String rowName(int row) { return Lang.label("row_" + Lang.slug(ROW_NAME[row]), ROW_NAME[row]); }
    /** a quality as the chip and the messages show it, in the display language; Q_LABEL is the English */
    static String qualityLabel(int q) { return q >= 0 && q < Q_LABEL.length ? Lang.label("quality_" + Lang.slug(Q_LABEL[q]), Q_LABEL[q]) : "?" + q; }
    /** chip display / navigation order (quality first) */
    static final int[] ORDER = { R_QUAL, R_STYLE, R_SAT, R_CON, R_SHARP, R_PE, R_SUB, R_WBMODE, R_KELVIN, R_AB, R_GM, R_EV, R_DRO };   // R_PP has no chip
    /** the overlay MainActivity is in: the full panel, the pill, nothing, the browser, or the full panel without its key legend */
    static final int OV_FULL = 0, OV_PILL = 1, OV_HIDDEN = 2, OV_BROWSER = 3, OV_QUIET = 4;
    /** whether the overlay shows the full panel, with its chips — with or without the key legend under it */
    static boolean panelUp(int overlay) { return overlay == OV_FULL || overlay == OV_QUIET; }
    /** the browser's two columns */
    static final int COL_GROUPS = 0, COL_RECIPES = 1;
    /** what a short press of the centre button does, by where the user is */
    static final int ENTER_PICK = 0, ENTER_FOCUS = 1, ENTER_BROWSER_COLUMN = 2, ENTER_BROWSER_PICK = 3;
    /** WB modes as stored: 1 auto, 14 colour temperature */
    static final int WB_AUTO = 1, WB_KELVIN = 14;

    // ------------------------------------------------------------ slots
    /** settings slot for a row; SUB depends on which effect is staged (0 when it has none) */
    static int slot(int row, int pe) { return row == R_SUB ? Recipes.subId(pe) : ROW_ID[row]; }

    /** slots whose byte is an unsigned index; every other slot holds a signed offset */
    static boolean unsignedSlot(int id) { return id == ID_WB_TEMP || id == ID_WB_MODE || id == ID_STYLE || id == ID_PE; }

    /** the fine-tune copy the camera applies in the given WB mode */
    static int abSlot(int wbMode) { return wbMode == WB_KELVIN ? ID_WB_AB_K : ID_WB_AB_AWB; }
    static int gmSlot(int wbMode) { return wbMode == WB_KELVIN ? ID_WB_GM_K : ID_WB_GM_AWB; }

    // ------------------------------------------------------------ store <-> row value
    /** DRO main byte -> row value: 0 off, 1 auto, 2..6 = Lv1..5 (anything above clamps to Lv5) */
    static int droFromStore(int b) { return b == 0 ? 0 : b == 1 ? Recipes.DRO_AUTO : Math.min(5, b - 1); }
    static int droMainToStore(int dro) { return dro == 0 ? 0 : dro == Recipes.DRO_AUTO ? 1 : dro + 1; }
    /** the level byte: 1 for off and auto, Lv n = n + 1 */
    static int droLevelToStore(int dro) { return (dro >= 1 && dro <= 5) ? dro + 1 : 1; }
    /**
     * Picture Profile number -> PP row: 1 while any profile is on. The row has no chip; a staged recipe always
     * sets it to 0, so picking any recipe switches off the PP3 that older matrix recipes left behind (issue #38).
     */
    static int ppFromStore(int ppNo) { return ppNo == 0 ? 0 : 1; }
    /** the store counts magenta positive, the app counts green positive */
    static int gmFromStore(int b) { return -b; }
    static int gmToStore(int gm) { return -gm; }

    /**
     * Decodes one stored byte into its row value. {@code b} is the byte as NativeBackup.readByte returns it —
     * sign-extended or 0..255, both are accepted. Not for the SUB or quality rows, which have their own readers.
     */
    static int fromStore(int id, int b) {
        if (id == ID_DRO) return droFromStore(b & 0xff);
        int v = unsignedSlot(id) ? b & 0xff : (byte) b;
        if (id == ID_PP_NO) return ppFromStore(v);
        if (id == ID_WB_GM) return gmFromStore(v);
        return v;
    }

    /** quality from the two stored bytes, or -1 when the pair is not a combination the camera writes */
    static int qualityFromStore(int fmt, int jpg) {
        if (fmt == 1) return Q_RAW;
        if (fmt == 2) return Q_RAWJPG;
        if (fmt == 0) return jpg == 0 ? Q_STD : Q_FINE;
        return -1;
    }
    /** quality from the runtime parameters, used when the stored pair is unknown */
    static int qualityFromRuntime(String storageFmt, String jpegQuality) {
        if ("raw".equals(storageFmt)) return Q_RAW;
        if ("rawjpeg".equals(storageFmt)) return Q_RAWJPG;
        return "25".equals(jpegQuality) ? Q_STD : Q_FINE;
    }
    /** a recipe's quality given the Factory base: effects need JPEG, so RAW bases become JPEG Fine for them */
    static int recipeQuality(Recipes.Recipe r, int base) { return r.isEffect() ? (base >= Q_FINE ? base : Q_FINE) : base; }
    /** a quality the user picked on this recipe becomes the Factory base — except a JPEG forced by an effect recipe */
    static boolean redefinesBaseQuality(Recipes.Recipe r, int quality) { return !r.isEffect() || quality >= Q_FINE; }

    // ------------------------------------------------------------ dirty rows and the writes that clear them
    /**
     * Whether a row differs from the store. {@code storedSub} is the byte in the staged effect's SUB slot; it is
     * only consulted for R_SUB, so callers may pass anything for other rows.
     */
    static boolean rowDirty(int row, int[] cur, int[] edit, int storedSub) {
        if (row == R_QUAL) return edit[row] != cur[row];
        if ((row == R_AB || row == R_GM) && edit[R_WBMODE] != cur[R_WBMODE]) return true;   // new WB mode → its own fine-tune pair must be written
        if (row == R_SUB) return Recipes.subId(edit[R_PE]) != 0 && edit[row] != storedSub;
        return ROW_ID[row] != NO_SLOT && edit[row] != cur[row];
    }

    /** number of dirty rows — what the "Stored n values" toast counts */
    static int dirtyRows(int[] cur, int[] edit, int storedSub) {
        int n = 0;
        for (int i = 1; i < N; i++) if (rowDirty(i, cur, edit, storedSub)) n++;
        return n;
    }

    /** one byte to write to the store */
    static final class Write {
        final int id, value;
        Write(int id, int value) { this.id = id; this.value = value; }
        @Override public boolean equals(Object o) { return o instanceof Write && ((Write) o).id == id && ((Write) o).value == value; }
        @Override public int hashCode() { return id * 31 + value; }
        @Override public String toString() { return String.format("%08x=%d", id, value); }
    }

    /** the byte writes, in order, that bring the store from {@code cur} to {@code edit} */
    static List<Write> writes(int[] cur, int[] edit, int storedSub) {
        List<Write> w = new ArrayList<Write>();
        for (int i = 1; i < N; i++) {
            if (!rowDirty(i, cur, edit, storedSub)) continue;
            int v = edit[i];
            if (i == R_QUAL) {
                w.add(new Write(ID_QFMT, Q_FMT_CODE[v])); w.add(new Write(ID_QFMT2, Q_FMT_CODE[v]));
                w.add(new Write(ID_QJPG, Q_JPG_CODE[v])); w.add(new Write(ID_QJPG2, Q_JPG_CODE[v]));
                continue;
            }
            int id = slot(i, edit[R_PE]);
            if (id == ID_EV) { w.add(new Write(ID_EV, v)); w.add(new Write(ID_EV2, v)); continue; }
            if (id == ID_WB_AB) { w.add(new Write(ID_WB_AB, v)); w.add(new Write(abSlot(edit[R_WBMODE]), v)); continue; }
            if (id == ID_WB_GM) { w.add(new Write(ID_WB_GM, gmToStore(v))); w.add(new Write(gmSlot(edit[R_WBMODE]), gmToStore(v))); continue; }
            if (id == ID_DRO) { w.add(new Write(ID_DRO, droMainToStore(v))); w.add(new Write(ID_DRO_LVL, droLevelToStore(v))); continue; }
            w.add(new Write(id, v));
        }
        return w;
    }

    // ------------------------------------------------------------ slots the camera may refuse
    /**
     * The attribute bit that marks a settings slot read-only. Backup protection decides whether a slot carrying it
     * can be written: with protection on the camera answers {@code -BACKUP_ERROR_READ_ONLY}, with it off (the
     * Protection tweak of OpenMemories-Tweak) the write goes through. A slot without the bit is writable either
     * way, which is why the app tests the slots a recipe actually writes rather than probing protection itself —
     * that probe answers for one unrelated read-only slot and says nothing about a recipe (issue #19).
     */
    static final int ATTR_READ_ONLY = 1;

    /** whether a slot's attribute word says the camera may refuse a write to it */
    static boolean slotLocked(int attr) { return (attr & ATTR_READ_ONLY) != 0; }

    /** every slot the app can write: the rows, their mirrors and per-mode copies, and each effect's sub-slot */
    static List<Integer> allSlots() {
        Set<Integer> ids = new LinkedHashSet<Integer>();
        for (int i = 1; i < N; i++) if (ROW_ID[i] > 0) ids.add(ROW_ID[i]);
        ids.add(ID_WB_AB_AWB); ids.add(ID_WB_GM_AWB); ids.add(ID_WB_AB_K); ids.add(ID_WB_GM_K);
        ids.add(ID_EV2); ids.add(ID_DRO_LVL);
        ids.add(ID_QFMT); ids.add(ID_QJPG); ids.add(ID_QFMT2); ids.add(ID_QJPG2);
        for (int pe = 0; pe < Recipes.PE_KEYS.length; pe++) { int sid = Recipes.subId(pe); if (sid != 0) ids.add(sid); }
        return new ArrayList<Integer>(ids);
    }

    /** the row a slot belongs to, as a message names it; the hex id for a slot no row owns */
    static String slotName(int id) {
        switch (id) {
            case ID_EV: case ID_EV2: return rowName(R_EV);
            case ID_QFMT: case ID_QJPG: case ID_QFMT2: case ID_QJPG2: return rowName(R_QUAL);
            case ID_WB_AB: case ID_WB_AB_AWB: case ID_WB_AB_K: return rowName(R_AB);
            case ID_WB_GM: case ID_WB_GM_AWB: case ID_WB_GM_K: return rowName(R_GM);
            case ID_DRO: case ID_DRO_LVL: return rowName(R_DRO);
        }
        for (int i = 1; i < N; i++) if (ROW_ID[i] > 0 && ROW_ID[i] == id) return rowName(i);
        for (int pe = 0; pe < Recipes.PE_KEYS.length; pe++) { int sid = Recipes.subId(pe); if (sid != 0 && sid == id) return rowName(R_SUB) + " " + Recipes.peLabel(pe); }   // an effect with no sub-slot answers 0, which is no slot at all
        return String.format("%08x", id);
    }

    /** the row names of a set of slots, each named once, in the order the slots come */
    private static String slotNames(List<Integer> ids) {
        Set<String> names = new LinkedHashSet<String>();
        for (int id : ids) names.add(slotName(id));
        StringBuilder s = new StringBuilder();
        for (String n : names) { if (s.length() > 0) s.append(", "); s.append(n); }
        return s.toString();
    }

    /**
     * The slots of a pending write the camera holds read-only, given one attribute word per write ({@code -1}
     * where the camera would not answer). A slot whose attribute cannot be read counts as writable: the write
     * path reports a refusal properly, so a failed probe must not stop a recipe that would have gone in.
     */
    static List<Integer> lockedFrom(List<Write> ws, int[] attrs) {
        List<Integer> locked = new ArrayList<Integer>();
        for (int i = 0; i < ws.size(); i++) if (attrs[i] >= 0 && slotLocked(attrs[i])) locked.add(ws.get(i).id);
        return locked;
    }

    /**
     * The recipe was not written because the camera holds some of its slots read-only: which settings they are,
     * and the one thing that changes it. Checked before the first write, so a locked slot cannot leave half a
     * recipe in the store.
     */
    static String lockedMessage(List<Integer> ids) {
        return Lang.t(ids.size() == 1 ? "locked_one" : "locked_many", slotNames(ids)) + " " + Lang.t("unlock_hint");
    }

    /** the camera refused a write: which setting stopped it, and how much of the recipe went in before it did */
    static String writeFailedMessage(int id, String error, int written) {
        return Lang.t(written == 0 ? "write_failed_none" : written == 1 ? "write_failed_one" : "write_failed_many",
                slotName(id), String.format("%08x", id), error, written);
    }

    /**
     * The verdict of the read-only check (developer menu): {@code attrs} holds one attribute word per slot of
     * {@code ids}, or -1 where the camera would not answer. A body that reports nothing read-only here is a body
     * on which no recipe can be refused, whatever backup protection says.
     */
    static String lockReport(List<Integer> ids, int[] attrs) {
        List<Integer> locked = new ArrayList<Integer>();
        int unreadable = 0;
        for (int i = 0; i < ids.size(); i++) {
            if (attrs[i] < 0) unreadable++;
            else if (slotLocked(attrs[i])) locked.add(ids.get(i));
        }
        return (locked.isEmpty() ? Lang.t("lock_report_none", ids.size()) : Lang.t("lock_report_some", ids.size(), locked.size(), slotNames(locked)))
                + (unreadable == 0 ? "" : Lang.t("lock_report_unreadable", unreadable));
    }

    /** the same check, one line per slot, as it is written to the file a compatibility report can quote — in English, always */
    static String lockLines(List<Integer> ids, int[] attrs) {
        int was = Lang.use(Lang.EN);
        try {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                int id = ids.get(i);
                s.append(String.format("%08x", id)).append(' ').append(slotName(id)).append(' ')
                 .append(attrs[i] < 0 ? "attr=?" : "attr=" + attrs[i] + (slotLocked(attrs[i]) ? " READ_ONLY" : "")).append('\n');
            }
            return s.toString();
        } finally { Lang.use(was); }
    }

    /** stages a recipe over the current edit values (WB is left alone when the recipe says so); quality is the caller's */
    static void stage(Recipes.Recipe r, int[] edit) {
        edit[R_STYLE] = r.style; edit[R_SAT] = r.sat; edit[R_CON] = r.con; edit[R_SHARP] = r.sharp; edit[R_PP] = 0;
        if (r.wbMode != 0) { edit[R_WBMODE] = r.wbMode; if (r.wbMode == WB_KELVIN) edit[R_KELVIN] = r.kelvin / 100; }
        edit[R_AB] = r.ab; edit[R_GM] = r.gm;
        edit[R_PE] = r.pe; edit[R_EV] = r.ev; edit[R_DRO] = r.dro; edit[R_SUB] = r.sub;
    }

    /**
     * Whether the staged rows hold a look the recipe does not: what makes an edit worth keeping as a custom recipe.
     * Quality is not part of a recipe (it follows the Factory base) and neither is PP; the kelvin row only counts in
     * kelvin mode, and the sub-setting only for an effect that has one — a chip the recipe would not show.
     */
    static boolean differsFromRecipe(Recipes.Recipe r, int[] edit) {
        int[] want = edit.clone();
        stage(r, want);
        for (int i = 1; i < N; i++) {
            if (i == R_QUAL || i == R_PP) continue;
            if (i == R_KELVIN && want[R_WBMODE] != WB_KELVIN) continue;
            if (i == R_SUB && Recipes.subValues(want[R_PE]) == null) continue;
            if (want[i] != edit[i]) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ live preview
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** the Camera.Parameters the staged values translate to, in the order they are set */
    static Map<String, String> preview(int[] edit) {
        Map<String, String> p = new LinkedHashMap<String, String>();
        int st = edit[R_STYLE];
        p.put("color-mode", Recipes.styleKnown(st) ? Recipes.STYLE_NAMES[st] : "standard");
        p.put("saturation", String.valueOf(clamp(edit[R_SAT], -3, 3)));   // the preview takes more, the store does not
        p.put("contrast", String.valueOf(clamp(edit[R_CON], -3, 3)));
        p.put("sharpness", String.valueOf(clamp(edit[R_SHARP], -3, 3)));
        p.put("rgb-matrix-mode", "false");
        if (edit[R_WBMODE] == WB_KELVIN) { p.put("whitebalance", "color-temp"); p.put("color-temperture-white-balance", String.valueOf(edit[R_KELVIN] * 100)); }
        else if (edit[R_WBMODE] == WB_AUTO) p.put("whitebalance", "auto");
        p.put("light-balance-for-white-balance", String.valueOf(edit[R_AB]));
        p.put("color-compensation-for-white-balance", String.valueOf(-edit[R_GM]));   // camera counts magenta positive; recipes count green positive
        p.put("storage-fmt", Q_FMT[edit[R_QUAL]]); p.put("jpeg-quality", Q_JPG[edit[R_QUAL]]);
        p.put("picture-effect", Recipes.PE_KEYS[edit[R_PE]]);
        String sk = Recipes.subKey(edit[R_PE]); String[] sv = Recipes.subValues(edit[R_PE]);
        if (sk != null && sv != null && edit[R_SUB] >= 0 && edit[R_SUB] < sv.length) p.put(sk, sv[edit[R_SUB]]);
        p.put("exposure-compensation", String.valueOf(edit[R_EV]));
        int dro = edit[R_DRO];
        if (dro == Recipes.DRO_AUTO) p.put("dro-mode", "auto");
        else if (dro == 0) p.put("dro-mode", "off");
        else { p.put("dro-mode", "on"); p.put("dro-level", String.valueOf(dro)); }
        return p;
    }

    // ------------------------------------------------------------ chips
    /** which chips make sense for what is staged */
    static boolean rowVisible(int row, int[] edit) {
        boolean pe = edit[R_PE] != 0;
        switch (row) {
            case R_STYLE: case R_SAT: case R_CON: case R_SHARP: return !pe;
            case R_PP: return false;
            case R_SUB: return pe && Recipes.subId(edit[R_PE]) != 0;
            case R_KELVIN: return edit[R_WBMODE] == WB_KELVIN;
            default: return true;
        }
    }

    /** enumerated rows (names, not numbers) scroll endlessly */
    static boolean isChoice(int row) { return row == R_STYLE || row == R_PE || row == R_SUB || row == R_QUAL || row == R_DRO || row == R_WBMODE; }

    /**
     * One step on the focused chip: choices wrap, numbers clamp, WB toggles auto/kelvin, SUB cycles the staged
     * effect's values. Changing the effect resets SUB and re-derives quality from {@code recipeQuality} (the current
     * recipe's quality from the Factory base), forcing JPEG when an effect is on. Returns true when the user changed
     * quality directly, which redefines the Factory base.
     */
    static boolean step(int[] edit, int row, int dir, int recipeQuality) {
        if (row == R_WBMODE) { edit[R_WBMODE] = edit[R_WBMODE] == WB_KELVIN ? WB_AUTO : WB_KELVIN; return false; }
        if (row == R_SUB) { String[] sv = Recipes.subValues(edit[R_PE]); int n = sv == null ? 1 : sv.length; edit[R_SUB] = (edit[R_SUB] + n + dir) % n; return false; }
        if (row == R_STYLE) { int n = ROW_MAX[row] - ROW_MIN[row] + 1; do { edit[row] = ROW_MIN[row] + ((edit[row] - ROW_MIN[row] + n + dir) % n); } while (!Recipes.styleKnown(edit[row])); return false; }   // unidentified enum values are skipped
        if (isChoice(row)) { int n = ROW_MAX[row] - ROW_MIN[row] + 1; edit[row] = ROW_MIN[row] + ((edit[row] - ROW_MIN[row] + n + dir) % n); }   // choices wrap around
        else edit[row] = clamp(edit[row] + dir, ROW_MIN[row], ROW_MAX[row]);                                                                  // numbers clamp
        if (row == R_PE) { edit[R_SUB] = 0; edit[R_QUAL] = recipeQuality; if (edit[R_PE] != 0 && edit[R_QUAL] <= Q_RAWJPG) edit[R_QUAL] = Q_FINE; }
        return row == R_QUAL;
    }

    /** LEFT/RIGHT inside the chip strip: the next / previous visible chip in ORDER, wrapping */
    static int nextChip(int row, int dir, int[] edit) {
        int pos = 0;
        for (int k = 0; k < ORDER.length; k++) if (ORDER[k] == row) pos = k;
        for (int k = 0; k < ORDER.length; k++) {
            pos = (pos + ORDER.length + dir) % ORDER.length;
            if (rowVisible(ORDER[pos], edit)) break;
        }
        return ORDER[pos];
    }

    /** the panel's lines, top to bottom: the recipe name, the chips, and the edit buttons while the recipe is edited */
    static final int LINE_RECIPE = 0, LINE_CHIPS = 1, LINE_ACTIONS = 2;

    /** UP / DOWN between the lines, wrapping: down goes recipe → chips → buttons → recipe; without buttons, recipe ↔ chips */
    static int nextLine(int line, int dir, boolean actions) {
        int n = actions ? 3 : 2, at = Math.min(line, n - 1);
        return (at + n + (dir > 0 ? 1 : -1)) % n;
    }

    /** the chip to land on when leaving the recipe line: the last one used if still visible, else the first visible */
    static int enterChips(int lastChip, int[] edit) {
        if (lastChip != R_RECIPE && rowVisible(lastChip, edit)) return lastChip;
        for (int i : ORDER) if (rowVisible(i, edit)) return i;
        return R_RECIPE;
    }

    // ------------------------------------------------------------ the centre button
    /**
     * Whether the keys act on the recipe line rather than on the chip strip. The chips only take the keys with the
     * full panel up and the highlight off the recipe line — under the pill, or with the overlay hidden, there are no
     * chips to act on.
     */
    static boolean onRecipeLine(int overlay, int row) { return row == R_RECIPE || !panelUp(overlay); }

    /**
     * Whether a hold on the centre button marks a favourite where the user is. It does wherever a recipe is what the
     * screen is about; it does not on the chip strip, or on the browser's group column, where the highlight is a group.
     */
    static boolean holdMarksFavourite(int overlay, int row, int browserCol) {
        return overlay == OV_BROWSER ? browserCol == COL_RECIPES : onRecipeLine(overlay, row);
    }

    /**
     * Whether a hold on MENU opens the app menu where the user is (issue #18). It does on the live screen — full panel,
     * pill or hidden — but not with a chip focused, where MENU unfocuses, nor in the browser, where MENU closes it; in
     * both of those MENU acts on the press, so there is no release left for a hold to wait on.
     */
    static boolean menuHoldArms(int overlay, boolean focus) { return overlay != OV_BROWSER && !focus; }

    /** what the centre button does when it is released before the hold fires */
    static int enterAction(int overlay, int row, int browserCol) {
        if (overlay == OV_BROWSER) return browserCol == COL_GROUPS ? ENTER_BROWSER_COLUMN : ENTER_BROWSER_PICK;
        return onRecipeLine(overlay, row) ? ENTER_PICK : ENTER_FOCUS;
    }

    // ------------------------------------------------------------ HUD strings
    /** a row value as its chip shows it */
    static String fmt(int row, int v, int[] edit) {
        switch (row) {
            case R_STYLE: return Recipes.styleLabel(v);
            case R_PP: return Lang.t(v == 0 ? "value_off" : "value_on");
            case R_WBMODE: return v == WB_AUTO ? Lang.t("value_auto") : v == WB_KELVIN ? Lang.t("value_kelvin") : String.valueOf(v);
            case R_KELVIN: return edit[R_WBMODE] == WB_KELVIN ? (v * 100) + "K" : "-";
            case R_AB: return v == 0 ? "0" : (v > 0 ? "A" + v : "B" + (-v));
            case R_GM: return v == 0 ? "0" : (v > 0 ? "G" + v : "M" + (-v));
            case R_PE: return Recipes.peLabel(v);
            case R_SUB: { String l = Recipes.subLabel(edit[R_PE], v); return l == null ? "-" : l; }
            case R_EV: return Recipes.evLabel(v);
            case R_DRO: return Recipes.droLabel(v);
            case R_QUAL: return qualityLabel(v);
            default: return (v > 0 ? "+" : "") + v;
        }
    }

    /** the line under the recipe name; {@code previewErr} is null while the live preview works */
    static String metaLine(int[] cur, int[] edit, String previewErr) {
        StringBuilder m = new StringBuilder();
        if (edit[R_PE] != 0) {
            String pe = Recipes.peLabel(edit[R_PE]), sl = Recipes.subLabel(edit[R_PE], edit[R_SUB]);
            m.append(Lang.t("meta_picture_effect", sl == null ? pe : pe + " " + sl)).append(' ').append(Lang.t("meta_effect_note"));
        } else m.append(Recipes.styleLabel(edit[R_STYLE]));
        String wb = edit[R_WBMODE] == WB_KELVIN ? (edit[R_KELVIN] * 100) + "K" : edit[R_WBMODE] == WB_AUTO ? Lang.t("value_auto") : Lang.t("meta_wb_mode", edit[R_WBMODE]);
        m.append("  ·  ").append(Lang.t("meta_white_balance", wb));
        if (edit[R_EV] != 0) m.append("  ·  ").append(Lang.t("meta_ev", Recipes.evLabel(edit[R_EV])));
        if (edit[R_DRO] != Recipes.DRO_AUTO) m.append("  ·  ").append(Lang.t("meta_dro", Recipes.droLabel(edit[R_DRO])));
        if (edit[R_QUAL] != cur[R_QUAL]) m.append("  ·  ").append(Lang.t("meta_quality_change", qualityLabel(edit[R_QUAL]), qualityLabel(cur[R_QUAL])));
        if (edit[R_PE] != 0 && edit[R_QUAL] <= Q_RAWJPG) m.append("  ·  ").append(Lang.t("meta_raw_effect_ignored"));
        if (previewErr != null) m.append("  ·  ").append(Lang.t("meta_no_preview", previewErr));
        return m.toString();
    }

    /** the one-line pill of the minimal overlay; {@code position} as the panel counts it (Library.position) */
    static String miniLine(Recipes.Recipe recipe, String position, int[] cur, int[] edit, boolean dirty) {
        return (edit[R_PE] != 0 ? "PE  " : "CS  ") + Recipes.displayName(recipe) + "   " + position
                + "   · " + Lang.t(dirty ? "mini_preview" : "mini_active")
                + (edit[R_QUAL] != cur[R_QUAL] ? "   · " + Lang.t("mini_quality", qualityLabel(edit[R_QUAL])) : "");
    }

    /** title and explanation of the quality-change prompt */
    static String[] qualityPrompt(int[] cur, int[] edit) {
        return new String[] {
            Lang.t("quality_prompt_title", qualityLabel(cur[R_QUAL]), qualityLabel(edit[R_QUAL])),
            Lang.t(edit[R_PE] != 0 ? "quality_prompt_effect" : "quality_prompt_style") };
    }

    // ------------------------------------------------------------ snapshot / diff tool
    /**
     * Reads "id size" lines (hex id, decimal size) into {@code into}; lines of any other shape are skipped. Fills a
     * caller-owned list so that whatever was parsed before an error survives it.
     */
    static void parseIds(BufferedReader br, List<int[]> into) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            String[] t = line.trim().split(" ");
            if (t.length == 2) into.add(new int[] { (int) Long.parseLong(t[0], 16), Integer.parseInt(t[1]) });
        }
    }

    /** up to the first four bytes as lower-case hex */
    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(b.length, 4); i++) s.append(String.format("%02x", b[i]));
        return s.toString();
    }

    /** one changed entry as the diff shows it: "01070175:01>02  " */
    static String diffEntry(int id, byte[] old, byte[] now) { return String.format("%08x:", id) + hex(old) + ">" + hex(now) + "  "; }
}
