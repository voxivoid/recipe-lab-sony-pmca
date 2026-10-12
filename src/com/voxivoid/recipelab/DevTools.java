package com.voxivoid.recipelab;

import com.voxivoid.recipelab.transfer.Protocol;
import com.voxivoid.recipelab.transfer.Stats;
import com.voxivoid.recipelab.transfer.TransferState;

/**
 * The app menu (MENU hold) and the developer menu under it, and the sample run it can start: the rows each menu
 * has, the About, Transfer to phone and key-logger pages, the settle delay the run waits between applying a recipe and firing the
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
    static final int APP_BROWSE = 0, APP_NEW = 1, APP_TRANSFER = 2, APP_PANEL = 3, APP_LANG = 4, APP_RESET = 5, APP_REPORT = 6,
            APP_ABOUT = 7, APP_DEV = 8, APP_ROWS = 9;

    /** the icons the menus draw beside their rows (MenuView draws them: the camera font has no symbol glyphs) */
    static final int IC_BROWSE = 0, IC_NEW = 1, IC_PANEL = 2, IC_LANGUAGE = 3, IC_RESET = 4, IC_ABOUT = 5, IC_DEV = 6,
            IC_SNAPSHOT = 7, IC_LOCK = 8, IC_SAMPLES = 9, IC_CLOCK = 10, IC_KEYS = 11, IC_REPORT = 12, IC_TRANSFER = 13;
    private static final int[] APP_ICONS = { IC_BROWSE, IC_NEW, IC_TRANSFER, IC_PANEL, IC_LANGUAGE, IC_RESET, IC_REPORT, IC_ABOUT, IC_DEV };
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
            case APP_TRANSFER: return Lang.t("menu_transfer");
            case APP_PANEL: return Lang.t("menu_panel");
            case APP_LANG: return Lang.t("menu_language");
            case APP_RESET: return Lang.t("menu_reset");
            case APP_REPORT: return Lang.t("menu_report");
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
            case APP_TRANSFER: return Lang.t("menu_transfer_detail");
            case APP_PANEL: return Lang.t("menu_panel_detail");
            case APP_LANG: return Lang.t("menu_language_detail");
            case APP_RESET: return Lang.t("menu_reset_detail");
            case APP_REPORT: return Lang.t("menu_report_detail");
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
     * The About page: {name, value}. The version comes from the installed package at runtime, never from here. The sponsor
     * ask goes under it ({@link #aboutFooter}).
     */
    static String[][] about(String version, String model, String firmware, String platform) {
        return new String[][] {
            { Lang.t("about_version"), orShown(version) },
            { Lang.t("about_camera"), orShown(model) },
            { Lang.t("about_firmware"), orShown(firmware) },
            { Lang.t("about_platform"), orShown(platform) },
            { Lang.t("about_source"), "github.com/voxivoid/recipe-lab-sony-pmca" },
        };
    }

    /**
     * The About page's footer, centred under the facts: the sponsor ask (drawn after a heart), then where to give —
     * Ko-fi first, as it needs no GitHub account.
     */
    static String[] aboutFooter() {
        return new String[] { Lang.t("about_love"), SPONSOR_URL, "github.com/sponsors/voxivoid" };
    }

    /** the line at the top of the live view, after its drawn heart: the ask and where to give */
    static String sponsorLine() { return Lang.t("about_love") + "  " + SPONSOR_URL; }

    /** where the sponsor ask sends people: Ko-fi, which needs no account anywhere */
    static final String SPONSOR_URL = "ko-fi.com/voxivoid";

    // ------------------------------------------------------------ Transfer to phone
    static String transferTitle() { return Lang.t("transfer_title"); }

    /**
     * What the Transfer to phone page shows (MenuView draws it): a status chip, the hotspot's network name and password
     * large in a card, one line of what to do, and a footer with the address, the last request and the totals.
     */
    static final class TransferPage {
        /** the chip's colour: neutral while starting, green when the phone can join, red when something is wrong */
        static final int STARTING = 0, READY = 1, ERROR = 2;
        int tone;
        String chip, networkLabel, network, passwordLabel, password;
        /** what went wrong, said under the card; null when nothing did */
        String problem;
        /** what to do now: join and import, or how to try again */
        String instruction;
        /** footer: the address, the last request (or a hotspot note, or waiting), the totals */
        String address, last, totals;
    }

    /** The page for {@code s}; {@code rate} is bytes per second. A server problem outranks a working hotspot. */
    static TransferPage transferPage(TransferState s, Stats stats, double rate) {
        TransferPage p = new TransferPage();
        p.problem = transferProblem(s);
        p.tone = p.problem != null ? TransferPage.ERROR : s.ready() ? TransferPage.READY : TransferPage.STARTING;
        p.chip = Lang.t(p.tone == TransferPage.ERROR ? "transfer_chip_error" : p.tone == TransferPage.READY ? "transfer_chip_ready" : "transfer_chip_starting");
        p.networkLabel = Lang.t("transfer_network");
        p.passwordLabel = Lang.t("transfer_password");
        p.network = s.ready() && s.ssid() != null ? s.ssid() : "—";
        p.password = s.ready() && s.password() != null ? s.password() : "—";
        p.instruction = Lang.t(s.failed() ? "transfer_hint_retry" : "transfer_instruction");
        p.address = TransferState.address();
        String last = stats.lastRequest();
        if (last.isEmpty()) last = Lang.t(s.note() == TransferState.NOTE_PHONE_JOINED ? "transfer_phone_joined"
                : s.note() == TransferState.NOTE_PHONE_LEFT ? "transfer_phone_left" : "transfer_waiting");
        p.last = last;
        p.totals = Lang.t(stats.active() > 0 ? "transfer_totals_active" : "transfer_totals",
                stats.requests(), Stats.megabytes(stats.bytesSent()), Stats.rate(rate), stats.active());
        return p;
    }

    /** what stops the transfer, in the display language: the hotspot's failure first, then the server's; null when none */
    static String transferProblem(TransferState s) {
        String d = s.detail();
        switch (s.status()) {
            case TransferState.UNAVAILABLE: return Lang.t("transfer_hotspot_unavailable");
            case TransferState.REFUSED: return Lang.t("transfer_hotspot_refused");
            case TransferState.NO_CONFIG: return Lang.t("transfer_hotspot_no_config");
            case TransferState.ERROR: return Lang.t("transfer_hotspot_error", orShown(d));
            case TransferState.FAILED: return Lang.t("transfer_hotspot_failed", orShown(d));
            case TransferState.TIMEOUT: return Lang.t("transfer_hotspot_timeout");
            default: break;
        }
        if (s.server() == TransferState.SERVER_NO_CARD) return Lang.t("transfer_no_card", orShown(s.serverDetail()));
        if (s.server() == TransferState.SERVER_PORT) return Lang.t("transfer_port_failed", String.valueOf(Protocol.HTTP_PORT), orShown(s.serverDetail()));
        return null;
    }

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
