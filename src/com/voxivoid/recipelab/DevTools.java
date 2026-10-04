package com.voxivoid.recipelab;

/**
 * The app menu (MENU hold) and the developer menu under it, and the sample run it can start: the rows each menu
 * has, the About and key-logger pages, the settle delay the run waits between applying a recipe and firing the
 * shutter, the progress lines it shows, and the manifest it writes so the frames can be matched to recipes afterwards.
 *
 * The run itself is a timed loop in MainActivity (stage a recipe → wait → shutter → wait → next); everything it
 * decides without the camera is here. No android.* import may appear in this class (tools/test.sh).
 */
final class DevTools {
    private DevTools() {}

    /** the app menu's title: the app's name, which no language translates */
    static final String APP_TITLE = "RECIPE LAB";
    static String title() { return Lang.t("dev_title"); }
    static String aboutTitle() { return Lang.t("about_title"); }

    /** the two menu levels: the app menu a MENU hold opens, and the developer menu under it */
    static final int LEVEL_APP = 0, LEVEL_DEV = 1;

    /** app menu rows, in display order */
    static final int APP_BROWSE = 0, APP_NEW = 1, APP_PANEL = 2, APP_LANG = 3, APP_RESET = 4, APP_ABOUT = 5, APP_DEV = 6, APP_ROWS = 7;

    /** the icons the menus draw beside their rows (MenuView draws them: the camera font has no symbol glyphs) */
    static final int IC_BROWSE = 0, IC_NEW = 1, IC_PANEL = 2, IC_LANGUAGE = 3, IC_RESET = 4, IC_ABOUT = 5, IC_DEV = 6,
            IC_SNAPSHOT = 7, IC_LOCK = 8, IC_SAMPLES = 9, IC_CLOCK = 10, IC_KEYS = 11;
    private static final int[] APP_ICONS = { IC_BROWSE, IC_NEW, IC_PANEL, IC_LANGUAGE, IC_RESET, IC_ABOUT, IC_DEV };
    private static final int[] DEV_ICONS = { IC_SNAPSHOT, IC_LOCK, IC_SAMPLES, IC_CLOCK, IC_KEYS };

    /** the icon of each row of a level, in row order */
    static int[] icons(int level) { return (level == LEVEL_APP ? APP_ICONS : DEV_ICONS).clone(); }

    /** developer menu rows, in display order */
    static final int ROW_SNAPSHOT = 0, ROW_LOCKS = 1, ROW_SAMPLES = 2, ROW_SETTLE = 3, ROW_KEYS = 4, ROWS = 5;

    /**
     * Settle delays to pick from, in ms: how long the preview pipeline gets after a recipe is applied before the
     * shutter fires. The right one is a property of the camera, not of this table — a frame that still carries the
     * previous look means the delay is too short, which is why the row exists instead of a constant.
     */
    static final int[] SETTLE_MS = { 800, 1200, 2000, 3000, 5000 };
    /** the delay a fresh install starts on */
    static final int SETTLE_DEFAULT = 1;
    /** what the run gives a capture before it stages the next recipe, in ms (the shutter key's press → release, automated) */
    static final int SHUTTER_MS = 2500;

    /** the frame list the run writes into the app's files dir; the frames themselves are named by the camera */
    static final String MANIFEST = "samples.txt";
    /** field separator of a manifest line; no recipe name contains it (RecipesTest) */
    static final String SEP = "|";

    // ------------------------------------------------------------ the app menu
    /** how many rows a level has */
    static int rows(int level) { return level == LEVEL_APP ? APP_ROWS : ROWS; }

    /** the row above / below on a level, wrapping */
    static int nextRow(int level, int row, int dir) { int n = rows(level); return (row + n + dir) % n; }

    /** an app menu row's title */
    static String appLabel(int row) {
        switch (row) {
            case APP_BROWSE: return Lang.t("menu_browse");
            case APP_NEW: return Lang.t("menu_new");
            case APP_PANEL: return Lang.t("menu_panel");
            case APP_LANG: return Lang.t("menu_language");
            case APP_RESET: return Lang.t("menu_reset");
            case APP_ABOUT: return Lang.t("menu_about");
            case APP_DEV: return Lang.t("menu_dev");
            default: return "?" + row;
        }
    }

    /** the line under an app menu row's title */
    static String appDetail(int row) {
        switch (row) {
            case APP_BROWSE: return Lang.t("menu_browse_detail");
            case APP_NEW: return Lang.t("menu_new_detail");
            case APP_PANEL: return Lang.t("menu_panel_detail");
            case APP_LANG: return Lang.t("menu_language_detail");
            case APP_RESET: return Lang.t("menu_reset_detail");
            case APP_ABOUT: return Lang.t("menu_about_detail");
            case APP_DEV: return Lang.t("menu_dev_detail");
            default: return "";
        }
    }

    /**
     * The value an app menu row shows at its right edge, which left / right change in place; null for a row that has
     * none. Panel visibility shows the panel state: Full, Label (the pill) or Hidden. Language shows the choice
     * ({@link Lang#choiceLabel}): Auto, or a language by its own name.
     */
    static String appValue(int row, int overlay, int langChoice) {
        switch (row) {
            case APP_PANEL: return panelLabel(overlay);
            case APP_LANG: return Lang.choiceLabel(langChoice);
            default: return null;
        }
    }

    /** a panel state as the menu names it */
    static String panelLabel(int overlay) {
        switch (overlay) {
            case Params.OV_FULL: return Lang.t("panel_full");
            case Params.OV_QUIET: return Lang.t("panel_no_keys");
            case Params.OV_PILL: return Lang.t("panel_label");
            case Params.OV_HIDDEN: return Lang.t("panel_hidden");
            default: return "?";
        }
    }

    /** the panel states in the order trash and the Panel visibility row walk them */
    static final int[] PANELS = { Params.OV_FULL, Params.OV_QUIET, Params.OV_PILL, Params.OV_HIDDEN };

    /** the panel state trash or left / right lands on: full → no keys → label → hidden, wrapping; the browser is never one of them */
    static int nextPanel(int overlay, int dir) {
        int pos = 0;                                              // from anything else — the browser — as from full
        for (int i = 0; i < PANELS.length; i++) if (PANELS[i] == overlay) pos = i;
        return PANELS[(pos + PANELS.length + dir) % PANELS.length];
    }

    /**
     * The About page: {name, value}. The version comes from the installed package at runtime, never from here. Below the
     * facts, the sponsor ask the README opens with, and where to give.
     */
    static String[][] about(String version, String model, String platform) {
        return new String[][] {
            { Lang.t("about_version"), orShown(version) },
            { Lang.t("about_camera"), orShown(model) },
            { Lang.t("about_platform"), orShown(platform) },
            { Lang.t("about_source"), "github.com/voxivoid/recipe-lab-sony-pmca" },
            { "", "" },                                             // then the ask, as the README makes it
            { "", Lang.t("about_love") },
            { Lang.t("about_sponsor"), SPONSOR_URL },                // Ko-fi first: it needs no GitHub account
            { Lang.t("about_github"), "github.com/sponsors/voxivoid" },
        };
    }

    /** the line at the top of the live view, after its drawn heart: the ask and where to give */
    static String sponsorLine() { return Lang.t("about_love") + "  " + SPONSOR_URL; }

    /** where the sponsor ask sends people: Ko-fi, which needs no account anywhere */
    static final String SPONSOR_URL = "ko-fi.com/voxivoid";

    // ------------------------------------------------------------ the reset question (hold trash, or Reset settings)
    /** the question asked before the factory look is stored: it replaces whatever the camera has now */
    static String resetTitle() { return Lang.t("reset_title"); }
    static String resetBody() { return Lang.t("reset_body"); }
    /** the answers; Cancel is the one highlighted when the question opens, so a stray centre press changes nothing */
    static String[] resetOptions() { return new String[] { Lang.t("button_reset"), Lang.t("button_cancel") }; }
    static final int RESET_DEFAULT = 1;

    /**
     * What the key probe found, as one line: "has Fn AEL C1  ·  lacks DISP  ·  unknown ZOOM_T". {@code has} lines up
     * with {@code scans}; a null entry is a key the camera would not answer for.
     */
    static String keysFound(int[] scans, Boolean[] has) {
        StringBuilder yes = new StringBuilder(), no = new StringBuilder(), unk = new StringBuilder();
        for (int i = 0; i < scans.length; i++) {
            StringBuilder b = has[i] == null ? unk : has[i] ? yes : no;
            b.append(b.length() == 0 ? "" : " ").append(Keys.name(scans[i]));
        }
        if (yes.length() == 0 && no.length() == 0) return "the camera would not say";
        StringBuilder out = new StringBuilder();
        if (yes.length() > 0) out.append("has ").append(yes);
        if (no.length() > 0) out.append(out.length() == 0 ? "" : "  ·  ").append("lacks ").append(no);
        if (unk.length() > 0) out.append(out.length() == 0 ? "" : "  ·  ").append("unknown ").append(unk);
        return out.toString();
    }

    /** the key logger's and keys.txt's "unknown", which stays English like the rest of the file */
    private static String orUnknown(String s) { return s == null || s.isEmpty() ? "unknown" : s; }
    /** the same on the About page, in the display language */
    private static String orShown(String s) { return s == null || s.isEmpty() ? Lang.t("value_unknown") : s; }

    // ------------------------------------------------------------ the key logger
    /** how many events the logger keeps on screen, newest first */
    static final int LOG_LINES = 10;
    /** the file the logger appends to in the app's files dir */
    static final String KEY_LOG = "keys.txt";

    /** the logger page title: the body it runs on */
    static String logTitle(String model, String platform) { return "KEY LOGGER  ·  " + orUnknown(model) + "  ·  " + orUnknown(platform); }

    /**
     * One key event: {"down 595", "DELETE  ·  repeat 0  ·  logic 1103"}. The scan code is what a compatibility report
     * needs; the name, the repeat count and Sony's logic code (null before platform API 3) are what make sense of it.
     */
    static String[] logLine(boolean down, int scan, int repeat, Integer logic) {
        String name = Keys.name(scan);
        return new String[] { (down ? "down " : "up   ") + scan,
                (name.isEmpty() ? "?" : name) + "  ·  repeat " + repeat + (logic == null ? "" : "  ·  logic " + logic) };
    }

    // ------------------------------------------------------------ the developer menu
    /** the row above / below, wrapping */
    static int nextRow(int row, int dir) { return nextRow(LEVEL_DEV, row, dir); }

    /** a row's title; the snapshot row and the delay row say what they will do next */
    static String rowLabel(int row, boolean snapshotTaken, int settle) {
        switch (row) {
            case ROW_SNAPSHOT: return Lang.t(snapshotTaken ? "dev_settings_diff" : "dev_settings_snapshot");
            case ROW_LOCKS: return Lang.t("dev_read_only_check", Params.allSlots().size());
            case ROW_SAMPLES: return Lang.t("dev_shoot_samples", Recipes.ALL.length);
            case ROW_SETTLE: return Lang.t("dev_settle_delay");
            case ROW_KEYS: return Lang.t("dev_key_logger");
            default: return "?" + row;
        }
    }

    /** the line under a row's title */
    static String rowDetail(int row, boolean snapshotTaken) {
        switch (row) {
            case ROW_SNAPSHOT: return Lang.t(snapshotTaken ? "dev_snapshot_compare" : "dev_snapshot_store");
            case ROW_LOCKS: return Lang.t("dev_locks_detail");
            case ROW_SAMPLES: return Lang.t("dev_samples_detail");
            case ROW_SETTLE: return Lang.t("dev_settle_detail");
            case ROW_KEYS: return Lang.t("dev_keys_detail");
            default: return "";
        }
    }

    /** the value a developer menu row shows at its right edge, which left / right change in place; null for none */
    static String rowValue(int row, int settle) { return row == ROW_SETTLE ? settleLabel(settle) : null; }

    // ------------------------------------------------------------ the settle delay
    /** a stored delay index brought back into the table */
    static int clampSettle(int idx) { return idx >= 0 && idx < SETTLE_MS.length ? idx : SETTLE_DEFAULT; }

    /** the next / previous delay, wrapping */
    static int nextSettle(int idx, int dir) { return (clampSettle(idx) + SETTLE_MS.length + dir) % SETTLE_MS.length; }

    /** a delay as the menu shows it: "1.2 s" (the number built by hand — String.format would follow the camera's locale) */
    static String settleLabel(int idx) {
        int ms = SETTLE_MS[clampSettle(idx)];
        return Lang.t("value_seconds", (ms / 1000) + "." + (ms % 1000) / 100);
    }

    // ------------------------------------------------------------ the sample run
    /** the run needs the live camera: without it nothing is applied and nothing can be shot */
    static String noPreview() { return Lang.t("dev_no_preview"); }

    /** the sticky line while the run walks the table; frames count from 1 */
    static String progress(int frame, int total, String recipeName) { return Lang.t("dev_progress", frame, total, recipeName); }

    /** the run reached the end of the table */
    static String doneMessage(int shot, int total) { return Lang.t("dev_done", shot, total, MANIFEST); }

    /** MENU during the run */
    static String stoppedMessage(int shot, int total) {
        return shot == 0 ? Lang.t("dev_stopped_none") : Lang.t("dev_stopped", shot, total, MANIFEST);
    }

    /** the camera refused a capture: the run cannot go on, and the frames so far are still listed */
    static String shootFailed(int frame, int shot, String error) { return Lang.t("dev_shoot_failed", frame, error, shot, MANIFEST); }

    // ------------------------------------------------------------ the manifest
    /**
     * The first line of a run: what the columns are, and the delay it was shot with. Appended to, so a file can
     * hold several runs and each one says how it was made.
     */
    static String manifestHeader(int total, int settleMs) {
        return "# recipe-lab samples  ·  " + total + " frames in recipe order  ·  settle " + settleMs + " ms"
                + "  ·  frame" + SEP + "recipe" + SEP + "brand" + SEP + "values";
    }

    /** one frame: its number in the run, and the recipe that was applied for it — in English whatever the menu shows */
    static String manifestLine(int frame, int recipeIndex) {
        Recipes.Recipe r = Recipes.ALL[recipeIndex];
        int was = Lang.use(Lang.EN);
        try { return pad2(frame) + SEP + r.name + SEP + Recipes.GROUPS[r.group] + SEP + r.summary(); }
        finally { Lang.use(was); }
    }

    private static String pad2(int n) { return n < 10 ? "0" + n : String.valueOf(n); }
}
