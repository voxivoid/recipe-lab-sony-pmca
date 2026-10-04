package com.voxivoid.recipelab;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.voxivoid.recipelab.Keys.*;
import static com.voxivoid.recipelab.Params.*;

/**
 * Recipe Lab — film recipes with LIVE PREVIEW, then persistent write (photo + video, survives power-cycle).
 *
 * Preview = runtime camera parameters. ENTER picks the recipe: writes its bytes + sync → power-cycle applies it everywhere.
 *
 * Keys (issue #18 — every function on keys every body has; Fn is a shortcut where it exists, see {@link Keys}):
 *       wheel / LEFT / RIGHT recipe · UP / DOWN parameter · top dial adjust · ENTER pick · hold ENTER favourite
 *       TRASH overlay: full → pill → hidden · hold TRASH reset (asks first) · hold MENU app menu (browse, panel,
 *       language, reset, about, developer) · SHUTTER photo · MENU exit · Fn brand browser
 *       hold MENU on a custom recipe in the brand list: its options (rename, delete); a short MENU still closes the list
 *
 * Custom recipes ({@link CustomRecipes}) live on the memory card; {@link Library} gives them indexes after the table's.
 * Finishing an edit of a built-in recipe offers to keep it as a new custom recipe; an edit of a custom one is saved in
 * place. Names are typed on the name editor ({@link NameEntry}, {@link KeyboardView}).
 *
 * This class holds the state and talks to the camera, the store and the views. What a value means, how the store
 * encodes it, what the preview sets and where a key press lands is decided in {@link Params}, {@link Keys} and
 * {@link DevTools}, which have no Android in them and are covered by tools/test.sh. Every word on screen comes from
 * {@link Lang}, in the language the app menu picked; this class only applies it and its font ({@link UiFont}).
 */
public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static final int ACCENT = 0xFFF2B85C, INK = 0xFF1A1208, WHITE = 0xFFFFFFFF, DIM = 0x99FFFFFF;

    private View panel;
    private PickerView picker;
    private HorizontalScrollView chipScroll;
    private boolean swallowMenuUp = false;
    private TextView name, nameOriginal, badge, editedBadge, tag, count, meta, mini, toast;
    private StarView fav;
    private PromptView prompt;
    private int promptSel = 0, promptKind = P_QUALITY; private boolean promptOpen = false;
    /**
     * the questions the prompt asks: quality change, reset, discard a recipe's edits, a custom recipe's options, delete it
     */
    private static final int P_QUALITY = 0, P_RESET = 1, P_DISCARD = 2, P_OPTIONS = 3, P_DELETE = 4;
    private Runnable afterDiscard;                               // what the discard question goes on to do when answered Discard
    // the name editor, and what its OK does: keep a recipe's edits as a copy and store it, keep the camera's settings, rename
    private KeyboardView keyboard;
    private NameEntry nameEntry;
    private boolean nameOpen = false;
    private int nameFor = NAME_COPY;
    private static final int NAME_COPY = 0, NAME_NEW = 1, NAME_RENAME = 2;
    // custom recipes: the card's folder (null without a card), the files a load skipped, and whether they were reported
    private final Library library = new Library();
    private File cardDir;
    private List<String> skipped = new ArrayList<String>();
    private boolean skippedShown = false;
    private SharedPreferences prefs;
    private MenuView menu;
    private boolean menuOpen = false;
    private int menuLevel = DevTools.LEVEL_APP, menuPage = PAGE_ROWS;   // which menu, and whether it shows rows or a read-only page
    private int menuSel = 0, settleIdx = DevTools.SETTLE_DEFAULT;   // menu: highlighted row · developer menu: chosen settle delay
    private int langChoice = Lang.AUTO;                         // the Language row: Lang.AUTO or a language
    private static final int PAGE_ROWS = 0, PAGE_ABOUT = 1;
    private Keys.Caps caps = Keys.Caps.UNKNOWN;                 // the shortcut keys this body reports (KeyProbe)
    private HintBar hints;
    private LinearLayout chips;
    private final TextView[] chipLabel = new TextView[N], chipValue = new TextView[N];
    private final View[] chip = new View[N];
    private final Handler handler = new Handler();
    private final Runnable hideToast = new Runnable() { public void run() { toast.setVisibility(View.GONE); } };
    // press / hold of the three keys that have both: centre (pick / favourite), MENU (exit / app menu), trash (hide / factory)
    private final Keys.Hold enter = new Keys.Hold(), menuKeyHold = new Keys.Hold(), trash = new Keys.Hold();
    private final Runnable enterHold = new Runnable() { public void run() { if (enter.fire() == Keys.Hold.HOLD) toggleFavourite(); } };
    private final Runnable menuHold = new Runnable() { public void run() { menuHoldFired(); } };
    private final Runnable trashHold = new Runnable() { public void run() { trashHoldFired(); } };
    private int trashScan = K_DELETE;                           // which of trash / SK2 the held press came from
    // the key logger (developer menu): every key event on screen and into keys.txt, until MENU is held
    private boolean logging = false;
    private final List<String[]> logLines = new ArrayList<String[]>();
    private List<Integer> favs = new ArrayList<Integer>();      // marked recipes, in marking order (Favourites decides, this holds)
    private String favStored = "";                              // the same as stored, with the marks of custom recipes not loaded

    // the sample run (developer menu): one frame per recipe, driven by the handler — stage, settle, shutter, next
    private boolean running = false;
    private int runFrame = 0, runReturnTo = 0;                  // frames shot so far = the next recipe index · the recipe to come back to
    private StringBuilder runLog;                               // manifest lines, written when the run ends
    private final Runnable runStage = new Runnable() { public void run() { sampleStage(); } };
    private final Runnable runShoot = new Runnable() { public void run() { sampleShoot(); } };
    private final Runnable runNext = new Runnable() { public void run() { cancelCapture(); resumePreview(); sampleStage(); } };

    private SurfaceHolder holder;
    private Object cameraEx; private Camera camera; private String origFlat;
    private int row = 0, recipe = 0, overlay = OV_FULL;   // Params.OV_*: the full panel, the pill, nothing, the browser, the panel without keys
    private int panelBefore = OV_FULL;                    // the overlay the browser was opened from
    private boolean focus = false;                        // a chip is focused: UP/DOWN change its value
    private boolean onActions = false;                    // the highlight is on the edit buttons under the chips
    private int actionSel = 0;                            // which of them
    private LinearLayout actionsRow;
    private final TextView[] actionBtn = new TextView[4];
    private int browserCol = COL_RECIPES;                 // browser: Params.COL_GROUPS or COL_RECIPES
    private int browserGroup = 0;                     // browser: the group the brand column is on — Favourites.GROUP, .CUSTOM or a brand
    private int lastChip = 0;                         // chip to return to when leaving the recipe line
    private final int[] cur = new int[N], edit = new int[N];
    private boolean previewOk = false;
    private String previewErr = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.main);
        prefs = getPreferences(MODE_PRIVATE);
        recipe = Math.max(0, Math.min(Recipes.ALL.length - 1, prefs.getInt("recipe", 0)));
        favStored = prefs.getString("favourites", "");
        favs = Favourites.decode(favStored, library);
        settleIdx = DevTools.clampSettle(prefs.getInt("settle", DevTools.SETTLE_DEFAULT));
        langChoice = Lang.parseChoice(prefs.getString("language", null));
        panel = findViewById(R.id.panel);
        picker = (PickerView) findViewById(R.id.picker);
        chipScroll = (HorizontalScrollView) findViewById(R.id.chipscroll);
        name = (TextView) findViewById(R.id.name);
        nameOriginal = (TextView) findViewById(R.id.name_original);
        badge = (TextView) findViewById(R.id.badge);
        editedBadge = (TextView) findViewById(R.id.edited);
        tag = (TextView) findViewById(R.id.tag);
        fav = (StarView) findViewById(R.id.fav);
        count = (TextView) findViewById(R.id.count);
        meta = (TextView) findViewById(R.id.meta);
        hints = (HintBar) findViewById(R.id.hints);
        mini = (TextView) findViewById(R.id.mini);
        toast = (TextView) findViewById(R.id.toast);
        prompt = (PromptView) findViewById(R.id.prompt);
        menu = (MenuView) findViewById(R.id.menu);
        keyboard = (KeyboardView) findViewById(R.id.keyboard);
        chips = (LinearLayout) findViewById(R.id.chips);
        buildChips();
        actionsRow = (LinearLayout) findViewById(R.id.actions);
        buildActions();
        applyLanguage();
        caps = KeyProbe.caps();
        hints.setCaps(caps); picker.setCaps(caps);
        SurfaceView sv = (SurfaceView) findViewById(R.id.surface);
        holder = sv.getHolder();
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildChips() {
        for (int i : ORDER) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setPadding(dp(8), dp(3), dp(8), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(5);
            c.setLayoutParams(lp);
            TextView l = new TextView(this); l.setTextSize(9);                    // its text is set by render(), in the display language
            TextView v = new TextView(this); v.setTextSize(13); v.setTypeface(Typeface.DEFAULT_BOLD); v.setSingleLine(true);
            c.addView(l); c.addView(v);
            chips.addView(c);
            chip[i] = c; chipLabel[i] = l; chipValue[i] = v;
        }
    }

    /** the edit buttons under the chips (Save, Apply, Copy, Discard — CustomRecipes.editActions), drawn like chips */
    private void buildActions() {
        for (int i = 0; i < actionBtn.length; i++) {
            TextView b = new TextView(this);
            b.setTextSize(12); b.setTypeface(Typeface.DEFAULT_BOLD); b.setSingleLine(true);
            b.setPadding(dp(12), dp(4), dp(12), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            b.setLayoutParams(lp);
            actionsRow.addView(b);
            actionBtn[i] = b;
        }
    }

    /** the language the Language row resolves to on this camera, in every view, with its font */
    private void applyLanguage() {
        java.util.Locale l = getResources().getConfiguration().locale;
        Lang.use(Lang.resolve(langChoice, l == null ? null : l.getLanguage(), l == null ? null : l.getCountry()));
        Typeface tf = UiFont.of(this, Lang.current());
        UiFont.apply(findViewById(android.R.id.content), tf);
        picker.setTypeface(tf); prompt.setTypeface(tf); menu.setTypeface(tf); hints.setTypeface(tf); keyboard.setTypeface(tf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        reloadCustoms();                                         // the card may have been edited over USB, or swapped
        int last = library.indexOfCustom(prefs.getString("customRecipe", null));
        if (last >= 0) recipe = last;
        try {
            Class<?> cx = Class.forName("com.sony.scalar.hardware.CameraEx");
            Method open = cx.getMethod("open", int.class, Class.forName("com.sony.scalar.hardware.CameraEx$OpenOptions"));
            cameraEx = open.invoke(null, 0, null);
            camera = (Camera) cx.getMethod("getNormalCamera").invoke(cameraEx);
            origFlat = camera.getParameters().flatten();
            holder.addCallback(this);
            previewOk = true;
        } catch (Throwable t) { previewOk = false; previewErr = String.valueOf(t); }
        stageRecipe(); applyPreview(); render();
        if (!prefs.getBoolean("keysNoticeSeen", false)) {               // the keys moved in this build (issue #18): say so once
            showToast(Keys.notice(), 8000);
            prefs.edit().putBoolean("keysNoticeSeen", true).commit();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRun(false);                                          // a run cannot outlive the camera it shoots with
        closeMenu();
        closeName();
        if (promptOpen && promptKind >= P_DISCARD) { closePrompt(); afterDiscard = null; }   // about this recipe: the card is read again on the way back
        stopLogger();
        handler.removeCallbacks(hideToast);
        handler.removeCallbacks(enterHold); handler.removeCallbacks(menuHold); handler.removeCallbacks(trashHold);
        enter.reset(); menuKeyHold.reset(); trash.reset();
        holder.removeCallback(this);
        // leave the live parameters equal to what is STORED (not to the launch snapshot): the camera writes some live
        // values (exposure bias, WB fine-tune) straight back into the settings store, which would undo a fresh store
        try { if (camera != null) { load(); System.arraycopy(cur, 0, edit, 0, N); applyPreview(); } } catch (Throwable t) {}
        try { if (camera != null) camera.stopPreview(); } catch (Throwable t) {}
        try { if (cameraEx != null) cameraEx.getClass().getMethod("release").invoke(cameraEx); } catch (Throwable t) {}
        cameraEx = null; camera = null;
    }

    public void surfaceCreated(SurfaceHolder h) {
        try { camera.setPreviewDisplay(h); camera.startPreview(); }
        catch (Throwable t) { previewOk = false; previewErr = String.valueOf(t); render(); }
    }
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
    public void surfaceDestroyed(SurfaceHolder h) {}

    // ------------------------------------------------------------ stored settings
    /** a slot's byte as an unsigned index */
    private int rdu(int id) throws NativeException { return NativeBackup.readByte(id) & 0xff; }

    private void load() {
        try {
            for (int i = 1; i < N; i++) {
                int id = ROW_ID[i];
                if (id == SUB_SLOT) { int sid = Recipes.subId(cur[R_PE]); cur[i] = edit[i] = sid == 0 ? 0 : rdu(sid); continue; }
                if (id == QUALITY_SLOTS) { cur[i] = edit[i] = readQuality(); continue; }
                cur[i] = edit[i] = Params.fromStore(id, NativeBackup.readByte(id));
            }
        } catch (Throwable t) { showToast(Lang.t("status_read_failed", String.valueOf(t.getMessage())), 0); }
    }

    private void stageRecipe() {
        Recipes.Recipe r = library.get(recipe);
        Params.stage(r, edit);
        edit[R_QUAL] = recipeQuality(r);
        // reopen on the last selected recipe: a custom one by name, as its index moves; the table's one stays as the fallback
        if (r.isCustom()) prefs.edit().putString("customRecipe", r.name).commit();
        else prefs.edit().putInt("recipe", recipe).remove("customRecipe").commit();
    }

    /** quality from the two stored bytes; falls back to the runtime value when the slots are not known yet */
    private int readQuality() {
        try {
            int q = Params.qualityFromStore(rdu(ID_QFMT), rdu(ID_QJPG));
            if (q >= 0) return q;
            if (camera != null) {
                Camera.Parameters p = camera.getParameters();
                return Params.qualityFromRuntime(p.get("storage-fmt"), p.get("jpeg-quality"));
            }
        } catch (Throwable t) {}
        return Q_FINE;
    }

    /** the Factory recipe's quality: the camera's current one until the user changes it in the app, then remembered */
    private int baseQuality() { int b = prefs.getInt("baseQuality", -1); return b >= 0 && b < 4 ? b : cur[R_QUAL]; }
    private int recipeQuality(Recipes.Recipe r) { return Params.recipeQuality(r, baseQuality()); }
    /** user changed quality on the current recipe: CS recipes and JPEG choices on PE recipes redefine the Factory quality */
    private void qualityChanged() {
        if (Params.redefinesBaseQuality(library.get(recipe), edit[R_QUAL])) prefs.edit().putInt("baseQuality", edit[R_QUAL]).commit();
    }
    private boolean qualityPersistent() { return ID_QFMT != 0; }

    /** stored value of the SUB slot for the staged effect (the slot changes with the effect) */
    private int storedSub() { int sid = Recipes.subId(edit[R_PE]); if (sid == 0) return edit[R_SUB]; try { return rdu(sid); } catch (Throwable t) { return edit[R_SUB]; } }

    private boolean qualityChanges() { return edit[R_QUAL] != cur[R_QUAL]; }

    private boolean rowDirty(int i) { return Params.rowDirty(i, cur, edit, i == R_SUB ? storedSub() : 0); }
    private boolean dirty() { for (int i = 1; i < N; i++) if (rowDirty(i)) return true; return false; }
    private boolean rowVisible(int i) { return Params.rowVisible(i, edit); }

    private void writeAll() { writeAll(false); }

    /** the attribute of every slot a pending write touches; -1 where the camera would not answer */
    private int[] attrsOf(List<Params.Write> ws) {
        int[] attrs = new int[ws.size()];
        for (int i = 0; i < attrs.length; i++) {
            try { attrs[i] = NativeBackup.attr(ws.get(i).id); } catch (Throwable t) { attrs[i] = -1; }
        }
        return attrs;
    }

    private void writeAll(boolean confirmed) {
        if (!confirmed && qualityChanges()) { openPrompt(P_QUALITY, 0); return; }
        if (!dirty()) { showToast(Lang.t("status_already_picked"), 2500); return; }
        int storedSub = storedSub();
        int n = Params.dirtyRows(cur, edit, storedSub);
        List<Params.Write> ws = Params.writes(cur, edit, storedSub);
        List<Integer> locked = Params.lockedFrom(ws, attrsOf(ws));   // the slots backup protection would refuse (issue #19)
        if (!locked.isEmpty()) { showToast(Params.lockedMessage(locked), 0); return; }   // nothing written, so nothing half-applied

        String msg = null;
        int written = 0;
        for (Params.Write w : ws) {
            try { NativeBackup.writeByte(w.id, w.value); written++; }
            catch (Throwable t) { msg = Params.writeFailedMessage(w.id, String.valueOf(t.getMessage()), written); break; }
        }
        if (written > 0) NativeBackup.sync();                    // Backup_sync_all is void: nothing to catch, nothing to report
        boolean ok = msg == null;
        if (ok) msg = Lang.t(n == 1 ? "status_picked_one" : "status_picked_many", n);
        // what was written is now the look: keep it on the chips — re-staging the recipe would drop edits just applied
        // to a built-in one ("Apply only"); after a refusal, the recipe is staged again, ready to retry
        load();
        if (!ok) stageRecipe();
        showToast(msg, ok ? 5000 : 0); render();
    }

    // ------------------------------------------------------------ the questions: quality, reset, and the custom recipe ones
    private void openPrompt(int kind, int sel) { promptOpen = true; promptKind = kind; promptSel = sel; renderPrompt(); }

    /** hold trash, or Reset settings in the app menu: ask before the factory look replaces the current one */
    private void askReset() { openPrompt(P_RESET, DevTools.RESET_DEFAULT); }

    private void renderPrompt() {
        Recipes.Recipe r = library.get(recipe);
        switch (promptKind) {
            case P_RESET: prompt.set(DevTools.resetTitle(), DevTools.resetBody(), DevTools.resetOptions(), promptSel, null); break;
            case P_DISCARD: prompt.set(CustomRecipes.discardTitle(Recipes.displayName(r)), CustomRecipes.discardBody(), CustomRecipes.discardOptions(), promptSel, null); break;
            case P_OPTIONS: prompt.set(r.name, CustomRecipes.optionsBody(library.entry(recipe).file), CustomRecipes.options(), promptSel, null); break;
            case P_DELETE: prompt.set(CustomRecipes.deleteTitle(r.name), CustomRecipes.deleteBody(library.entry(recipe).file), CustomRecipes.deleteOptions(), promptSel, null); break;
            default: {
                String[] q = Params.qualityPrompt(cur, edit);
                String[] opts = { Lang.t("button_accept"), Lang.t("button_cancel") };
                prompt.set(q[0], q[1], opts, promptSel, qualityPersistent() ? null : Lang.t("quality_slot_note"));
            }
        }
        prompt.setVisibility(View.VISIBLE);
    }

    private void closePrompt() { prompt.setVisibility(View.GONE); promptOpen = false; }

    private int promptOptions() {
        switch (promptKind) {
            case P_OPTIONS: return CustomRecipes.options().length;
            default: return 2;
        }
    }

    private boolean promptKey(int sc) {
        switch (sc) {
            case K_LEFT: case K_WHEEL_CCW: case K_DIAL_CCW: promptSel = (promptSel + promptOptions() - 1) % promptOptions(); renderPrompt(); return true;
            case K_RIGHT: case K_WHEEL_CW: case K_DIAL_CW: promptSel = (promptSel + 1) % promptOptions(); renderPrompt(); return true;
            case K_ENTER: closePrompt(); promptAnswered(); render(); return true;
            case K_MENU: case K_SK1:
                swallowMenuUp = true; closePrompt();
                if (promptKind == P_DISCARD) afterDiscard = null;
                render(); return true;
        }
        return true;
    }

    /** the centre button on a highlighted answer */
    private void promptAnswered() {
        switch (promptKind) {
            case P_RESET: if (promptSel == 0) storeFactory(); else showToast(Lang.t("status_not_reset"), 2000); return;
            case P_DISCARD: {
                Runnable then = afterDiscard; afterDiscard = null;
                if (promptSel == CustomRecipes.DISCARD && then != null) then.run();
                return;
            }
            case P_OPTIONS:
                if (promptSel == CustomRecipes.OPT_RENAME) openName(NAME_RENAME);
                else if (promptSel == CustomRecipes.OPT_DELETE) openPrompt(P_DELETE, CustomRecipes.DELETE_DEFAULT);
                return;
            case P_DELETE: if (promptSel == 0) deleteCustom(); return;
            default: if (promptSel == 0) writeAll(true); else showToast(Lang.t("status_not_picked"), 2000);   // cancel: recipe stays previewed only
        }
    }

    private void cycleQuality() {
        edit[R_QUAL] = (edit[R_QUAL] + 1) % 4; qualityChanged(); applyPreview(); render();
        showToast(Lang.t(qualityPersistent() ? "status_quality_pick" : "status_quality_live", qualityLabel(edit[R_QUAL])), 2500);
    }

    // ------------------------------------------------------------ snapshot / diff of the whole settings store (developer menu)
    private File snapFile() { return new File(getFilesDir(), "snapshot.bin"); }

    private List<int[]> idList() {
        List<int[]> ids = new ArrayList<int[]>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.ids)));
            try { Params.parseIds(br, ids); } finally { br.close(); }
        } catch (Throwable t) {}
        return ids;
    }

    private void snapshotOrDiff() {
        List<int[]> ids = idList();
        File f = snapFile();
        try {
            if (!f.exists()) {
                FileOutputStream o = new FileOutputStream(f);
                for (int[] e : ids) { byte[] v; try { v = NativeBackup.read(e[0]); } catch (Throwable t) { v = new byte[0]; } o.write(v.length); o.write(v); }
                o.close();
                showToast(Lang.t("status_snapshot_taken", ids.size()), 6000);
                return;
            }
            FileInputStream in = new FileInputStream(f);
            StringBuilder sb = new StringBuilder(); int changed = 0;
            for (int[] e : ids) {
                int len = in.read(); byte[] old = new byte[Math.max(0, len)]; if (len > 0) in.read(old);
                byte[] now; try { now = NativeBackup.read(e[0]); } catch (Throwable t) { now = new byte[0]; }
                if (!java.util.Arrays.equals(old, now)) {
                    changed++;
                    if (changed <= 14) sb.append(Params.diffEntry(e[0], old, now));
                }
            }
            in.close(); f.delete();
            java.io.FileWriter w = new java.io.FileWriter(new File(getFilesDir(), "diff.txt"), true); w.write(changed + " changed  " + sb + "\n"); w.close();
            showToast(Lang.t("status_diff_changed", changed, sb.toString()), 0);
        } catch (Throwable t) { showToast(Lang.t("status_snapshot_error", String.valueOf(t)), 0); }
    }

    // ------------------------------------------------------------ read-only check of the slots a recipe writes
    /**
     * Whether this body holds any slot a recipe writes read-only — the question the PROTECTED badge used to ask
     * of one unrelated slot (issue #19). Every slot of {@link Params#allSlots()} is listed with its attribute in
     * locks.txt, so a compatibility report can quote it.
     */
    private void lockCheck() {
        List<Integer> ids = Params.allSlots();
        int[] attrs = new int[ids.size()];
        for (int i = 0; i < attrs.length; i++) {
            try { attrs[i] = NativeBackup.attr(ids.get(i)); } catch (Throwable t) { attrs[i] = -1; }
        }
        String text = Params.lockReport(ids, attrs), file;
        int was = Lang.use(Lang.EN);                              // the file is quoted in compatibility reports: English
        try { file = Params.lockReport(ids, attrs); } finally { Lang.use(was); }
        try {
            java.io.FileWriter w = new java.io.FileWriter(new File(getFilesDir(), "locks.txt"), true);
            try { w.write(file + "\n" + Params.lockLines(ids, attrs)); } finally { w.close(); }
        } catch (Throwable t) { text += "  ·  " + Lang.t("status_file_failed", "locks.txt", String.valueOf(t)); }
        showToast(text, 0);
    }

    // ------------------------------------------------------------ app menu (MENU hold), developer menu under it, and the sample run
    private void openMenu(int level) {
        if (running) return;
        menuOpen = true; menuLevel = level; menuPage = PAGE_ROWS; menuSel = 0; renderMenu();
    }

    private void renderMenu() {
        if (menuPage == PAGE_ABOUT) menu.setPage(DevTools.aboutTitle(), DevTools.about(versionName(), KeyProbe.prop("model.name"),
                KeyProbe.prop("version.platform")), Keys.hints(Keys.H_PAGE, caps));
        else {
            int n = DevTools.rows(menuLevel);
            boolean app = menuLevel == DevTools.LEVEL_APP, snapshotTaken = !app && snapFile().exists();
            String[] labels = new String[n], details = new String[n], values = new String[n];
            Typeface[] faces = new Typeface[n];
            for (int i = 0; i < n; i++) {
                labels[i] = app ? DevTools.appLabel(i) : DevTools.rowLabel(i, snapshotTaken, settleIdx);
                details[i] = app ? DevTools.appDetail(i) : DevTools.rowDetail(i, snapshotTaken);
                values[i] = app ? DevTools.appValue(i, overlay, langChoice) : DevTools.rowValue(i, settleIdx);
            }
            if (app) faces[DevTools.APP_LANG] = UiFont.of(this, Lang.choiceScript(langChoice));   // 简体中文 in its own font, whatever the menu's
            boolean value = values[menuSel] != null;
            int legend = app ? (value ? Keys.H_MENU_TOP_VALUE : Keys.H_MENU_TOP) : (value ? Keys.H_MENU_SUB_VALUE : Keys.H_MENU_SUB);
            menu.set(app ? DevTools.APP_TITLE : DevTools.title(), labels, details, values, faces, menuSel, Keys.hints(legend, caps));
        }
        menu.setVisibility(View.VISIBLE);
    }

    private void closeMenu() { menu.setVisibility(View.GONE); menuOpen = false; }

    /** the installed version, from the package — never a string in this file (tools/check-version.sh) */
    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable t) { return null; }
    }

    /** what the key probe says about every key it is asked about, for the key logger */
    private String keysFound() {
        Boolean[] has = new Boolean[KeyProbe.REPORTED.length];
        for (int i = 0; i < has.length; i++) has[i] = KeyProbe.has(KeyProbe.REPORTED[i]);
        return DevTools.keysFound(KeyProbe.REPORTED, has);
    }

    /** the centre button on a menu row */
    private void pickMenuRow() {
        if (menuLevel == DevTools.LEVEL_APP) {
            switch (menuSel) {
                case DevTools.APP_BROWSE: closeMenu(); browse(); break;
                case DevTools.APP_NEW: closeMenu(); openName(NAME_NEW); break;   // the camera's current settings, kept as a recipe
                case DevTools.APP_PANEL: case DevTools.APP_LANG: stepMenuValue(+1); break;
                case DevTools.APP_RESET: closeMenu(); askReset(); break;
                case DevTools.APP_ABOUT: menuPage = PAGE_ABOUT; renderMenu(); break;
                case DevTools.APP_DEV: menuLevel = DevTools.LEVEL_DEV; menuSel = 0; renderMenu(); break;
            }
            return;
        }
        // developer menu: the tools close the menu and run, the delay row stays open and cycles
        switch (menuSel) {
            case DevTools.ROW_SNAPSHOT: closeMenu(); snapshotOrDiff(); break;
            case DevTools.ROW_LOCKS: closeMenu(); lockCheck(); break;
            case DevTools.ROW_SAMPLES: closeMenu(); startRun(); break;
            case DevTools.ROW_SETTLE: stepMenuValue(+1); break;
            case DevTools.ROW_KEYS: closeMenu(); startLogger(); break;
        }
    }

    /** left / right on a row that has a value: change it in place, the menu stays open; false when the row has none */
    private boolean stepMenuValue(int dir) {
        if (menuLevel == DevTools.LEVEL_APP && menuSel == DevTools.APP_PANEL) overlay = DevTools.nextPanel(overlay, dir);
        else if (menuLevel == DevTools.LEVEL_APP && menuSel == DevTools.APP_LANG) {
            langChoice = Lang.nextChoice(langChoice, dir);
            prefs.edit().putString("language", Lang.choiceCode(langChoice)).commit();
            applyLanguage();                                     // the menu redraws in it straight away, the panel under it too
        } else if (menuLevel == DevTools.LEVEL_DEV && menuSel == DevTools.ROW_SETTLE) {
            settleIdx = DevTools.nextSettle(settleIdx, dir);
            prefs.edit().putInt("settle", settleIdx).commit();
        } else return false;
        renderMenu(); render();
        return true;
    }

    /** keys while a menu is up: MENU goes back a level (a page back to its menu, the developer menu back to the app menu), then closes */
    private boolean menuKey(int sc) {
        if (menuPage != PAGE_ROWS) {
            if (isMenu(sc)) swallowMenuUp = true;
            if (isMenu(sc) || sc == K_ENTER) { menuPage = PAGE_ROWS; renderMenu(); }
            return true;
        }
        switch (sc) {
            // the dial moves rows like the wheel: on the A5100 the control wheel itself arrives as the dial (525 / 526)
            case K_UP: case K_WHEEL_CCW: case K_DIAL_CCW: menuSel = DevTools.nextRow(menuLevel, menuSel, -1); renderMenu(); return true;
            case K_DOWN: case K_WHEEL_CW: case K_DIAL_CW: menuSel = DevTools.nextRow(menuLevel, menuSel, +1); renderMenu(); return true;
            case K_LEFT: stepMenuValue(-1); return true;                         // only rows with a value take left / right
            case K_RIGHT: stepMenuValue(+1); return true;
            case K_ENTER: pickMenuRow(); return true;
            case K_MENU: case K_SK1:
                swallowMenuUp = true;
                if (menuLevel == DevTools.LEVEL_DEV) { menuLevel = DevTools.LEVEL_APP; menuSel = DevTools.APP_DEV; renderMenu(); }
                else closeMenu();
                return true;
        }
        return true;
    }

    // ------------------------------------------------------------ key logger (developer menu)
    private void startLogger() {
        logging = true; logLines.clear();
        appendKeyLog("# " + DevTools.logTitle(KeyProbe.prop("model.name"), KeyProbe.prop("version.platform")) + "  ·  " + keysFound());
        renderLogger();
    }

    private void stopLogger() {
        if (!logging) return;
        logging = false; logLines.clear();
        menu.setVisibility(View.GONE);
    }

    private void renderLogger() {
        String[][] lines = new String[logLines.size() + 1][];
        lines[0] = new String[] { "keys", keysFound() };
        for (int i = 0; i < logLines.size(); i++) lines[i + 1] = logLines.get(logLines.size() - 1 - i);   // newest first
        menu.setPage(DevTools.logTitle(KeyProbe.prop("model.name"), KeyProbe.prop("version.platform")), lines, Keys.hints(Keys.H_LOGGER, caps));
        menu.setVisibility(View.VISIBLE);
    }

    private void appendKeyLog(String line) {
        try {
            java.io.FileWriter w = new java.io.FileWriter(new File(getFilesDir(), DevTools.KEY_LOG), true);
            try { w.write(line + "\n"); } finally { w.close(); }
        } catch (Throwable t) {}
    }

    /** every key while the logger runs: shown and written, nothing else happens; a MENU hold leaves */
    private boolean logKey(KeyEvent e, boolean down) {
        int sc = e.getScanCode();
        String[] line = DevTools.logLine(down, sc, e.getRepeatCount(), down ? KeyProbe.logic(sc, 4) : null);
        logLines.add(line);
        while (logLines.size() > DevTools.LOG_LINES) logLines.remove(0);
        appendKeyLog(line[0] + "  " + line[1] + "  ·  keyCode " + e.getKeyCode());
        if (isMenu(sc)) {
            if (down) { if (menuKeyHold.down(e.getRepeatCount()) == Keys.Hold.ARM) handler.postDelayed(menuHold, HOLD_MS); }
            else { handler.removeCallbacks(menuHold); menuKeyHold.up(); swallowMenuUp = false; }
        }
        if (logging) renderLogger();
        return true;
    }

    /** every key is swallowed while the run walks the table, so nothing changes the recipe mid-run; MENU stops it */
    private boolean runKey(int sc) {
        if (sc == K_MENU || sc == K_SK1) { swallowMenuUp = true; stopRun(true); }
        return true;
    }

    private int settleMs() { return DevTools.SETTLE_MS[DevTools.clampSettle(settleIdx)]; }

    /** shoot one frame per recipe, in table order: the gallery of issue #17, and a preview-pipeline test */
    private void startRun() {
        if (camera == null || !previewOk) { showToast(DevTools.noPreview(), 5000); return; }
        running = true; runFrame = 0; runReturnTo = recipe;
        runLog = new StringBuilder(DevTools.manifestHeader(Recipes.ALL.length, settleMs())).append('\n');
        handler.post(runStage);
    }

    /** apply the next recipe and give the preview pipeline the settle delay before the shutter */
    private void sampleStage() {
        if (!running) return;
        if (runFrame >= Recipes.ALL.length) { endRun(DevTools.doneMessage(runFrame, Recipes.ALL.length)); return; }
        recipe = runFrame;
        stageRecipe();
        edit[R_QUAL] = Q_FINE;                                  // a sample is only a sample as a JPEG with the look in it, whatever the user shoots
        applyPreview(); render();
        showToast(DevTools.progress(runFrame + 1, Recipes.ALL.length, Recipes.displayName(Recipes.ALL[runFrame])), 0);
        handler.postDelayed(runShoot, settleMs());
    }

    /** fire the shutter, then let the capture finish before the next recipe is applied */
    private void sampleShoot() {
        if (!running) return;
        try { camera.takePicture(null, null, null); }
        catch (Throwable t) {
            String msg = DevTools.shootFailed(runFrame + 1, runFrame, String.valueOf(t.getMessage()));
            endRun(msg); return;
        }
        runLog.append(DevTools.manifestLine(runFrame + 1, runFrame)).append('\n');
        runFrame++;
        handler.postDelayed(runNext, DevTools.SHUTTER_MS);
    }

    /** MENU during the run, or the camera going away under it */
    private void stopRun(boolean tell) {
        if (!running) return;
        endRun(tell ? DevTools.stoppedMessage(runFrame, Recipes.ALL.length) : null);
    }

    private void endRun(String msg) {
        running = false;
        handler.removeCallbacks(runStage); handler.removeCallbacks(runShoot); handler.removeCallbacks(runNext);
        cancelCapture();
        String failed = writeManifest();
        recipe = runReturnTo; stageRecipe(); applyPreview();
        if (msg != null) showToast(msg + failed, 0);
        render();
    }

    /** the frame list of the run: which recipe each frame was shot with, in order — the camera names the files */
    private String writeManifest() {
        if (runLog == null || runFrame == 0) return "";
        try {
            java.io.FileWriter w = new java.io.FileWriter(new File(getFilesDir(), DevTools.MANIFEST), true);
            try { w.write(runLog.toString()); } finally { w.close(); }
        } catch (Throwable t) { return "  ·  " + Lang.t("status_file_failed", DevTools.MANIFEST, String.valueOf(t)); }
        finally { runLog = null; }
        return "";
    }

    /** the shutter key's release, as the run and the key handler both need it */
    private void cancelCapture() { try { if (cameraEx != null) cameraEx.getClass().getMethod("cancelTakePicture").invoke(cameraEx); } catch (Throwable t) {} }

    /**
     * Between two frames of a run: a capture leaves the preview stopped on a plain Android camera, and nothing is
     * applied to a stopped preview. Whether this body needs it is a question for the camera — on one that restarts
     * the preview itself the call is a no-op, and a HAL that objects to it throws in here rather than out there.
     */
    private void resumePreview() { try { if (camera != null) camera.startPreview(); } catch (Throwable t) {} }

    // ------------------------------------------------------------ favourites (app storage, not the camera store: lost on uninstall)
    private void saveFavourites() {
        favStored = Favourites.encode(favs, library, favStored);
        prefs.edit().putString("favourites", favStored).commit();
    }

    /** hold on the centre button: mark / unmark the highlighted recipe, built-in or custom */
    private void toggleFavourite() {
        int pos = favs.indexOf(recipe);
        boolean on = Favourites.toggle(favs, recipe);
        saveFavourites();
        showToast(Favourites.toggleMessage(Recipes.displayName(library.get(recipe)), on), 2500);
        if (overlay == OV_BROWSER && browserGroup == Favourites.GROUP && !on) {
            // unmarked inside the Favourites list: the highlight moves to a neighbour, or back to the brand column when the list is empty
            int next = Favourites.afterRemoval(favs, pos);
            if (next < 0) browserCol = COL_GROUPS;
            else { recipe = next; stageRecipe(); applyPreview(); }
        }
        render();
    }


    // ------------------------------------------------------------ custom recipes (the memory card, RECIPES)
    /** the card's recipe folder; null when no card is in or the camera will not say */
    private static File findCardDir() {
        try {
            if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) return null;
            return new File(Environment.getExternalStorageDirectory(), CustomRecipes.DIR);
        } catch (Throwable t) { return null; }
    }

    /** the model a saved file says it was made on */
    private static String madeOn() { return KeyProbe.prop("model.name"); }

    /**
     * Reads the card's recipes again. The highlighted custom recipe is followed by name, as its index can move; when it
     * is gone the first listed recipe takes its place. The favourites are decoded again for the new indexes.
     */
    private void reloadCustoms() {
        String keep = library.isCustom(recipe) ? library.get(recipe).name : null;
        cardDir = findCardDir();
        CustomRecipes.Loaded l = CustomRecipes.load(cardDir);
        library.set(l.entries);
        if (!l.skipped.equals(skipped)) { skipped = l.skipped; skippedShown = false; }
        favs = Favourites.decode(favStored, library);
        if (keep != null) recipe = library.indexOfCustom(keep);
        if (!library.valid(recipe)) recipe = 0;
    }

    /** the files the last load skipped, said once, the first time the Custom group is shown after it */
    private void reportSkipped() {
        if (skippedShown || skipped.isEmpty()) return;
        skippedShown = true;
        showToast(CustomRecipes.skippedMessage(skipped), 0);
    }

    /** the name editor for one of the NAME_* purposes; refused without a card, as nothing could be kept */
    private void openName(int purpose) {
        if (cardDir == null) { showToast(Lang.t("custom_need_card"), 4000); return; }
        if (purpose != NAME_RENAME) {                           // nothing to name if the look is not one a file can hold
            String bad = CustomRecipes.problem(CustomRecipes.recipe(CustomRecipes.UNTITLED, purpose == NAME_NEW ? cur : edit));
            if (bad != null) { showToast(Lang.t("custom_capture_failed", bad), 0); return; }
        }
        nameFor = purpose;
        nameEntry = purpose == NAME_RENAME ? NameEntry.of(library.get(recipe).name)
                : NameEntry.blank(purpose == NAME_COPY ? CustomRecipes.copyName(library.get(recipe).name, library.customNames()) : CustomRecipes.defaultName(library.customNames()));
        nameOpen = true;
        renderName();
    }

    private void renderName() {
        String title = nameFor == NAME_RENAME ? Lang.t("name_title_rename", library.get(recipe).name) : Lang.t("name_title_new");
        keyboard.set(title, nameEntry, Keys.hints(Keys.H_NAME, caps));
        keyboard.setVisibility(View.VISIBLE);
    }

    private void closeName() { if (keyboard != null) keyboard.setVisibility(View.GONE); nameOpen = false; }

    /** keys while the name editor is up: the four-way and the dials move, centre types, trash deletes, MENU cancels */
    private boolean nameKey(int sc) {
        switch (sc) {
            case K_UP: nameEntry.move(-1, 0); break;
            case K_DOWN: nameEntry.move(+1, 0); break;
            case K_LEFT: nameEntry.move(0, -1); break;
            case K_RIGHT: nameEntry.move(0, +1); break;
            case K_WHEEL_CW: case K_DIAL_CW: nameEntry.step(+1); break;
            case K_WHEEL_CCW: case K_DIAL_CCW: nameEntry.step(-1); break;
            case K_ENTER: if (nameEntry.press() == NameEntry.DONE) { nameDone(); return true; } break;
            case K_DELETE: case K_SK2: nameEntry.backspace(); break;
            case K_MENU: case K_SK1:
                swallowMenuUp = true; closeName();
                render(); return true;
            default: return true;
        }
        renderName();
        return true;
    }

    /** OK on the name editor: a name nobody has is kept, anything else says why under the field */
    private void nameDone() {
        String name = nameEntry.value();
        String self = nameFor == NAME_RENAME ? library.get(recipe).name : null;
        String bad = CustomRecipes.nameProblem(name, library.customNames(), self);
        if (bad != null) { nameEntry.setError(bad); renderName(); return; }
        closeName();
        switch (nameFor) {
            case NAME_NEW: saveNew(name, cur); break;
            case NAME_COPY: focus = false; if (saveNew(name, edit)) writeAll(); break;   // the copy is the look now: stored too
            case NAME_RENAME: renameCustom(name); break;
        }
        render();
    }

    /** keeps {@code rows} as a new custom recipe and highlights it; false when the card refused */
    private boolean saveNew(String name, int[] rows) {
        CustomRecipes.Entry e;
        try { e = CustomRecipes.save(cardDir, CustomRecipes.recipe(name, rows), madeOn(), null); }
        catch (Throwable t) { showToast(Lang.t("custom_save_failed", String.valueOf(t.getMessage())), 0); return false; }
        reloadCustoms();
        follow(name);
        stageRecipe(); applyPreview();
        showToast(Lang.t("custom_created", name, CustomRecipes.path(e.file)), 5000);
        return true;
    }

    /** an edit of a custom recipe finished: its file takes the new values */
    private void saveInPlace() {
        CustomRecipes.Entry e = library.entry(recipe);
        if (cardDir == null) { showToast(Lang.t("custom_need_card"), 4000); return; }
        try { CustomRecipes.save(cardDir, CustomRecipes.recipe(e.recipe.name, edit), madeOn(), e.file); }
        catch (Throwable t) { showToast(Lang.t("custom_save_failed", String.valueOf(t.getMessage())), 0); return; }
        reloadCustoms();
        showToast(Lang.t("custom_saved", e.recipe.name), 2500);
    }

    private void renameCustom(String name) {
        CustomRecipes.Entry e = library.entry(recipe);
        if (name.equals(e.recipe.name)) return;
        try { CustomRecipes.save(cardDir, CustomRecipes.renamed(e.recipe, name), madeOn(), e.file); }
        catch (Throwable t) { showToast(Lang.t("custom_save_failed", String.valueOf(t.getMessage())), 0); return; }
        favStored = Favourites.renameCustom(favStored, e.recipe.name, name);
        prefs.edit().putString("favourites", favStored).commit();
        reloadCustoms();
        follow(name);
        stageRecipe();
        showToast(Lang.t("custom_renamed", name), 3000);
    }

    /** highlights the custom recipe just written — unless the reload could not read it back, which leaves the highlight where it is */
    private void follow(String name) { int i = library.indexOfCustom(name); if (i >= 0) recipe = i; }

    /** the delete question answered Delete: the file goes, and its favourite mark; the highlight moves to a neighbour */
    private void deleteCustom() {
        CustomRecipes.Entry e = library.entry(recipe);
        int k = recipe - Library.BASE, favPos = favs.indexOf(recipe);
        try { CustomRecipes.delete(cardDir, e.file); }
        catch (Throwable t) { showToast(Lang.t("custom_delete_failed", e.recipe.name, String.valueOf(t.getMessage())), 0); return; }
        favStored = Favourites.forgetCustom(favStored, e.recipe.name);
        prefs.edit().putString("favourites", favStored).commit();
        reloadCustoms();                                         // the recipe is gone: the first one stands in
        if (overlay == OV_BROWSER && browserGroup == Favourites.GROUP) {
            int next = Favourites.afterRemoval(favs, favPos);
            if (next < 0) browserCol = COL_GROUPS; else recipe = next;
        } else if (library.customCount() > 0) recipe = Library.BASE + Math.min(k, library.customCount() - 1);
        else if (overlay == OV_BROWSER && browserGroup == Favourites.CUSTOM) browserCol = COL_GROUPS;   // nothing left to highlight
        stageRecipe(); applyPreview();
        showToast(Lang.t("custom_deleted", e.recipe.name), 3000);
        render();
    }

    /** the staged values are not the recipe's own: its edits, saved nowhere yet (the line under the name says so) */
    private boolean edited() { return Params.differsFromRecipe(library.get(recipe), edit); }

    /** edits that would be lost by leaving the recipe: neither saved nor applied — after Apply the camera has them */
    private boolean editsAtRisk() { return edited() && dirty(); }

    /** goes on with {@code go} — leaving the recipe — after asking, when that would drop edits nothing keeps */
    private void unlessEditsLost(Runnable go) {
        if (!editsAtRisk()) { go.run(); return; }
        afterDiscard = go;
        openPrompt(P_DISCARD, CustomRecipes.DISCARD_DEFAULT);
    }

    /**
     * The centre button on the recipe line: pick it. With edits on it, that means going back to the recipe, so ask first —
     * Discard puts its own values back and picks them; saving, copying and applying edits are the buttons under the chips.
     */
    private void pick() {
        if (!edited()) { writeAll(); return; }
        afterDiscard = new Runnable() { public void run() { stageRecipe(); applyPreview(); writeAll(); } };
        openPrompt(P_DISCARD, CustomRecipes.DISCARD_DEFAULT);
    }

    /** the edit buttons that make sense now: none without edits, or without the full panel to show them on */
    private int[] actions() {
        if (!panelUp(overlay) || !edited()) return new int[0];
        return CustomRecipes.editActions(library.isCustom(recipe), !dirty());
    }

    /** centre on an edit button */
    private void doAction(int action) {
        switch (action) {
            case CustomRecipes.EDIT_SAVE: saveInPlace(); break;     // custom recipes only: a built-in one has Copy
            case CustomRecipes.EDIT_COPY: openName(NAME_COPY); break;
            case CustomRecipes.EDIT_APPLY: writeAll(); break;
            case CustomRecipes.EDIT_DISCARD:                         // asks first; then the recipe's own values, written too when the camera had the edits
                afterDiscard = new Runnable() { public void run() {
                    boolean applied = !dirty();
                    stageRecipe(); applyPreview();
                    if (applied) writeAll();
                    render();
                } };
                openPrompt(P_DISCARD, CustomRecipes.DISCARD_DEFAULT);
                break;
        }
        render();
    }

    /** the highlight is on the edit buttons and they are on screen — not under the pill, or after the edits went */
    private boolean onButtons() { return onActions && actions().length > 0; }

    /** the panel's line the highlight is on (Params.LINE_*) */
    private int line() { return onButtons() ? LINE_ACTIONS : row == 0 ? LINE_RECIPE : LINE_CHIPS; }

    /** UP / DOWN: recipe name → chips → edit buttons (while there are edits) → recipe name */
    private void moveLine(int dir) {
        int to = Params.nextLine(line(), dir, actions().length > 0);
        if (row != 0) lastChip = row;
        if (to == LINE_RECIPE) { row = 0; onActions = false; }
        else if (to == LINE_CHIPS) { row = Params.enterChips(lastChip, edit); onActions = false; }
        else { row = Params.enterChips(lastChip, edit); onActions = true; actionSel = 0; }   // row stays off the recipe line
        render();
    }

    /** the buttons went away (saved, restored, another recipe): the highlight goes back to the chips */
    private void settleActions(int count) {
        if (onActions && count == 0) { onActions = false; row = Params.enterChips(lastChip, edit); }
        if (actionSel >= count) actionSel = Math.max(0, count - 1);
    }

    // ------------------------------------------------------------ live preview (runtime params)
    private void applyPreview() {
        if (camera == null) return;
        try {
            Camera.Parameters p = camera.getParameters();
            for (Map.Entry<String, String> e : Params.preview(edit).entrySet()) p.set(e.getKey(), e.getValue());
            camera.setParameters(p);
            previewOk = true;
        } catch (Throwable t) { previewOk = false; previewErr = String.valueOf(t.getMessage()); }
    }

    // ------------------------------------------------------------ UI
    private void showToast(String msg, int ms) {
        toast.setText(msg); toast.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideToast);
        if (ms > 0) handler.postDelayed(hideToast, ms);
    }

    private void render() {
        Recipes.Recipe r = library.get(recipe);
        boolean dirty = dirty();
        String pos = library.position(recipe);
        String grp = Recipes.groupLabel(r.group).toUpperCase();
        picker.setVisibility(overlay == OV_BROWSER ? View.VISIBLE : View.GONE);
        if (overlay == OV_BROWSER) { panel.setVisibility(View.GONE); mini.setVisibility(View.GONE); picker.set(recipe, browserCol, browserGroup, favs, library, cardDir != null); return; }
        if (panelUp(overlay)) {
            panel.setVisibility(View.VISIBLE); mini.setVisibility(View.GONE);
            name.setText(Recipes.displayName(r));
            name.setTextColor(row == 0 ? ACCENT : WHITE);
            String original = Recipes.originalName(r);            // a translated name keeps the canonical one under it
            nameOriginal.setText(original == null ? "" : original);
            nameOriginal.setVisibility(original == null ? View.GONE : View.VISIBLE);
            count.setText(grp + "   " + pos);
            tag.setText(Lang.t("tag_jpeg_only"));                    // a Picture Effect: the camera drops it under RAW
            tag.setTextColor(ACCENT);
            tag.setVisibility(edit[R_PE] != 0 ? View.VISIBLE : View.GONE);
            fav.setVisibility(favs.contains(recipe) ? View.VISIBLE : View.GONE);
            // two questions, two badges: does the camera have what you see (ACTIVE / PREVIEW), and is it the recipe as it was (EDITED)
            if (dirty) { badge.setText(Lang.t("state_preview")); badge.setBackgroundResource(R.drawable.badge_warn); }
            else { badge.setText(Lang.t("state_active")); badge.setBackgroundResource(R.drawable.badge_ok); }
            editedBadge.setText(Lang.t("state_edited"));
            editedBadge.setVisibility(edited() ? View.VISIBLE : View.GONE);
            String m = Params.metaLine(edit, previewOk ? null : previewErr);   // warnings only: the values are on the chips
            meta.setText(m);
            meta.setVisibility(m.isEmpty() ? View.GONE : View.VISIBLE);
            int[] acts = actions();
            settleActions(acts.length);
            actionsRow.setVisibility(acts.length > 0 ? View.VISIBLE : View.GONE);
            for (int i = 0; i < actionBtn.length; i++) {
                if (i >= acts.length) { actionBtn[i].setVisibility(View.GONE); continue; }
                boolean on = onActions && i == actionSel;
                actionBtn[i].setVisibility(View.VISIBLE);
                actionBtn[i].setText(CustomRecipes.editLabel(acts[i]));
                actionBtn[i].setBackgroundResource(on ? R.drawable.chip_sel : R.drawable.chip);
                actionBtn[i].setTextColor(on ? INK : WHITE);
            }
            for (int i : ORDER) {
                chip[i].setVisibility(rowVisible(i) ? View.VISIBLE : View.GONE);
                boolean sel = i == row && !onActions, ch = rowDirty(i), foc = sel && focus;
                chip[i].setBackgroundResource(foc ? R.drawable.chip_sel : sel ? R.drawable.chip_hi : R.drawable.chip);
                chipLabel[i].setText(Params.rowName(i));
                chipLabel[i].setTextColor(foc ? INK : sel ? ACCENT : DIM);
                chipValue[i].setTextColor(foc ? INK : ch ? ACCENT : WHITE);
                chipValue[i].setText(Params.fmt(i, edit[i], edit));
            }
            if (row == 0) chipScroll.post(new Runnable() { public void run() { chipScroll.smoothScrollTo(0, 0); } });
            else {
                final View c = chip[row];
                chipScroll.post(new Runnable() { public void run() {
                    int l = c.getLeft(), rgt = c.getRight(), sx = chipScroll.getScrollX(), w = chipScroll.getWidth();
                    if (l < sx) chipScroll.smoothScrollTo(l - dp(8), 0); else if (rgt > sx + w) chipScroll.smoothScrollTo(rgt - w + dp(8), 0);
                } });
            }
            hints.setVisibility(overlay == OV_QUIET ? View.GONE : View.VISIBLE);
            hints.setMode(onActions ? Keys.H_ACTIONS : row == 0 ? HintBar.RECIPE : focus ? HintBar.EDIT : HintBar.CHIPS);
        } else if (overlay == OV_PILL) {
            panel.setVisibility(View.GONE); mini.setVisibility(View.VISIBLE);
            mini.setText(Params.miniLine(r, pos, cur, edit, dirty));
        } else {
            panel.setVisibility(View.GONE); mini.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------ input
    /** change the value of the focused chip */
    private void stepValue(int dir) {
        if (row == 0) return;
        if (Params.step(edit, row, dir, recipeQuality(library.get(recipe)))) qualityChanged();
        applyPreview(); render();
    }

    /** left / right on the edit buttons, wrapping */
    private void moveAction(int dir) { int n = actions().length; if (n > 0) actionSel = (actionSel + n + dir) % n; render(); }

    /** Fn, or Browse recipes: the brand list, whose highlight previews other recipes — after asking, when that would drop edits */
    private void browse() { unlessEditsLost(new Runnable() { public void run() { openBrowser(true); } }); }

    /** MENU: out of the app — after asking, when that would drop edits */
    private void leave() { unlessEditsLost(new Runnable() { public void run() { finish(); } }); }

    /** LEFT/RIGHT inside the chip strip: next / previous visible chip, wrapping */
    private void moveChip(int dir) { row = Params.nextChip(row, dir, edit); lastChip = row; render(); }

    private void setFocus(boolean f) { focus = f && row != 0 && !onButtons(); render(); }

    /** the wheel, or left / right on the recipe line: the next recipe — after asking, when that would drop edits */
    private void nextRecipe(final int dir) {
        unlessEditsLost(new Runnable() { public void run() { recipe = library.next(recipe, dir); stageRecipe(); applyPreview(); render(); } });
    }

    /**
     * Brand column: the group above / below, its first recipe previewed. An empty Favourites or Custom list leaves the
     * recipe alone.
     */
    private void nextGroup(int dir) {
        browserGroup = Favourites.nextGroup(browserGroup, dir);
        int land = Favourites.landing(browserGroup, favs, library);
        if (land >= 0) { recipe = land; stageRecipe(); applyPreview(); }
        if (browserGroup == Favourites.CUSTOM) reportSkipped();
        render();
    }

    private void openBrowser(boolean open) {
        if (open) panelBefore = overlay;
        overlay = open ? OV_BROWSER : fullPanel(); row = 0; focus = false;
        browserGroup = Favourites.openingGroup(favs, recipe, library);
        browserCol = COL_RECIPES;
        if (open && browserGroup == Favourites.CUSTOM) reportSkipped();
        render();
    }

    /** recipe column: the next / previous recipe of the group the browser is on, wrapping */
    private void nextInGroup(int dir) {
        if (browserGroup == Favourites.CUSTOM) { int to = Favourites.nextCustom(recipe, dir, library); if (to < 0) return; recipe = to; }
        else recipe = browserGroup == Favourites.GROUP ? Favourites.next(favs, recipe, dir) : Recipes.nextInGroup(recipe, dir);
        stageRecipe(); applyPreview(); render();
    }

    /** the recipe column is not reachable while the Favourites or Custom list is empty */
    private boolean enterRecipeColumn() {
        if (!Favourites.hasRecipes(browserGroup, favs, library)) { showToast(Favourites.emptyHint(browserGroup), 3000); return false; }
        browserCol = COL_RECIPES; render(); return true;
    }

    /** the centre button on a recipe in the browser: close it, leaving that recipe previewed */
    private void pickInBrowser() {
        openBrowser(false); showToast(Lang.t("status_recipe_previewed", Recipes.displayName(library.get(recipe))), 3000);
    }

    /** the reset question answered Reset: the factory look, stored — the same store as a centre press */
    private void storeFactory() {
        overlay = fullPanel(); row = 0; focus = false;
        recipe = Recipes.FACTORY; stageRecipe(); applyPreview(); render();
        writeAll();
    }

    /** trash: full panel → pill → hidden → full; the browser is not in the cycle */
    private void cycleOverlay() { overlay = DevTools.nextPanel(overlay, +1); render(); }

    /** the full panel to come back to from the browser: without its legend if that is how it was left, else with it */
    private int fullPanel() { return overlay == OV_QUIET || (overlay == OV_BROWSER && panelBefore == OV_QUIET) ? OV_QUIET : OV_FULL; }

    /** MENU held past HOLD_MS: the app menu, or out of the key logger; its release is swallowed either way */
    private void menuHoldFired() {
        if (menuKeyHold.fire() != Keys.Hold.HOLD) return;
        swallowMenuUp = true;
        if (logging) { stopLogger(); showToast(Lang.t("status_logger_stopped", DevTools.KEY_LOG), 4000); return; }
        if (running || promptOpen || menuOpen || nameOpen) return;
        if (overlay == OV_BROWSER) { if (onCustomRow()) openPrompt(P_OPTIONS, CustomRecipes.OPT_DEFAULT); return; }
        if (menuHoldArms(overlay, focus)) openMenu(DevTools.LEVEL_APP);
    }

    /** the brand list's highlight is on a custom recipe, whose options a MENU hold opens */
    private boolean onCustomRow() { return overlay == OV_BROWSER && browserCol == COL_RECIPES && library.isCustom(recipe); }

    /** trash held past HOLD_MS: ask to reset — unless the camera says the key is already up, and its release got lost */
    private void trashHoldFired() {
        if (trash.fire() != Keys.Hold.HOLD) return;
        if (running || promptOpen || menuOpen || nameOpen) { trash.reset(); return; }
        if (!trashHoldActs(KeyProbe.isDown(trashScan))) { trash.reset(); trashPress(); return; }   // a press whose release never came
        if (overlay == OV_BROWSER) openBrowser(false);
        askReset();
    }

    /** trash pressed and released before the hold: close the brand list, or step the panel full → label → hidden → full */
    private void trashPress() {
        if (running || promptOpen || menuOpen || nameOpen) return;
        if (overlay == OV_BROWSER) openBrowser(false); else cycleOverlay();
    }

    private boolean browserKey(int sc) {
        switch (sc) {
            case K_UP: case K_WHEEL_CCW: case K_DIAL_CCW: if (browserCol == COL_GROUPS) nextGroup(-1); else nextInGroup(-1); return true;
            case K_DOWN: case K_WHEEL_CW: case K_DIAL_CW: if (browserCol == COL_GROUPS) nextGroup(+1); else nextInGroup(+1); return true;
            case K_LEFT: case K_RIGHT: if (browserCol == COL_RECIPES) { browserCol = COL_GROUPS; render(); } else enterRecipeColumn(); return true;
            case K_MENU: case K_SK1:
                // on a custom recipe MENU is press / hold: the release closes the list, a hold opens the recipe's options
                if (onCustomRow()) { if (menuKeyHold.down(0) == Keys.Hold.ARM) handler.postDelayed(menuHold, HOLD_MS); return true; }
                swallowMenuUp = true; openBrowser(false); return true;
            case K_FN: openBrowser(false); return true;
            case K_DELETE: case K_SK2: armTrash(sc); return true;       // closes the list on the release; a hold asks to reset
            case K_S1: try { camera.autoFocus(null); } catch (Throwable t) {} return true;
            case K_S2: try { camera.takePicture(null, null, null); } catch (Throwable t) {} return true;
        }
        return true;
    }

    /** handle keys before any focusable view (the chip scroller would otherwise eat LEFT/RIGHT) */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) return onKeyDown(e.getKeyCode(), e);
        if (e.getAction() == KeyEvent.ACTION_UP) return onKeyUp(e.getKeyCode(), e);
        return super.dispatchKeyEvent(e);
    }

    /** whether a hold on the centre button marks a favourite where the user is now */
    private boolean holdMarksFavourite() { return Params.holdMarksFavourite(overlay, row, browserCol); }

    /** the centre button pressed: the short action waits for the release, a hold becomes "favourite" (a custom recipe's options) */
    private void enterDown(int repeat) {
        if (enter.down(repeat) != Keys.Hold.ARM) return;          // key repeat while held
        if (holdMarksFavourite()) handler.postDelayed(enterHold, HOLD_MS);
    }

    /** the centre button released before the hold fired: what ENTER used to do on the press */
    private void enterUp() {
        handler.removeCallbacks(enterHold);
        if (enter.up() != Keys.Hold.SHORT || menuOpen) return;   // a menu opened while it was held: the release is not a pick
        switch (Params.enterAction(overlay, row, browserCol)) {
            case ENTER_BROWSER_COLUMN: enterRecipeColumn(); break;
            case ENTER_BROWSER_PICK: pickInBrowser(); break;
            case ENTER_PICK: pick(); break;
            default: {
                if (onButtons()) { int[] acts = actions(); if (actionSel < acts.length) doAction(acts[actionSel]); break; }
                setFocus(!focus); break;
            }
        }
    }

    /** trash pressed: nothing happens yet — the release hides the panel, a hold past HOLD_MS asks to reset */
    private void armTrash(int sc) {
        trashScan = sc;
        if (trash.down(0) == Keys.Hold.ARM) handler.postDelayed(trashHold, HOLD_MS);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        int sc = e.getScanCode();
        if (logging) return logKey(e, true);
        if (e.getRepeatCount() > 0 && oneShot(sc)) return true;  // held Fn / trash / MENU / centre act once
        if (isMenu(sc) && menuKeyHold.isDown()) return true;     // still the press that opened the menu: not a second press
        if (isTrash(sc) && trash.isDown()) return true;          // still the press that hid the panel
        if (running) return runKey(sc);
        if (promptOpen) return promptKey(sc);
        if (nameOpen) return nameKey(sc);
        if (menuOpen) return menuKey(sc);
        if (sc == K_ENTER) { enterDown(e.getRepeatCount()); return true; }
        if (sc == K_AEL || sc == K_C1 || sc == K_DISP) return true;   // not bound on any screen (issue #18)
        if (overlay == OV_BROWSER && sc != K_PLAY) return browserKey(sc);
        switch (sc) {
            case K_LEFT: case K_RIGHT: {
                int dir = e.getScanCode() == K_RIGHT ? +1 : -1;
                if (focus) stepValue(dir); else if (onButtons()) moveAction(dir); else if (Params.onRecipeLine(overlay, row)) nextRecipe(dir); else moveChip(dir);
                return true;
            }
            case K_WHEEL_CW: case K_WHEEL_CCW: {
                int dir = e.getScanCode() == K_WHEEL_CW ? +1 : -1;
                if (focus) stepValue(dir); else nextRecipe(dir);
                return true;
            }
            case K_DIAL_CW: case K_DIAL_CCW: {
                int dir = e.getScanCode() == K_DIAL_CW ? +1 : -1;
                if (focus) stepValue(dir); else if (onButtons()) moveAction(dir); else if (Params.onRecipeLine(overlay, row)) nextRecipe(dir); else moveChip(dir);
                return true;
            }
            case K_UP: case K_DOWN: {
                if (!panelUp(overlay)) return true;
                if (focus) stepValue(e.getScanCode() == K_UP ? +1 : -1); else moveLine(e.getScanCode() == K_DOWN ? +1 : -1);
                return true;
            }
            case K_FN: browse(); return true;
            case K_DELETE: case K_SK2: armTrash(sc); return true;       // hides on the release; a hold asks to reset
            case K_S1: try { camera.autoFocus(null); } catch (Throwable t) {} return true;
            case K_S2: try { camera.takePicture(null, null, null); } catch (Throwable t) {} return true;
            case K_MENU: case K_SK1:
                if (focus) { swallowMenuUp = true; setFocus(false); return true; }
                if (menuHoldArms(overlay, focus) && menuKeyHold.down(0) == Keys.Hold.ARM) handler.postDelayed(menuHold, HOLD_MS);
                return true;                                     // exit waits for the release, unless the hold fires first
            case K_PLAY: return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) { leave(); return true; }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        int sc = e.getScanCode();
        if (logging) return logKey(e, false);
        // the hold bookkeeping runs on every release, whatever is on screen, or a lost release would block the next press
        if (isTrash(sc)) { handler.removeCallbacks(trashHold); if (trash.up() == Keys.Hold.SHORT) trashPress(); }
        if (isMenu(sc)) { handler.removeCallbacks(menuHold); menuKeyHold.up(); }
        if (sc == K_ENTER && (promptOpen || nameOpen || running)) { handler.removeCallbacks(enterHold); enter.reset(); }
        if (promptOpen || nameOpen) { if (isMenu(sc)) swallowMenuUp = false; return true; }
        if (running) return true;                               // the release of whatever key started or stopped the run
        switch (sc) {
            case K_ENTER: enterUp(); return true;
            case K_FN: return true;
            case K_MENU: case K_SK1:
                if (swallowMenuUp) { swallowMenuUp = false; return true; }
                if (overlay == OV_BROWSER) { openBrowser(false); return true; }   // a short press on a custom recipe's row
                leave(); return true;
            case K_S1: try { camera.cancelAutoFocus(); } catch (Throwable t) {} return true;
            case K_S2: cancelCapture(); return true;
            case K_UP: case K_DOWN: case K_LEFT: case K_RIGHT: case K_PLAY: case K_DISP:
            case K_DELETE: case K_SK2: case K_C1: case K_AEL: case K_WHEEL_CW: case K_WHEEL_CCW: case K_DIAL_CW: case K_DIAL_CCW: return true;
        }
        return super.onKeyUp(keyCode, e);
    }
}
