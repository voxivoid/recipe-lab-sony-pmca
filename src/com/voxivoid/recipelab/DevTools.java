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

    static final String APP_TITLE = "RECIPE LAB", TITLE = "DEV TOOLS", CONTROLS_TITLE = "CONTROLS", ABOUT_TITLE = "ABOUT";

    /** the two menu levels: the app menu a MENU hold opens, and the developer menu under it */
    static final int LEVEL_APP = 0, LEVEL_DEV = 1;

    /** app menu rows, in display order */
    static final int APP_BROWSE = 0, APP_PANEL = 1, APP_FACTORY = 2, APP_CONTROLS = 3, APP_ABOUT = 4, APP_DEV = 5, APP_ROWS = 6;

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

    /** an app menu row's title; the panel row says which way it will go from where the panel is now */
    static String appLabel(int row, int overlay) {
        switch (row) {
            case APP_BROWSE: return "Browse recipes";
            case APP_PANEL: return overlay == Params.OV_HIDDEN ? "Show panel" : "Hide panel";
            case APP_FACTORY: return "Factory settings";
            case APP_CONTROLS: return "Controls";
            case APP_ABOUT: return "About";
            case APP_DEV: return "Developer  >";
            default: return "?" + row;
        }
    }

    /** the line under an app menu row's title */
    static String appDetail(int row, int overlay) {
        switch (row) {
            case APP_BROWSE: return "Brands and favourites";
            case APP_PANEL: return overlay == Params.OV_FULL ? "Full panel to a small label" : overlay == Params.OV_PILL ? "Small label to nothing" : "Back to the full panel";
            case APP_FACTORY: return "Store the camera's factory look now";
            case APP_CONTROLS: return "What every key does on this camera";
            case APP_ABOUT: return "Version, camera, keys found";
            case APP_DEV: return "Settings snapshot, read-only check, samples, key logger";
            default: return "";
        }
    }

    /** the About page: {name, value}. The version comes from the installed package at runtime, never from here. */
    static String[][] about(String version, String model, String platform, String keysFound) {
        return new String[][] {
            { "version", orUnknown(version) },
            { "camera", orUnknown(model) },
            { "platform", orUnknown(platform) },
            { "keys", keysFound },
            { "source", "github.com/voxivoid/recipe-lab-sony-pmca" },
        };
    }

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

    private static String orUnknown(String s) { return s == null || s.isEmpty() ? "unknown" : s; }

    // ------------------------------------------------------------ the key logger
    /** how many events the logger keeps on screen, newest first */
    static final int LOG_LINES = 6;
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
            case ROW_SNAPSHOT: return snapshotTaken ? "Settings diff" : "Settings snapshot";
            case ROW_LOCKS: return "Read-only check — " + Params.allSlots().size() + " slots";
            case ROW_SAMPLES: return "Shoot samples — " + Recipes.ALL.length + " recipes";
            case ROW_SETTLE: return "Settle delay — " + settleLabel(settle);
            case ROW_KEYS: return "Key logger";
            default: return "?" + row;
        }
    }

    /** the line under a row's title */
    static String rowDetail(int row, boolean snapshotTaken) {
        switch (row) {
            case ROW_SNAPSHOT: return snapshotTaken ? "Compare every settings id against the snapshot" : "Store the value of every settings id";
            case ROW_LOCKS: return "Test every slot a recipe writes for the read-only flag";
            case ROW_SAMPLES: return "One JPEG per recipe, in table order — MENU stops the run";
            case ROW_SETTLE: return "Wait after applying a recipe before the shutter fires";
            case ROW_KEYS: return "Show every key's scan code — hold MENU to leave";
            default: return "";
        }
    }

    // ------------------------------------------------------------ the settle delay
    /** a stored delay index brought back into the table */
    static int clampSettle(int idx) { return idx >= 0 && idx < SETTLE_MS.length ? idx : SETTLE_DEFAULT; }

    /** the next / previous delay, wrapping */
    static int nextSettle(int idx, int dir) { return (clampSettle(idx) + SETTLE_MS.length + dir) % SETTLE_MS.length; }

    /** a delay as the menu shows it: "1.2 s" (built by hand — String.format would follow the camera's locale) */
    static String settleLabel(int idx) {
        int ms = SETTLE_MS[clampSettle(idx)];
        return (ms / 1000) + "." + (ms % 1000) / 100 + " s";
    }

    // ------------------------------------------------------------ the sample run
    /** the run needs the live camera: without it nothing is applied and nothing can be shot */
    static final String NO_PREVIEW = "No live preview — the sample run needs the camera";

    /** the sticky line while the run walks the table; frames count from 1 */
    static String progress(int frame, int total, String recipeName) {
        return "Shooting " + frame + " / " + total + "  ·  " + recipeName + "   —   MENU stops";
    }

    /** the run reached the end of the table */
    static String doneMessage(int shot, int total) {
        return "Samples done — " + shot + " of " + total + " frames shot, listed in " + MANIFEST;
    }

    /** MENU during the run */
    static String stoppedMessage(int shot, int total) {
        return shot == 0 ? "Sample run stopped before the first frame"
                : "Sample run stopped — " + shot + " of " + total + " frames shot, listed in " + MANIFEST;
    }

    /** the camera refused a capture: the run cannot go on, and the frames so far are still listed */
    static String shootFailed(int frame, int shot, String error) {
        return "Shutter failed on frame " + frame + ": " + error + "  —  " + shot + " frames shot, listed in " + MANIFEST;
    }

    // ------------------------------------------------------------ the manifest
    /**
     * The first line of a run: what the columns are, and the delay it was shot with. Appended to, so a file can
     * hold several runs and each one says how it was made.
     */
    static String manifestHeader(int total, int settleMs) {
        return "# recipe-lab samples  ·  " + total + " frames in recipe order  ·  settle " + settleMs + " ms"
                + "  ·  frame" + SEP + "recipe" + SEP + "brand" + SEP + "values";
    }

    /** one frame: its number in the run, and the recipe that was applied for it */
    static String manifestLine(int frame, int recipeIndex) {
        Recipes.Recipe r = Recipes.ALL[recipeIndex];
        return pad2(frame) + SEP + r.name + SEP + Recipes.GROUPS[r.group] + SEP + r.summary();
    }

    private static String pad2(int n) { return n < 10 ? "0" + n : String.valueOf(n); }
}
