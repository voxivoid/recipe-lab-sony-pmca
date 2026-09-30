package com.voxivoid.recipelab;

import java.util.ArrayList;
import java.util.List;

/**
 * The keys (issue #18): the scan codes the app reads, the press / hold gesture, and the legend built for the keys this
 * body actually has.
 *
 * Every function has a route on keys every PlayMemories body has — wheel, four-way, centre, MENU, trash, shutter. Fn is
 * the one shortcut on top, and the legend names it only where {@link KeyProbe} reports it. AEL, C1 and DISP are not
 * bound at all: trash hides the panel on every body, MENU hold → Developer replaces C1, and a DISP that arrives as 608
 * on a wheel-top body would steal wheel-up. No android.* import may appear in this class (tools/test.sh).
 */
final class Keys {
    private Keys() {}

    // ------------------------------------------------------------ scan codes (com.sony.scalar.sysutil.ScalarInput)
    static final int K_UP = 103, K_DOWN = 108, K_LEFT = 105, K_RIGHT = 106, K_ENTER = 232, K_MENU = 514, K_SK1 = 229,
            K_DELETE = 595, K_SK2 = 513, K_PLAY = 207, K_DISP = 608, K_FN = 520, K_AEL = 532, K_C1 = 622, K_S1 = 516, K_S2 = 518,
            K_WHEEL_CW = 522, K_WHEEL_CCW = 523, K_DIAL_CW = 525, K_DIAL_CCW = 526, K_MOVIE = 515, K_ZOOM_T = 610, K_ZOOM_W = 611;
    /** the absolute-position codes of the wheel and the top dial: what the key probe asks about, not what a turn sends */
    static final int K_WHEEL_STATUS = 521, K_DIAL_STATUS = 524;
    /** the AF/MF–AEL lever of the A7 II bodies, in both of Sony's spellings: probed and logged, not bound (A7S II, #48) */
    static final int K_AEL_LEVER = 589, K_AEL_AFMF = 638;

    /** a scan code as Sony names it, for the key logger; unknown codes are shown as the number alone */
    static String name(int scan) {
        switch (scan) {
            case K_UP: return "UP"; case K_DOWN: return "DOWN"; case K_LEFT: return "LEFT"; case K_RIGHT: return "RIGHT";
            case K_ENTER: return "ENTER"; case K_MENU: return "MENU"; case K_SK1: return "SK1"; case K_SK2: return "SK2";
            case K_DELETE: return "DELETE"; case K_PLAY: return "PLAY"; case K_DISP: return "DISP"; case K_FN: return "FN";
            case K_AEL: return "AEL"; case K_C1: return "C1"; case K_S1: return "S1"; case 517: return "S1_2"; case K_S2: return "S2";
            case K_WHEEL_CW: return "WHEEL+"; case K_WHEEL_CCW: return "WHEEL-"; case K_DIAL_CW: return "DIAL+"; case K_DIAL_CCW: return "DIAL-";
            case 528: return "DIAL2+"; case 529: return "DIAL2-"; case K_MOVIE: return "MOVIE"; case 637: return "MOVIE2";
            case K_ZOOM_T: return "ZOOM_T"; case K_ZOOM_W: return "ZOOM_W"; case 533: return "AFMF";
            case K_AEL_LEVER: return "AEL_LEVER"; case K_AEL_AFMF: return "AEL_AFMF"; case 588: return "CUSTOM";
            case 623: return "C2"; case 659: return "C3"; case 572: return "MODE_DIAL"; case 519: return "FINDER";
            case 530: return "LENS"; case 591: return "UP_RIGHT"; case 592: return "DOWN_RIGHT"; case 593: return "UP_LEFT"; case 594: return "DOWN_LEFT";
            default: return "";
        }
    }

    /**
     * Keys whose press does one thing, once: a key-repeat of these is dropped, or holding Fn would open and then close
     * the list, and holding trash would spin the panel. The four-way and the dials keep their repeat — held, they walk.
     */
    static boolean oneShot(int scan) {
        switch (scan) {
            case K_ENTER: case K_MENU: case K_SK1: case K_DELETE: case K_SK2: case K_FN: return true;
            default: return false;
        }
    }

    /** trash on bodies that have one, or the second soft key that stands for it on the NEX bodies and possibly the A5000 */
    static boolean isTrash(int scan) { return scan == K_DELETE || scan == K_SK2; }
    static boolean isMenu(int scan) { return scan == K_MENU || scan == K_SK1; }

    // ------------------------------------------------------------ press / hold
    /** how long a key is held before its hold action fires instead of its press */
    static final long HOLD_MS = 600;

    /**
     * One key's press-or-hold. The short action runs on the release, unless the hold already fired; the hold is fired
     * by a timer the caller posts on ARM, so nothing depends on the firmware delivering key-repeat events.
     */
    static final class Hold {
        static final int IGNORE = 0, ARM = 1, SHORT = 2, NONE = 3, HOLD = 4;
        private boolean down, fired;

        /** the key went down: ARM on a first press, IGNORE for a repeat or while this key is still held */
        int down(int repeat) {
            if (repeat > 0 || down) return IGNORE;
            down = true; fired = false;
            return ARM;
        }

        /** the key came up: SHORT if it was down and the hold did not fire */
        int up() {
            boolean was = down, f = fired;
            down = false; fired = false;
            return was && !f ? SHORT : NONE;
        }

        /** the hold timer ran out: HOLD once per press, while the key is down */
        int fire() {
            if (!down || fired) return NONE;
            fired = true;
            return HOLD;
        }

        boolean isDown() { return down; }
        void reset() { down = false; fired = false; }
    }

    /**
     * Whether a trash hold that has fired is a hold. Trash hides on its release, so a release cancels the timer and a
     * fired timer normally means the key is still down. The exception is a body that loses the release: when the camera
     * says the key is already up, the press was a short one and hides instead. When the camera would not say, it is a
     * hold — the worst case is the reset question, which defaults to Cancel.
     */
    static boolean trashHoldActs(Boolean probeDown) { return probeDown == null || probeDown; }

    // ------------------------------------------------------------ what the body has
    /** the shortcut key the legend may name, as the probe reports it: TRUE present, FALSE absent, null unknown */
    static final class Caps {
        static final Caps UNKNOWN = new Caps(null);
        final Boolean fn;
        Caps(Boolean fn) { this.fn = fn; }
        boolean hasFn() { return Boolean.TRUE.equals(fn); }
    }

    // ------------------------------------------------------------ the legend
    /** legend icon ids; {@link Legend} draws them */
    static final int I_NONE = -1, I_WHEEL = 0, I_UPDOWN = 1, I_LEFTRIGHT = 2, I_DIAL = 3, I_ENTER = 4, I_TRASH = 6,
            I_MENU = 7, I_FN = 9;

    /** the legend rows: the main panel's three, the browser's two columns, and the menu's — *_VALUE on a row left / right change */
    static final int H_RECIPE = 0, H_CHIPS = 1, H_EDIT = 2, H_BRANDS = 3, H_RECIPES = 4, H_MENU_TOP = 5, H_MENU_SUB = 6,
            H_PAGE = 7, H_LOGGER = 8, H_MENU_TOP_VALUE = 9, H_MENU_SUB_VALUE = 10;

    /** one legend row: per item an icon, an optional shortcut icon drawn before it (I_NONE for none), and a label */
    static final class Hints {
        final int[] icons, alts;
        final String[] labels;
        Hints(int[] icons, int[] alts, String[] labels) { this.icons = icons; this.alts = alts; this.labels = labels; }
    }

    private static final class Row {
        final List<int[]> keys = new ArrayList<int[]>();
        final List<String> labels = new ArrayList<String>();
        Row add(int icon, String label) { return add(icon, I_NONE, label); }
        Row add(int icon, int alt, String label) { keys.add(new int[] { icon, alt }); labels.add(label); return this; }
        Hints done() {
            int n = keys.size();
            int[] icons = new int[n], alts = new int[n];
            for (int i = 0; i < n; i++) { icons[i] = keys.get(i)[0]; alts[i] = keys.get(i)[1]; }
            return new Hints(icons, alts, labels.toArray(new String[n]));
        }
    }

    /**
     * The legend for a screen, for this body. Universal keys carry every function; Fn appears only when the probe
     * reports it — as "browse", which is also in the app menu, or beside MENU where both close the list — so a body that
     * lacks it (or a probe that failed) still reads a complete legend. The order is fixed: pick, browse, fav, menu, hide,
     * exit. Reset (hold trash) is left out on purpose: it asks before it writes, and a hint would invite it. The labels
     * are in the display language ({@link Lang}).
     */
    static Hints hints(int mode, Caps caps) {
        int fn = caps.hasFn() ? I_FN : I_NONE;
        switch (mode) {
            case H_RECIPE: {
                Row r = new Row().add(I_ENTER, Lang.t("action_pick"));
                if (caps.hasFn()) r.add(I_FN, Lang.t("action_browse"));
                return r.add(I_ENTER, Lang.t("action_favourite_hold")).add(I_MENU, Lang.t("action_menu_hold")).add(I_TRASH, Lang.t("action_hide")).add(I_MENU, Lang.t("action_exit")).done();
            }
            case H_CHIPS: {
                Row r = new Row().add(I_ENTER, Lang.t("action_edit"));
                if (caps.hasFn()) r.add(I_FN, Lang.t("action_browse"));
                return r.add(I_MENU, Lang.t("action_menu_hold")).add(I_TRASH, Lang.t("action_hide")).add(I_MENU, Lang.t("action_exit")).done();
            }
            case H_EDIT: return new Row().add(I_ENTER, Lang.t("action_done")).done();
            case H_BRANDS: return new Row().add(I_ENTER, Lang.t("action_recipes")).add(I_MENU, fn, Lang.t("action_close")).done();
            case H_RECIPES: return new Row().add(I_ENTER, Lang.t("action_pick")).add(I_ENTER, Lang.t("action_favourite_hold")).add(I_MENU, fn, Lang.t("action_close")).done();
            case H_MENU_TOP: return new Row().add(I_UPDOWN, Lang.t("action_move")).add(I_ENTER, Lang.t("action_select")).add(I_MENU, Lang.t("action_close")).done();
            case H_MENU_SUB: return new Row().add(I_UPDOWN, Lang.t("action_move")).add(I_ENTER, Lang.t("action_select")).add(I_MENU, Lang.t("action_back")).done();
            case H_MENU_TOP_VALUE: return new Row().add(I_UPDOWN, Lang.t("action_move")).add(I_LEFTRIGHT, Lang.t("action_change")).add(I_MENU, Lang.t("action_close")).done();
            case H_MENU_SUB_VALUE: return new Row().add(I_UPDOWN, Lang.t("action_move")).add(I_LEFTRIGHT, Lang.t("action_change")).add(I_MENU, Lang.t("action_back")).done();
            case H_PAGE: return new Row().add(I_MENU, Lang.t("action_back")).done();
            case H_LOGGER: return new Row().add(I_MENU, Lang.t("action_exit_hold")).done();
            default: return new Row().done();
        }
    }

    /** shown once, on the first launch of a build with these keys */
    static String notice() { return Lang.t("keys_notice"); }
}
