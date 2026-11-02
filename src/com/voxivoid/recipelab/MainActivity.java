package com.voxivoid.recipelab;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.voxivoid.recipelab.transfer.ArwPreview;
import com.voxivoid.recipelab.transfer.Logger;
import com.voxivoid.recipelab.transfer.TransferMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.nayuki.qrcodegen.QrCode;
import static com.voxivoid.recipelab.Keys.*;
import static com.voxivoid.recipelab.Params.*;

/**
 * Recipe Lab — film recipes with LIVE PREVIEW, then persistent write (photo + video, survives power-cycle).
 *
 * Preview = runtime camera parameters. ENTER picks the recipe: writes its bytes + sync → power-cycle applies it everywhere.
 *
 * Keys (issue #18 — every function on keys every body has; Fn is a shortcut where it exists, see {@link Keys}):
 *       wheel / dial / LEFT / RIGHT recipe, chip or value · UP / DOWN line · ENTER pick · hold ENTER favourite
 *       TRASH overlay: full → pill → hidden · hold TRASH reset (asks first) · hold MENU app menu (browse, panel,
 *       transfer to phone, language, reset, about, developer) · SHUTTER photo · MENU exit · Fn brand browser
 *       hold MENU on a custom recipe in the brand list: its options (rename, delete); a short MENU still closes the list
 *
 * Custom recipes ({@link CustomRecipes}) live on the memory card; {@link Library} gives them indexes after the table's.
 * Finishing an edit of a built-in recipe offers to keep it as a new custom recipe; an edit of a custom one is saved in
 * place. Names are typed on the name editor ({@link NameEntry}, {@link KeyboardView}).
 *
 * Transfer to phone (app menu) serves the memory card over Wi-Fi for Recipe Lab Studio on a phone: a page of the menu
 * drives {@link TransferMode}, which stops when the page or the app is left.
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
    private static final int P_QUALITY = 0, P_RESET = 1, P_DISCARD = 2, P_OPTIONS = 3, P_DELETE = 4, P_PP = 5, P_ADD = 6;
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
    // recipe QR codes: a custom recipe's (Share) or the scanner (Read Recipe QR Code), full screen while qrOpen
    private QrView qr;
    private boolean qrOpen = false, scanning = false;           // scanning: the scanner, not a recipe's code
    private boolean scanShutter = false;                       // no live view frames came: the shutter reads a photo instead
    private volatile boolean scanBusy = false;                 // a frame or a photo is being read; others are dropped meanwhile
    private int scanFrames = 0;                                // frames the live view handed in since the scanner opened
    private long notRecipeAt = 0;                              // when "not a recipe" was last said
    private int scanPolls = 0;
    private long scanShotAt;                                   // when the shutter went, for the log's timings
    private static final int SCAN_POLL_MS = 200, SCAN_POLLS = 100;   // looked for every 200 ms, for 20 s
    private java.util.Set<String> scanBefore;                  // the pictures on the card before the shutter: the new one is not among them
    private File scanPhoto; private long scanPhotoLength = -1; // the new picture, and its size when last looked at (still being written?)
    private Recipes.Recipe scanFound;                          // the recipe read, while the prompt asks to add it
    private String scanName;                                   // the name it would be added under
    // Share: the card screen (shape, Save image, Show QR code), the code full screen, and the save to playback
    private boolean shareCard = false, shareQr = false, saving = false, resumed = false;
    private int shareRow = SHARE_SAVE, cardShape = RecipeCardLayout.PORTRAIT;
    private static final int SHARE_SHAPE = 0, SHARE_SAVE = 1, SHARE_QR = 2, SHARE_ROWS = 3;
    private Recipes.Recipe shareRecipe;
    private boolean[] shareModules;
    private int shareSize;
    private final CardPainter cardPainter = new CardPainter();      // the screen's; a save draws with its own
    private final Runnable scanNoFrames = new Runnable() { public void run() { scanFallBack(); } };
    private final Runnable scanPollPhoto = new Runnable() { public void run() { scanFindPhoto(); } };
    private int menuLevel = DevTools.LEVEL_APP, menuPage = PAGE_ROWS;   // which menu, and whether it shows rows or a read-only page
    private int menuSel = 0, settleIdx = DevTools.SETTLE_DEFAULT;   // menu: highlighted row · developer menu: chosen settle delay
    private int langChoice = Lang.AUTO;                         // the Language row: Lang.AUTO or a language
    private static final int PAGE_ROWS = 0, PAGE_ABOUT = 1, PAGE_TRANSFER = 2;
    private TransferMode transfer;                              // Transfer to phone, while its page is open (null otherwise)
    private boolean transferSync;                               // the page was opened as Sync recipes: same link, its own words
    private Keys.Caps caps = Keys.Caps.UNKNOWN;                 // the shortcut keys this body reports (KeyProbe)
    private HintBar hints;
    private SponsorLine sponsor;
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
    // the shareable log on the card (CardLog): the last preview line written, so a failure repeated per key press is logged once
    private String lastPreviewLog = "";
    private String lastMode;                                   // the mode dial as last logged: a turn with the app open is logged once

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
        sponsor = (SponsorLine) findViewById(R.id.sponsor);
        mini = (TextView) findViewById(R.id.mini);
        toast = (TextView) findViewById(R.id.toast);
        prompt = (PromptView) findViewById(R.id.prompt);
        menu = (MenuView) findViewById(R.id.menu);
        keyboard = (KeyboardView) findViewById(R.id.keyboard);
        qr = (QrView) findViewById(R.id.qr);
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
        final Thread.UncaughtExceptionHandler was = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread th, Throwable t) {
                log(CardLog.CRASH, CardLog.stack(t));            // written and closed before the app goes
                if (was != null) was.uncaughtException(th, t);
            }
        });
    }

    // ------------------------------------------------------------ the shareable log on the card (RECIPLAB/LOGS)
    /** the card's root; null when no card is in or the camera will not say */
    private static File cardRoot() {
        try { return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ? Environment.getExternalStorageDirectory() : null; }
        catch (Throwable t) { return null; }
    }

    /** one line of LOG.TXT; a log that cannot be written must never take the app down with it */
    private static void log(String kind, String msg) {
        try { CardLog.append(CardLog.dir(cardRoot()), CardLog.LOG, CardLog.line(kind, msg)); } catch (Throwable t) {}
    }

    /**
     * Each opening: the body, the build and the mode dial; what the store holds, when it changed since it was last
     * logged; and every camera parameter in PARAMS.TXT, rewritten only when they changed. All of it comes from what
     * onResume has already read (load, the camera's parameters), so an opening costs one short line most of the time.
     */
    private static String androidVersion() {
        return CardLog.android(Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
    }

    private void logOpen() {
        lastMode = CardLog.param(origFlat, CardLog.MODE_KEY);
        String head = CardLog.header(KeyProbe.prop("model.name"), KeyProbe.firmware(), KeyProbe.prop("version.platform"), androidVersion(), versionName(), keysFound(), lastMode);
        log(CardLog.OPEN, head);
        String state = CardLog.state(cur);
        if (!state.equals(prefs.getString("loggedState", null))) {
            log(CardLog.STATE, state);
            prefs.edit().putString("loggedState", state).commit();
        }
        File dir = CardLog.dir(cardRoot());
        if (origFlat == null || dir == null) return;
        int hash = origFlat.hashCode();
        if (hash == prefs.getInt("paramsHash", 0) && new File(dir, CardLog.PARAMS).exists()) return;
        try {
            CardLog.replace(dir, CardLog.PARAMS, CardLog.paramsFile(CardLog.now() + "  " + head, origFlat));
            prefs.edit().putInt("paramsHash", hash).commit();
        } catch (Throwable t) {}
    }

    /**
     * Hold MENU → Save report: one file, RECIPLAB/REPORT.TXT, with everything a bug report needs — the camera as it is
     * now and the end of every log — so a user attaches one thing. Replaced on every save.
     */
    private void saveReport() {
        File root = cardRoot();
        if (root == null) { showToast(Lang.t("report_need_card"), 4000); return; }
        String flat = null;
        try { if (camera != null) flat = camera.getParameters().flatten(); } catch (Throwable t) {}
        String head = CardLog.header(KeyProbe.prop("model.name"), KeyProbe.firmware(), KeyProbe.prop("version.platform"), androidVersion(), versionName(),
                keysFound(), CardLog.param(flat, CardLog.MODE_KEY));
        try {
            CardLog.replace(CardLog.reportDir(root), CardLog.REPORT,
                    CardLog.report(CardLog.dir(root), CardLog.now(), head,
                            CardLog.app(Recipes.displayName(library.get(recipe)), !dirty(), edited(), Lang.choiceCode(langChoice)
                                    + (langChoice == Lang.AUTO ? " (" + Lang.CODES[Lang.current()] + ")" : ""),
                                    library.customNames().size(), skipped, favs.size()),
                            CardLog.state(cur), flat));
            showToast(Lang.t("report_saved", CardLog.DIR + "/" + CardLog.REPORT), 8000);   // long enough to read the path
        } catch (Throwable t) { showToast(Lang.t("report_failed", String.valueOf(t.getMessage())), 0); }
    }

    /** a preview line, once per change: the same failure on every key press would bury the rest */
    private void logPreview(String msg) {
        if (msg.equals(lastPreviewLog)) return;
        lastPreviewLog = msg;
        log(CardLog.PREVIEW, msg);
    }

    /**
     * Appends a developer tool's output to its file in the card's logs folder, where a report can attach it; without a
     * card, to {@code fallback} in the app's own storage. Returns where it went, as the toast names it.
     */
    private String debugFile(String name, String fallback, String text) throws java.io.IOException {
        try { CardLog.append(CardLog.dir(cardRoot()), name, text); return CardLog.path(name); }
        catch (java.io.IOException t) {
            java.io.FileWriter w = new java.io.FileWriter(new File(getFilesDir(), fallback), true);
            try { w.write(text); } finally { w.close(); }
            return fallback;
        }
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

    /** the edit buttons under the chips (Save, Apply, Save as new, Discard — CustomRecipes.editActions), drawn like chips */
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
        picker.setTypeface(tf); prompt.setTypeface(tf); menu.setTypeface(tf); qr.setTypeface(tf); hints.setTypeface(tf); keyboard.setTypeface(tf); sponsor.setTypeface(tf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        reloadCustoms();                                         // the card may have been edited over USB, or swapped
        int last = library.indexOfCustom(prefs.getString("customRecipe", null));
        if (last >= 0) recipe = last;
        lastPreviewLog = ""; origFlat = null;
        resumed = true;
        try {
            openCamera();
            origFlat = camera.getParameters().flatten();
            holder.addCallback(this);
            previewOk = true;
        } catch (Throwable t) { previewOk = false; previewErr = String.valueOf(t); logPreview("camera did not open: " + previewErr); }
        logOpen();
        showRecipe();                                          // with the edits last applied, when the camera still holds them
        applyPreview(); render();
        if (!prefs.getBoolean("keysNoticeSeen", false)) {               // the keys moved in this build (issue #18): say so once
            showToast(Keys.notice(), 8000);
            prefs.edit().putBoolean("keysNoticeSeen", true).commit();
        }
        if (Params.asksPictureProfileOff(cur)) openPrompt(P_PP, Params.PP_DEFAULT);   // every time: no recipe shows while it runs (#90)
    }

    /** Sony's camera, and the plain Android one inside it the app drives */
    private void openCamera() throws Exception {
        Class<?> cx = Class.forName("com.sony.scalar.hardware.CameraEx");
        Method open = cx.getMethod("open", int.class, Class.forName("com.sony.scalar.hardware.CameraEx$OpenOptions"));
        cameraEx = open.invoke(null, 0, null);
        camera = (Camera) cx.getMethod("getNormalCamera").invoke(cameraEx);
    }

    /** lets go of the camera: its decoder refuses every photo while the app holds it (Playback) */
    private void releaseCamera() {
        try { if (camera != null) camera.stopPreview(); } catch (Throwable t) {}
        try { if (cameraEx != null) cameraEx.getClass().getMethod("release").invoke(cameraEx); } catch (Throwable t) {}
        cameraEx = null; camera = null;
    }

    /** the camera back after {@link #releaseCamera}, its live view on the screen the app already has, the look applied */
    private void reopenCamera() {
        try {
            openCamera();
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            previewOk = true;
            applyPreview();
        } catch (Throwable t) { previewOk = false; previewErr = String.valueOf(t); logPreview("camera did not reopen: " + previewErr); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        stopRun(false);                                          // a run cannot outlive the camera it shoots with
        stopTransfer();                                          // leaving the app stops Transfer to phone: hotspot off, auto power-off back
        closeMenu();
        closeName();
        closeQr();
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

    @Override
    protected void onDestroy() {
        stopTransfer();                                          // onPause has normally done it; never leave Wi-Fi on behind the app
        super.onDestroy();
    }

    public void surfaceCreated(SurfaceHolder h) {
        try { camera.setPreviewDisplay(h); camera.startPreview(); }
        catch (Throwable t) { previewOk = false; previewErr = String.valueOf(t); logPreview("preview did not start: " + previewErr); render(); }
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
        } catch (Throwable t) { showToast(Lang.t("status_read_failed", String.valueOf(t.getMessage())), 0); log(CardLog.FAIL, "read: " + t); }
    }

    private void stageRecipe() {
        Recipes.Recipe r = library.get(recipe);
        Params.stage(r, edit);
        edit[R_QUAL] = recipeQuality(r);
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
    private int baseQuality() { int b = prefs.getInt("baseQuality", -1); b = b >= 0 && b < 4 ? b : cur[R_QUAL]; return b == Q_RAW ? Q_RAWJPG : b; }
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
        int[] attrs = attrsOf(ws);
        List<Integer> locked = Params.lockedFrom(ws, attrs);   // the slots backup protection would refuse (issue #19)
        ws = Params.supportedFrom(ws, attrs);                  // drop companion copies absent on older bodies (issue #84)
        String what = Recipes.displayName(library.get(recipe));
        if (!locked.isEmpty()) { log(CardLog.FAIL, what + ": read-only " + CardLog.ids(locked)); showToast(Params.lockedMessage(locked), 0); return; }   // nothing written, so nothing half-applied

        String msg = null;
        int written = 0;
        for (Params.Write w : ws) {
            try { NativeBackup.writeByte(w.id, w.value); written++; }
            catch (Throwable t) {
                msg = Params.writeFailedMessage(w.id, String.valueOf(t.getMessage()), written);
                log(CardLog.FAIL, what + ": " + CardLog.writeFailed(w.id, w.value, String.valueOf(t.getMessage()), written, ws.size()));
                break;
            }
        }
        if (written > 0) NativeBackup.sync();                    // Backup_sync_all is void: nothing to catch, nothing to report
        boolean ok = msg == null;
        if (ok) log(CardLog.WRITE, CardLog.written(what, ws));
        if (ok) msg = Lang.t(n == 1 ? "status_picked_one" : "status_picked_many", n);
        // what was written is now the look: keep it on the chips — re-staging the recipe would drop edits just applied
        // to a built-in one ("Apply only"); after a refusal, the recipe is staged again, ready to retry
        load();
        if (!ok) stageRecipe(); else { rememberReopen(); rememberAppliedEdit(); }
        showToast(msg, ok ? 5000 : 0); render();
    }

    /** "Turn off" on the Picture Profile question: writes that one byte, and leaves the staged recipe as it is */
    private void turnPictureProfileOff() {
        List<Params.Write> ws = Params.pictureProfileOff();
        String what = "Picture Profile off";
        List<Integer> locked = Params.lockedFrom(ws, attrsOf(ws));
        if (!locked.isEmpty()) { log(CardLog.FAIL, what + ": read-only " + CardLog.ids(locked)); showToast(Params.lockedMessage(locked), 0); return; }
        Params.Write w = ws.get(0);
        try { NativeBackup.writeByte(w.id, w.value); }
        catch (Throwable t) {
            log(CardLog.FAIL, what + ": " + CardLog.writeFailed(w.id, w.value, String.valueOf(t.getMessage()), 0, ws.size()));
            showToast(Params.writeFailedMessage(w.id, String.valueOf(t.getMessage()), 0), 0);
            return;
        }
        NativeBackup.sync();
        log(CardLog.WRITE, CardLog.written(what, ws));
        cur[R_PP] = 0; edit[R_PP] = 0;                           // not load(): that would put the stored look over the staged recipe
        applyPreview();
        showToast(Lang.t("status_pp_off"), 3000); render();
    }

    /**
     * The recipe the app reopens on: the last one selected (centre in the brand list) or applied (any write, New recipe),
     * whichever came last — never just the last one the wheel scrolled past. A custom one is kept by name, as its index
     * moves; the table's index stays as the fallback when its card is out.
     */
    private void rememberReopen() {
        Recipes.Recipe r = library.get(recipe);
        if (r.isCustom()) prefs.edit().putString("customRecipe", r.name).commit();
        else prefs.edit().putInt("recipe", recipe).remove("customRecipe").commit();
    }

    /**
     * After a write: when what the camera now holds is the recipe with edits (Apply), keep which recipe and which values,
     * so reopening the app can show them as they are — ACTIVE EDITED — rather than stage the recipe's own values over
     * them. Anything else written forgets it.
     */
    private void rememberAppliedEdit() {
        if (edited()) prefs.edit().putString("appliedRecipe", Favourites.key(recipe, library)).putString("appliedRows", Params.rowsText(cur)).commit();
        else prefs.edit().remove("appliedRecipe").remove("appliedRows").commit();
    }

    /**
     * When the recipe shown is the one whose edits were last applied, and the camera still holds exactly those values,
     * they replace the staged recipe — same look, so ACTIVE, and not the recipe's own, so EDITED. On opening the app and
     * on coming back to it with the wheel or the brand list; a camera changed since shows the recipe as before.
     */
    private void restoreAppliedEdit() {
        if (Params.showsAppliedEdit(prefs.getString("appliedRecipe", null), Params.rowsFrom(prefs.getString("appliedRows", null)),
                Favourites.key(recipe, library), cur)) System.arraycopy(cur, 0, edit, 0, N);
    }

    /** a recipe to look at — the wheel, the brand list, a neighbour: staged, with its applied edits when the camera holds them */
    private void showRecipe() { stageRecipe(); restoreAppliedEdit(); }

    /** selecting another recipe in the brand list lets go of the applied edits: coming back shows the recipe's own values */
    private void forgetAppliedEdit() { prefs.edit().remove("appliedRecipe").remove("appliedRows").commit(); }

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
            case P_PP: { String[] q = Params.pictureProfilePrompt(); prompt.set(q[0], q[1], Params.pictureProfileOptions(), promptSel, null); break; }
            case P_ADD: prompt.set(Lang.t("qr_add_title", scanName), scanFound.summary(), CustomRecipes.addOptions(), promptSel, null); break;
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
        int dir = sc == K_LEFT ? -1 : sc == K_RIGHT ? +1 : turn(sc);
        if (dir != 0) { promptSel = (promptSel + promptOptions() + dir) % promptOptions(); renderPrompt(); return true; }
        switch (sc) {
            case K_ENTER: closePrompt(); promptAnswered(); render(); return true;
            case K_MENU: case K_SK1:
                swallowMenuUp = true; closePrompt();
                if (promptKind == P_DISCARD) afterDiscard = null;
                if (promptKind == P_ADD) closeQr();
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
            case P_ADD: if (promptSel == CustomRecipes.ADD) addScanned(); closeQr(); return;
            case P_OPTIONS:
                if (promptSel == CustomRecipes.OPT_SHARE) openShare();
                else if (promptSel == CustomRecipes.OPT_RENAME) openName(NAME_RENAME);
                else if (promptSel == CustomRecipes.OPT_DELETE) openPrompt(P_DELETE, CustomRecipes.DELETE_DEFAULT);
                return;
            case P_DELETE: if (promptSel == 0) deleteCustom(); return;
            case P_PP: if (promptSel == Params.PP_OFF) turnPictureProfileOff(); else showToast(Lang.t("status_pp_kept"), 3000); return;
            default: if (promptSel == 0) writeAll(true); else showToast(Lang.t("status_not_picked"), 2000);   // cancel: recipe stays previewed only
        }
    }

    private void cycleQuality() {
        Params.step(edit, R_QUAL, +1, baseQuality()); qualityChanged(); applyPreview(); render();
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
            StringBuilder sb = new StringBuilder(), all = new StringBuilder(); int changed = 0;
            for (int[] e : ids) {
                int len = in.read(); byte[] old = new byte[Math.max(0, len)]; if (len > 0) in.read(old);
                byte[] now; try { now = NativeBackup.read(e[0]); } catch (Throwable t) { now = new byte[0]; }
                if (!java.util.Arrays.equals(old, now)) {
                    changed++;
                    if (changed <= 14) sb.append(Params.diffEntry(e[0], old, now));
                    all.append(Params.diffEntry(e[0], old, now));   // the file takes every change, the screen the first 14
                }
            }
            in.close(); f.delete();
            String line = CardLog.line(CardLog.INFO, changed + " changed  " + all);
            String where = debugFile(CardLog.DIFF, "diff.txt", line);
            showToast(Lang.t("status_diff_changed", changed, sb.toString()) + "  ·  " + where, 0);
        } catch (Throwable t) { showToast(Lang.t("status_snapshot_error", String.valueOf(t)), 0); }
    }

    // ------------------------------------------------------------ read-only check of the slots a recipe writes
    /**
     * Whether this body holds any slot a recipe writes read-only — the question the PROTECTED badge used to ask
     * of one unrelated slot (issue #19). Every slot of {@link Params#allSlots()} is listed with its attribute in
     * LOCKS.TXT on the card, so a compatibility report can attach it.
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
            text += "  ·  " + debugFile(CardLog.LOCKS, "locks.txt", file + "\n" + Params.lockLines(ids, attrs));
        } catch (Throwable t) { text += "  ·  " + Lang.t("status_file_failed", CardLog.LOCKS, String.valueOf(t)); }
        showToast(text, 0);
    }

    // ------------------------------------------------------------ app menu (MENU hold), developer menu under it, and the sample run
    private void openMenu(int level) {
        if (running) return;
        menuOpen = true; menuLevel = level; menuPage = PAGE_ROWS; menuSel = 0; renderMenu();
    }

    private void renderMenu() {
        if (menuPage == PAGE_ABOUT) menu.setPage(DevTools.aboutTitle(), DevTools.about(versionName(), KeyProbe.prop("model.name"),
                KeyProbe.firmware(), KeyProbe.prop("version.platform")), DevTools.aboutFooter(), Keys.hints(Keys.H_PAGE, caps));
        else if (menuPage == PAGE_TRANSFER && transfer != null) menu.setTransfer(transferSync ? DevTools.syncTitle() : DevTools.transferTitle(),
                DevTools.transferPage(transfer.state(), transfer.stats(), transfer.rate(), transferSync), Keys.hints(Keys.H_TRANSFER, caps));
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
            menu.set(app ? DevTools.APP_TITLE : DevTools.title(), labels, details, values, faces, DevTools.icons(menuLevel), menuSel, Keys.hints(legend, caps));
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
                case DevTools.APP_READ_QR: closeMenu(); openScan(); break;
                case DevTools.APP_TRANSFER: openTransfer(false); break;
                case DevTools.APP_SYNC: openTransfer(true); break;
                case DevTools.APP_PANEL: case DevTools.APP_LANG: stepMenuValue(+1); break;
                case DevTools.APP_RESET: closeMenu(); askReset(); break;
                case DevTools.APP_REPORT: closeMenu(); saveReport(); break;
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
        int dir = sc == K_UP ? -1 : sc == K_DOWN ? +1 : turn(sc);      // any wheel or dial moves rows, as in the camera's menu
        if (dir != 0) { menuSel = DevTools.nextRow(menuLevel, menuSel, dir); renderMenu(); return true; }
        switch (sc) {
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

    // ------------------------------------------------------------ Transfer to phone (app menu): a page that serves the card over Wi-Fi
    /**
     * the camera's hotspot and the server up, the page showing how it goes — refreshed by every event and once a second.
     * {@code sync} opens it as Sync recipes: the link is the same one, the page says where recipes go on the phone
     */
    private void openTransfer(boolean sync) {
        transferSync = sync;
        if (transfer == null) transfer = new TransferMode(this, transferModel(), new Logger() {
            public void info(String msg) { log(CardLog.TRANSFER, msg); }
            public void error(String msg, Throwable t) { log(CardLog.TRANSFER, msg + ": " + t); }
        }, new TransferMode.Listener() {
            public void changed() { if (menuOpen && menuPage == PAGE_TRANSFER) renderMenu(); }
        });
        menuPage = PAGE_TRANSFER;
        transfer.start();
        renderMenu();
    }

    /** server, hotspot and Wi-Fi off, auto power-off back on */
    private void stopTransfer() {
        if (transfer != null) transfer.stop();
        transfer = null;
    }

    /** the model the phone is told: Sony's model.name (ILCE-6000) — Build.MODEL is ScalarA on every body — else Build.MODEL */
    private static String transferModel() {
        String m = KeyProbe.prop("model.name");
        return m != null && !m.isEmpty() ? m : Build.MODEL;
    }

    /** keys on the Transfer to phone page: MENU stops and goes back; nothing else does anything there */
    private boolean transferKey(int sc, int repeat) {
        if (repeat > 0 || (sc != K_MENU && sc != K_SK1)) return true;
        swallowMenuUp = true; stopTransfer(); menuPage = PAGE_ROWS;
        reloadCustoms();                                         // the phone may have sent recipes
        renderMenu();
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
        try { debugFile(CardLog.KEYS, DevTools.KEY_LOG, line + "\n"); } catch (Throwable t) {}
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
        try { shoot("sample " + (runFrame + 1) + ", " + Recipes.displayName(Recipes.ALL[runFrame])); }
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
        recipe = runReturnTo; showRecipe(); applyPreview();
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

    /**
     * The shutter. Its line is written before the call, not after: a body that reboots on the shutter (issue #100)
     * leaves nothing else behind, and the last line of the log is then the frame that took it down.
     */
    private void shoot(String what) {
        log(CardLog.SHOOT, what);
        try { camera.takePicture(null, null, null); }
        catch (RuntimeException t) { log(CardLog.FAIL, "shutter: " + t); throw t; }
    }

    /** the shutter key: a frame with the recipe on screen; a refusal is logged, as it was always swallowed */
    private void shootKey() {
        try { shoot(Recipes.displayName(library.get(recipe)) + (dirty() ? " (preview)" : "")); } catch (Throwable t) {}
    }

    /** the shutter key's release, as the run and the key handler both need it */
    private void cancelCapture() { try { if (cameraEx != null) cameraEx.getClass().getMethod("cancelTakePicture").invoke(cameraEx); } catch (Throwable t) {} }

    /**
     * Between two frames of a run: a capture leaves the preview stopped on a plain Android camera, and nothing is
     * applied to a stopped preview. Whether this body needs it is a question for the camera — on one that restarts
     * the preview itself the call is a no-op, and a HAL that objects to it throws in here rather than out there.
     */
    private void resumePreview() { try { if (camera != null) camera.startPreview(); } catch (Throwable t) {} }

    // ------------------------------------------------------------ recipe QR codes: Share, and Read Recipe QR Code
    /** a custom recipe's options → Share: its code full screen, for a phone or another camera to read */
    private void openShare() {
        Recipes.Recipe r = library.get(recipe);
        String link = RecipeShare.encode(r);
        if (link == null) { showToast(Lang.t("qr_share_failed", r.name, String.valueOf(CustomRecipes.problem(r))), 0); return; }
        QrCode code = QrCode.encodeText(link, QrCode.Ecc.MEDIUM);
        boolean[] m = new boolean[code.size * code.size];
        for (int y = 0; y < code.size; y++) for (int x = 0; x < code.size; x++) m[y * code.size + x] = code.getModule(x, y);
        shareRecipe = r; shareModules = m; shareSize = code.size;
        cardShape = Math.max(0, Math.min(RecipeCardLayout.SHAPES - 1, prefs.getInt("cardShape", RecipeCardLayout.PORTRAIT)));
        qrOpen = true; scanning = false; shareCard = true; shareQr = false; shareRow = SHARE_SAVE;
        renderShareCard();
        showQr();
        log(CardLog.QR, "share " + r.name + " " + link + " (" + code.size + " modules)");
    }

    /** Share's screen: the card in the chosen shape, and the rows — shape, Save image, Show QR code */
    private void renderShareCard() {
        cardPainter.setTypeface(UiFont.of(this, Lang.current()));
        String[] rows = { Lang.t("share_shape"), Lang.t("share_save"), Lang.t("share_show_qr") };
        qr.card(cardPainter, RecipeCardLayout.of(cardShape), shareRecipe, shareModules, shareSize, rows,
                RecipeCardLayout.LABEL[cardShape], shareRow, Keys.hints(Keys.H_SHARE, caps));
    }

    /** the code alone, full screen, for someone next to the camera to scan; MENU goes back to the card */
    private void showShareQr() {
        shareCard = false; shareQr = true;
        qr.share(shareModules, shareSize, shareRecipe.name, Lang.t("qr_share_hint"), Keys.hints(Keys.H_PAGE, caps));
    }

    private void stepShape(int dir) {
        cardShape = RecipeCardLayout.next(cardShape, dir);
        prefs.edit().putInt("cardShape", cardShape).commit();
        renderShareCard();
    }

    /**
     * Save image: the card drawn full size and saved as one of the camera's photos (Playback), so it shows in playback
     * and travels as any photo does. The camera is let go meanwhile — its decoder refuses every photo while the app
     * holds it — and opened again after. A few seconds; the screen says so.
     */
    private void saveCard() {
        final Recipes.Recipe r = shareRecipe;
        final RecipeCardLayout l = RecipeCardLayout.of(cardShape);
        final boolean[] modules = shareModules;
        final int size = shareSize;
        final File dcim = new File(cardRoot(), "DCIM");
        final android.content.ContentResolver cr = getContentResolver();
        final CardPainter painter = new CardPainter();
        painter.setTypeface(UiFont.of(this, Lang.current()));
        saving = true;
        qr.cover(r.name, Lang.t("share_saving"), true, new Keys.Hints(new int[0], new int[0], new String[0]));
        releaseCamera();
        log(CardLog.QR, "save card " + r.name + " " + RecipeCardLayout.LABEL[l.shape] + " " + l.width + "x" + l.height);
        final HandlerThread thread = new HandlerThread("card-save");
        thread.start();
        final Handler worker = new Handler(thread.getLooper());
        final Playback.Log plog = new Playback.Log() { public void line(String s) { log(CardLog.QR, s); } };
        worker.post(new Runnable() {
            public void run() {
                Playback.Target t = null;
                try {
                    t = Playback.prepare(cr, dcim, l.width, l.height, plog);
                    writeCard(t, painter.bitmap(l, r, modules, size), l);
                    Playback.export(t, cr, dcim, worker, plog, new Playback.Done() {
                        public void done(String path, String why) { savedCard(thread, path, why); }
                    });
                } catch (Throwable e) {
                    Playback.discard(t);
                    String why = String.valueOf(e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e);
                    savedCard(thread, null, why.contains("no photo the camera could make") || why.contains("no card in the camera's database")
                            ? Lang.t("share_need_photo") : why);
                }
            }
        });
    }

    /** the card's pixels into the camera's image, 32 rows at a time, the bitmap let go after */
    private static void writeCard(Playback.Target t, Bitmap b, RecipeCardLayout l) {
        try {
            int w = l.width, band = 32;
            int[] px = new int[w * band];
            int handle = NativePlayback.open(t.image, t.geometry, w, l.height);
            for (int y = 0; y < l.height; y += band) {
                int n = Math.min(band, l.height - y);
                b.getPixels(px, 0, w, 0, y, w, n);
                NativePlayback.rows(handle, px, w, y, n);
            }
            String why = NativePlayback.close(handle, l.height);
            if (why != null) throw new IllegalStateException(why);
        } finally {
            b.recycle();
        }
    }

    /** the save over, on the save's thread: back on the app's, the camera opened again and the card screen shown */
    private void savedCard(final HandlerThread thread, final String path, final String why) {
        handler.post(new Runnable() {
            public void run() {
                thread.quit();
                saving = false;
                log(CardLog.QR, path != null ? "saved card as " + path : "card not saved: " + why);
                if (resumed) reopenCamera();
                if (qrOpen && shareRecipe != null) { shareCard = true; renderShareCard(); }
                showToast(path != null ? Lang.t("share_saved", new File(path).getName()) : Lang.t("share_save_failed", why), path != null ? 5000 : 0);
            }
        });
    }

    /**
     * App menu → Read Recipe QR Code: the live view in a frame, each frame read for a code. A body whose live view hands
     * no frames to the app (not known before trying: none of the app's other screens reads them) falls back to the
     * shutter after a few seconds — the photo it takes is read instead, and stays on the card.
     */
    private void openScan() {
        if (cardDir == null) { showToast(Lang.t("custom_need_card"), 4000); return; }
        if (camera == null) { showToast(Lang.t("qr_no_photo", previewErr), 0); return; }
        qrOpen = true; scanning = true; scanShutter = false; scanBusy = false; scanFrames = 0; scanFound = null;
        renderScan(Lang.t("qr_scan_live"));
        showQr();
        int[] size = previewSize();
        log(CardLog.QR, "scan: preview " + (size == null ? "size unknown" : size[0] + "x" + size[1]));
        // three ways Android hands out live view frames; which (if any) this body honours is what the log tells
        Camera.PreviewCallback cb = new Camera.PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera c) { scanFrame(data); }
        };
        try { camera.setPreviewCallback(cb); } catch (Throwable t) { log(CardLog.QR, "scan: setPreviewCallback refused: " + t); }
        try { camera.setOneShotPreviewCallback(cb); } catch (Throwable t) { log(CardLog.QR, "scan: setOneShotPreviewCallback refused: " + t); }
        try {
            camera.addCallbackBuffer(new byte[1920 * 1080 * 2]);
            camera.setPreviewCallbackWithBuffer(cb);
        } catch (Throwable t) { log(CardLog.QR, "scan: setPreviewCallbackWithBuffer refused: " + t); }
        handler.postDelayed(scanNoFrames, 4000);
    }

    private void renderScan(String status) { qr.scan(Lang.t("qr_scan_title"), status, Keys.hints(Keys.H_PAGE, caps)); }

    /** the QR screen over everything the panel shows */
    private void showQr() {
        panel.setVisibility(View.GONE); mini.setVisibility(View.GONE); picker.setVisibility(View.GONE); sponsor.setVisibility(View.GONE);
        qr.setVisibility(View.VISIBLE);
    }

    private void closeQr() {
        if (!qrOpen) return;
        qrOpen = false; scanning = false; shareCard = false; shareQr = false;
        handler.removeCallbacks(scanNoFrames); handler.removeCallbacks(scanPollPhoto);
        try { if (camera != null) camera.setPreviewCallback(null); } catch (Throwable t) {}
        qr.setVisibility(View.GONE);
        render();
    }

    /** the live view's size, {width, height}; null when the camera will not say (the A6000 will not) */
    private int[] previewSize() {
        try { Camera.Size s = camera.getParameters().getPreviewSize(); return s == null ? null : new int[] { s.width, s.height }; }
        catch (Throwable t) { return null; }
    }

    /**
     * A live view frame: read on a thread of its own, one at a time — the frames that come meanwhile are dropped. A frame
     * whose size the camera will not say cannot be read: it is logged once, and the shutter takes over as if none came.
     */
    private void scanFrame(final byte[] data) {
        if (!scanning || scanShutter || promptOpen || data == null) return;
        final int[] size = previewSize();
        if (scanFrames++ == 0) log(CardLog.QR, "scan: first live view frame, " + data.length + " bytes" + (size == null ? ", size unknown" : ""));
        try { camera.addCallbackBuffer(data); } catch (Throwable t) {}   // the buffer back, for the next one
        if (size == null) return;
        handler.removeCallbacks(scanNoFrames);
        if (scanBusy) return;
        final int w = size[0], h = size[1];
        scanBusy = true;
        new Thread(new Runnable() {
            public void run() {
                final String text = QrScan.fromLuminance(data, w, h);
                handler.post(new Runnable() { public void run() { scanBusy = false; scanRead(text, false); } });
            }
        }, "qr-frame").start();
    }

    /** no frame that can be read came: the live view does not hand them out on this body, so the shutter takes over */
    private void scanFallBack() {
        if (!scanning) return;
        scanShutter = true;
        log(CardLog.QR, "scan: " + scanFrames + " live view frames in 4 s, none readable: reading photos instead");
        renderScan(Lang.t("qr_scan_shutter"));
    }

    /** keys while a QR screen is up: MENU closes it; on the scanner the shutter reads a photo, a half-press focuses */
    private boolean qrKey(int sc) {
        if (saving) { keepCover(sc); return true; }               // the camera is let go while a card is saved: it must finish
        if (scanning && scanShutter && scanBusy) {               // a photo being read: MENU still leaves, the rest waits
            if (sc == K_MENU || sc == K_SK1) { swallowMenuUp = true; log(CardLog.QR, "scan: left while reading"); closeQr(); }
            else keepCover(sc);
            return true;
        }
        if (shareCard) return shareKey(sc);
        switch (sc) {
            case K_MENU: case K_SK1:
                swallowMenuUp = true;
                if (shareQr) { shareQr = false; shareCard = true; renderShareCard(); } else closeQr();   // the code: back to the card
                return true;
            case K_ENTER: if (shareQr) { shareQr = false; shareCard = true; renderShareCard(); } else if (!scanning) closeQr(); return true;
            case K_S1: if (scanning) try { camera.autoFocus(null); } catch (Throwable t) { logPreview("autofocus refused: " + t); } return true;
            case K_S2: if (scanning && !scanBusy) scanShoot(); return true;
            default: return true;
        }
    }

    /**
     * A key while a card is saved or a photo read: nothing to do, but the black screen and its ring stay up. Seen once on
     * an A6000, not again after a restart: a key took the ring away while the work went on. Logged, with what the screen
     * was, so another time says why.
     */
    private void keepCover(int sc) {
        log(CardLog.QR, "key " + sc + " while busy: screen " + (qr.isShown() ? "shown" : "hidden")
                + ", window " + (hasWindowFocus() ? "focused" : "not focused"));
        panel.setVisibility(View.GONE); mini.setVisibility(View.GONE); picker.setVisibility(View.GONE); sponsor.setVisibility(View.GONE);
        qr.setVisibility(View.VISIBLE);
        qr.bringToFront();
        qr.invalidate();
    }

    /** keys on Share's card screen: up / down choose a row, left / right (and the dials) the shape, centre does the row */
    private boolean shareKey(int sc) {
        int t = turn(sc);
        if (t != 0) { stepShape(t); return true; }
        switch (sc) {
            case K_UP: case K_DOWN:
                shareRow = (shareRow + SHARE_ROWS + (sc == K_DOWN ? 1 : -1)) % SHARE_ROWS; renderShareCard(); return true;
            case K_LEFT: stepShape(-1); return true;
            case K_RIGHT: stepShape(+1); return true;
            case K_ENTER:
                if (shareRow == SHARE_SHAPE) stepShape(+1);
                else if (shareRow == SHARE_SAVE) saveCard();
                else showShareQr();
                return true;
            case K_MENU: case K_SK1: swallowMenuUp = true; closeQr(); return true;
            default: return true;
        }
    }

    /** the shutter on the scanner: a photo, then the newest picture on the card is read once the camera has written it */
    private void scanShoot() {
        scanPolls = 0; scanBusy = true; scanPhoto = null; scanPhotoLength = -1; scanShotAt = SystemClock.uptimeMillis();
        // the card's clock is not the app's (files read an hour off on an A6000), so the new picture is the one not here now
        scanBefore = photos(new File(cardRoot(), "DCIM"));
        qr.cover(Lang.t("qr_scan_title"), Lang.t("qr_scan_reading"), true, Keys.hints(Keys.H_PAGE, caps));   // the live view off, the ring turning
        try { shoot("read recipe QR code"); }
        catch (Throwable t) { scanBusy = false; renderScan(Lang.t("qr_no_photo", String.valueOf(t))); return; }
        handler.postDelayed(scanPollPhoto, SCAN_POLL_MS);
    }

    /**
     * The photo the shutter took: a JPG (else an ARW) in DCIM that was not there before, once its size has stopped
     * changing — the camera may still be writing it. Looked for every {@link #SCAN_POLL_MS} for a while.
     */
    private void scanFindPhoto() {
        if (!scanning) { scanBusy = false; return; }
        if (scanPhoto == null) scanPhoto = newPhoto(new File(cardRoot(), "DCIM"), scanBefore);
        long length = scanPhoto == null ? -1 : scanPhoto.length();
        if (scanPhoto == null || length <= 0 || length != scanPhotoLength) {
            scanPhotoLength = length;
            if (++scanPolls < SCAN_POLLS) { handler.postDelayed(scanPollPhoto, SCAN_POLL_MS); return; }
            scanBusy = false; resumePreview(); renderScan(Lang.t("qr_no_photo", "no new picture in DCIM"));
            log(CardLog.QR, "scan: no new picture in DCIM");
            return;
        }
        resumePreview();
        final File photo = scanPhoto;
        final long found = SystemClock.uptimeMillis();
        log(CardLog.QR, "scan: reading " + photo.getPath() + " (" + photo.length() + " bytes), written " + (found - scanShotAt) + " ms after the shutter");
        new Thread(new Runnable() {
            public void run() {
                String text = null, failed = null;
                try { text = readPhoto(photo); }
                catch (Throwable t) { failed = String.valueOf(t); }
                final String read = text, why = failed;
                final long took = SystemClock.uptimeMillis() - found;
                handler.post(new Runnable() {                    // the answer first: deleting can wait behind it
                    public void run() {
                        scanBusy = false;
                        log(CardLog.QR, "scan: read in " + took + " ms, " + (SystemClock.uptimeMillis() - scanShotAt) + " ms after the shutter");
                        if (why != null) { log(CardLog.QR, "scan: photo unreadable: " + why); renderScan(Lang.t("qr_no_photo", why)); return; }
                        scanRead(read, true);
                    }
                });
                deleteScanPhotos();                              // read or not: the picture was only ever a way to see the code
            }
        }, "qr-photo").start();
    }

    /** every .JPG and .ARW in the folders under {@code dcim}, by path */
    private static java.util.Set<String> photos(File dcim) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        File[] dirs = dcim.listFiles();
        if (dirs == null) return out;
        for (File dir : dirs) {
            File[] files = dir.isDirectory() ? dir.listFiles() : null;
            if (files == null) continue;
            for (File f : files) {
                String n = f.getName().toUpperCase(java.util.Locale.US);
                if (n.endsWith(".JPG") || n.endsWith(".ARW")) out.add(f.getPath());
            }
        }
        return out;
    }

    /** a picture under {@code dcim} not in {@code before}: the JPG when the shot wrote one, else the ARW; null when none */
    private static File newPhoto(File dcim, java.util.Set<String> before) {
        String p = QrScan.toRead(QrScan.added(before, photos(dcim)));
        return p == null ? null : new File(p);
    }

    /**
     * Deletes what the scanner's shutter added to the card — the JPG and the ARW of the shot — so reading codes leaves no
     * pictures behind. Only files that were not there before the press; each deletion is logged. Through the camera's
     * own delete first ({@link Playback#deleteShot}): a file deleted behind its back stays in playback as a picture it
     * cannot display. What that leaves is deleted as a file. The camera's delete may never return (it hung on an A6000
     * with the camera open): it gets {@link #DB_DELETE_MS}, and once it has not answered it is not asked again until the
     * app starts over.
     */
    private void deleteScanPhotos() {
        java.util.Set<String> before = scanBefore;
        for (String p : QrScan.added(before, photos(new File(cardRoot(), "DCIM")))) {
            File f = new File(p);
            if (!f.exists()) continue;                             // the camera's delete took it with its pair
            long size = f.length();
            if (!dbDeleteStuck) {
                String why = dbDelete(p);
                log(CardLog.QR, "scan: " + p + ": " + why);
                if (!f.exists()) { log(CardLog.QR, "scan: deleted " + p + " (" + size + " bytes) from playback"); continue; }
            }
            log(CardLog.QR, f.delete() ? "scan: deleted " + p + " (" + size + " bytes)" : "scan: could not delete " + p);
        }
    }

    private static final long DB_DELETE_MS = 3000;
    private static volatile boolean dbDeleteStuck = false;       // the camera's delete did not answer: files only from then on

    /** {@link Playback#deleteShot} on a thread of its own, waited for {@link #DB_DELETE_MS} at most */
    private String dbDelete(final String path) {
        final String[] out = new String[1];
        Thread t = new Thread(new Runnable() {
            public void run() { String r = Playback.deleteShot(getContentResolver(), path, new boolean[1]); synchronized (out) { out[0] = r; } }
        }, "qr-delete");
        t.setDaemon(true);
        t.start();
        try { t.join(DB_DELETE_MS); } catch (InterruptedException e) {}
        synchronized (out) {
            if (out[0] != null) return out[0];
        }
        dbDeleteStuck = true;
        return "the camera's delete did not answer in " + DB_DELETE_MS + " ms";
    }

    /**
     * A code in a photo: a JPEG scaled down, or the JPEG an ARW carries inside. Small first ({@link QrScan#QUICK} pixels
     * across: a code that fills a fair part of the frame reads there, four times faster), then larger
     * ({@link QrScan#FULL}) only when the small one held none.
     */
    private static String readPhoto(File photo) throws Exception {
        String text = readPhoto(photo, QrScan.QUICK);
        return text != null ? text : readPhoto(photo, QrScan.FULL);
    }

    private static String readPhoto(File photo, int across) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b;
        if (photo.getName().toUpperCase(java.util.Locale.US).endsWith(".ARW")) {
            ArwPreview.Jpeg j = ArwPreview.locate(photo).pick(false);
            if (j == null) throw new Exception("no preview in the ARW");
            byte[] bytes = new byte[(int) j.length];
            RandomAccessFile raf = new RandomAccessFile(photo, "r");
            try { raf.seek(j.offset); raf.readFully(bytes); } finally { raf.close(); }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            o.inSampleSize = QrScan.sample(Math.max(bounds.outWidth, bounds.outHeight), across);
            b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        } else {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photo.getPath(), bounds);
            o.inSampleSize = QrScan.sample(Math.max(bounds.outWidth, bounds.outHeight), across);
            b = BitmapFactory.decodeFile(photo.getPath(), o);
        }
        if (b == null) throw new Exception("the picture did not decode");
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        b.recycle();
        return QrScan.fromPixels(px, w, h);
    }

    /** what a frame or a photo held: a recipe is offered, anything else said and the scanner goes on */
    private void scanRead(String text, boolean fromPhoto) {
        if (!scanning || promptOpen) return;
        if (text == null) {
            if (fromPhoto) { renderScan(Lang.t("qr_no_code")); log(CardLog.QR, "scan: no code in the photo"); }
            return;                                              // a frame without a code: the next one is read
        }
        log(CardLog.QR, "scan: read " + text);
        switch (QrScan.kind(text)) {
            case QrScan.NOT_RECIPE: {
                long now = System.currentTimeMillis();
                if (fromPhoto || now - notRecipeAt > 4000) { notRecipeAt = now; showToast(Lang.t("qr_not_recipe"), 3000); }
                if (fromPhoto) renderScan(Lang.t("qr_scan_shutter"));
                return;
            }
            case QrScan.UNREADABLE: showToast(Lang.t("qr_unreadable"), 6000); closeQr(); return;
        }
        Recipes.Recipe r = RecipeShare.decode(text);
        List<CustomRecipes.Entry> have = new ArrayList<CustomRecipes.Entry>();
        for (int i = 0; i < library.customCount(); i++) have.add(library.entry(Library.BASE + i));
        String name = CustomRecipes.sharedName(r, have);
        if (name == null) { showToast(Lang.t("qr_already", r.name), 4000); closeQr(); return; }
        scanFound = r; scanName = name;
        qr.cover(Lang.t("qr_scan_title"), "", false, Keys.hints(Keys.H_PAGE, caps));   // read: the ring stops, the question is asked over black
        openPrompt(P_ADD, CustomRecipes.ADD_DEFAULT);
    }

    /** the add question answered Add: the recipe is written to the card and highlighted */
    private void addScanned() {
        Recipes.Recipe r = CustomRecipes.renamed(scanFound, scanName);
        CustomRecipes.Entry e;
        try { e = CustomRecipes.save(cardDir, r, madeOn(), null); }
        catch (Throwable t) { log(CardLog.FAIL, "save " + r.name + ": " + t); showToast(Lang.t("custom_save_failed", String.valueOf(t.getMessage())), 0); return; }
        log(CardLog.QR, "added " + r.name + " as " + e.file);
        reloadCustoms();
        follow(r.name);
        stageRecipe(); applyPreview();
        showToast(Lang.t("qr_added", r.name), 4000);
    }

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
            else { recipe = next; showRecipe(); applyPreview(); }
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
        if (turn(sc) != 0) { nameEntry.step(turn(sc)); renderName(); return true; }
        switch (sc) {
            case K_UP: nameEntry.move(-1, 0); break;
            case K_DOWN: nameEntry.move(+1, 0); break;
            case K_LEFT: nameEntry.move(0, -1); break;
            case K_RIGHT: nameEntry.move(0, +1); break;
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
            case NAME_NEW: if (saveNew(name, cur)) rememberReopen(); break;   // the camera's own look: the one it holds
            case NAME_COPY: focus = false; if (saveNew(name, edit)) writeAll(); break;   // the copy is the look now: stored too
            case NAME_RENAME: renameCustom(name); break;
        }
        render();
    }

    /** keeps {@code rows} as a new custom recipe and highlights it; false when the card refused */
    private boolean saveNew(String name, int[] rows) {
        CustomRecipes.Entry e;
        try { e = CustomRecipes.save(cardDir, CustomRecipes.recipe(name, rows), madeOn(), null); }
        catch (Throwable t) { log(CardLog.FAIL, "save " + name + ": " + t); showToast(Lang.t("custom_save_failed", String.valueOf(t.getMessage())), 0); return false; }
        reloadCustoms();
        follow(name);
        stageRecipe(); applyPreview();
        showToast(Lang.t("custom_created", name, CustomRecipes.path(e.file)), 5000);
        return true;
    }

    /** an edit of a custom recipe finished: its file takes the new values */
    private boolean saveInPlace() {
        CustomRecipes.Entry e = library.entry(recipe);
        if (cardDir == null) { showToast(Lang.t("custom_need_card"), 4000); return false; }
        try { CustomRecipes.save(cardDir, CustomRecipes.recipe(e.recipe.name, edit), madeOn(), e.file); }
        catch (Throwable t) { log(CardLog.FAIL, "save " + e.recipe.name + ": " + t); showToast(Lang.t("custom_save_failed", String.valueOf(t.getMessage())), 0); return false; }
        reloadCustoms();
        showToast(Lang.t("custom_saved", e.recipe.name), 2500);
        return true;
    }

    private void renameCustom(String name) {
        CustomRecipes.Entry e = library.entry(recipe);
        if (name.equals(e.recipe.name)) return;
        try { CustomRecipes.save(cardDir, CustomRecipes.renamed(e.recipe, name), madeOn(), e.file); }
        catch (Throwable t) { log(CardLog.FAIL, "save " + name + ": " + t); showToast(Lang.t("custom_save_failed", String.valueOf(t.getMessage())), 0); return; }
        favStored = Favourites.renameCustom(favStored, e.recipe.name, name);
        prefs.edit().putString("favourites", favStored).commit();
        if (e.recipe.name.equalsIgnoreCase(prefs.getString("customRecipe", ""))) prefs.edit().putString("customRecipe", name).commit();
        if (Favourites.key(recipe, library).equals(prefs.getString("appliedRecipe", null)))
            prefs.edit().putString("appliedRecipe", Favourites.CUSTOM_PREFIX + name).commit();   // its applied edit moves with it
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
        catch (Throwable t) { log(CardLog.FAIL, "delete " + e.recipe.name + ": " + t); showToast(Lang.t("custom_delete_failed", e.recipe.name, String.valueOf(t.getMessage())), 0); return; }
        favStored = Favourites.forgetCustom(favStored, e.recipe.name);
        prefs.edit().putString("favourites", favStored).commit();
        reloadCustoms();                                         // the recipe is gone: the first one stands in
        if (overlay == OV_BROWSER && browserGroup == Favourites.GROUP) {
            int next = Favourites.afterRemoval(favs, favPos);
            if (next < 0) browserCol = COL_GROUPS; else recipe = next;
        } else if (library.customCount() > 0) recipe = Library.BASE + Math.min(k, library.customCount() - 1);
        else if (overlay == OV_BROWSER && browserGroup == Favourites.CUSTOM) browserCol = COL_GROUPS;   // nothing left to highlight
        showRecipe(); applyPreview();
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
            case CustomRecipes.EDIT_SAVE: if (saveInPlace() && dirty()) writeAll(); break;   // custom only; stored too, unless the camera has it already
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
            Map<String, String> wanted = Params.preview(edit);
            for (Map.Entry<String, String> e : wanted.entrySet()) p.set(e.getKey(), e.getValue());
            camera.setParameters(p);
            previewOk = true;
            Camera.Parameters back = camera.getParameters();     // what the camera kept: a dropped value throws nothing (#104)
            Map<String, String> got = new java.util.HashMap<String, String>();
            for (String k : wanted.keySet()) got.put(k, back.get(k));
            String dropped = CardLog.previewDropped(wanted, got);
            if (dropped.length() > 0) logPreview(Recipes.displayName(library.get(recipe)) + ": not kept  " + dropped);
            String mode = back.get(CardLog.MODE_KEY);              // read anyway: a dial turned with the app open costs nothing more
            if (mode != null && !mode.equals(lastMode)) { log(CardLog.INFO, "mode " + lastMode + " > " + mode); lastMode = mode; }
        } catch (Throwable t) { previewOk = false; previewErr = String.valueOf(t.getMessage()); logPreview("parameters refused: " + previewErr); }
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
        sponsor.setText(DevTools.sponsorLine());                     // the README's ask, small, over the live view
        sponsor.setVisibility(panelUp(overlay) ? View.VISIBLE : View.GONE);   // Full and No keys; Label and Hidden hide it
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
        unlessEditsLost(new Runnable() { public void run() { recipe = library.next(recipe, dir); showRecipe(); applyPreview(); render(); } });
    }

    /**
     * Brand column: the group above / below, its first recipe previewed. An empty Favourites or Custom list leaves the
     * recipe alone.
     */
    private void nextGroup(int dir) {
        browserGroup = Favourites.nextGroup(browserGroup, dir);
        int land = Favourites.landing(browserGroup, favs, library);
        if (land >= 0) { recipe = land; showRecipe(); applyPreview(); }
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
        showRecipe(); applyPreview(); render();
    }

    /** the recipe column is not reachable while the Favourites or Custom list is empty */
    private boolean enterRecipeColumn() {
        if (!Favourites.hasRecipes(browserGroup, favs, library)) { showToast(Favourites.emptyHint(browserGroup), 3000); return false; }
        browserCol = COL_RECIPES; render(); return true;
    }

    /** the centre button on a recipe in the browser: close it, leaving that recipe previewed */
    private void pickInBrowser() {
        if (!Favourites.key(recipe, library).equals(prefs.getString("appliedRecipe", null))) forgetAppliedEdit();   // a new one chosen
        rememberReopen();                                        // a recipe chosen in the list is where the app reopens
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
        if (logging) { stopLogger(); showToast(Lang.t("status_logger_stopped", cardRoot() != null ? CardLog.path(CardLog.KEYS) : DevTools.KEY_LOG), 4000); return; }
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
        int dir = sc == K_UP ? -1 : sc == K_DOWN ? +1 : turn(sc);
        if (dir != 0) { if (browserCol == COL_GROUPS) nextGroup(dir); else nextInGroup(dir); return true; }
        switch (sc) {
            case K_LEFT: case K_RIGHT: if (browserCol == COL_RECIPES) { browserCol = COL_GROUPS; render(); } else enterRecipeColumn(); return true;
            case K_MENU: case K_SK1:
                // on a custom recipe MENU is press / hold: the release closes the list, a hold opens the recipe's options
                if (onCustomRow()) { if (menuKeyHold.down(0) == Keys.Hold.ARM) handler.postDelayed(menuHold, HOLD_MS); return true; }
                swallowMenuUp = true; openBrowser(false); return true;
            case K_FN: openBrowser(false); return true;
            case K_DELETE: case K_SK2: armTrash(sc); return true;       // closes the list on the release; a hold asks to reset
            case K_S1: try { camera.autoFocus(null); } catch (Throwable t) { logPreview("autofocus refused: " + t); } return true;
            case K_S2: shootKey(); return true;
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
        if (qrOpen) return qrKey(sc);
        if (menuOpen && menuPage == PAGE_TRANSFER && transfer != null) return transferKey(sc, e.getRepeatCount());
        if (menuOpen) return menuKey(sc);
        if (sc == K_ENTER) { enterDown(e.getRepeatCount()); return true; }
        if (sc == K_AEL || sc == K_C1 || sc == K_DISP) return true;   // not bound on any screen (issue #18)
        if (overlay == OV_BROWSER && sc != K_PLAY) return browserKey(sc);
        int step = turn(sc);
        if (step != 0) {                                         // any wheel or dial: along the line the highlight is on
            if (focus) stepValue(step); else if (onButtons()) moveAction(step); else if (Params.onRecipeLine(overlay, row)) nextRecipe(step); else moveChip(step);
            return true;
        }
        switch (sc) {
            case K_LEFT: case K_RIGHT: {
                int dir = e.getScanCode() == K_RIGHT ? +1 : -1;
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
            case K_S1: try { camera.autoFocus(null); } catch (Throwable t) { logPreview("autofocus refused: " + t); } return true;
            case K_S2: shootKey(); return true;
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
        if (qrOpen) {                                            // the shutter and focus releases; nothing else leaves the screen
            if (sc == K_S2) cancelCapture();
            if (sc == K_S1) try { camera.cancelAutoFocus(); } catch (Throwable t) {}
            return true;
        }
        if (running) return true;                               // the release of whatever key started or stopped the run
        if (turn(sc) != 0) return true;
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
            case K_DELETE: case K_SK2: case K_C1: case K_AEL: return true;
        }
        return super.onKeyUp(keyCode, e);
    }
}
