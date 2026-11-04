package com.voxivoid.recipelab;

/**
 * Hold MENU → Display: everything about what sits over the live image, in one submenu. First the panel itself — Full,
 * No keys, Label or Hidden, the same cycle trash steps through, kept by the activity under {@code panel} — then one
 * ON / OFF row each for the live histogram, the rule-of-thirds grid and the recipe counter, kept in the app's
 * preferences under their own keys ({@link #KEYS}), never in the camera's store; and Restore defaults.
 *
 * The key legend and the Label pill have no rows of their own: the panel's No keys and Hidden states already take them
 * away, and a second switch for each made a trash press that changed nothing on screen.
 *
 * A fresh install looks as before: the full panel, histogram and grid off, the counter on. The autofocus-point overlay
 * is a separate feature: when it lands it adds its row here (an id before {@link #DEFAULTS}, its key, default, label
 * and detail). No android.* import may appear in this class (tools/test.sh).
 */
final class DisplayOptions {
    private DisplayOptions() {}

    /** the submenu's rows, in display order; the rows between the panel and Restore defaults are ON / OFF options */
    static final int PANEL = 0, HISTOGRAM = 1, GRID = 2, COUNTER = 3, DEFAULTS = 4, ROWS = 5;

    /** each option's preference key, by row; the panel is a state, not an ON / OFF option, and the activity keeps it */
    static final String[] KEYS = { null, "display.histogram", "display.grid", "display.counter" };
    private static final boolean[] DEFAULT = { false, false, false, true };

    private static final String[] LABEL = { "menu_panel", "display_histogram", "display_grid", "display_counter", "display_defaults" };
    private static final String[] DETAIL = { "menu_panel_detail", "display_histogram_detail", "display_grid_detail", "display_counter_detail",
            "display_defaults_detail" };

    static String title() { return Lang.t("display_title"); }

    /** what a fresh install shows, by row (the panel's entry unused) */
    static boolean[] defaults() { return DEFAULT.clone(); }

    /** whether a row is an ON / OFF option — not the panel, not Restore defaults */
    static boolean hasValue(int row) { return row > PANEL && row < DEFAULTS; }

    /** left (off) / right (on) on a row: sets it, the same value again changing nothing; false on a row with no value */
    static boolean set(boolean[] on, int row, boolean value) {
        if (!hasValue(row)) return false;
        on[row] = value;
        return true;
    }

    /** the centre button on an option: on ↔ off; false on a row with no value */
    static boolean toggle(boolean[] on, int row) {
        if (!hasValue(row)) return false;
        on[row] = !on[row];
        return true;
    }

    /** Restore defaults: every option as on a fresh install (the caller puts the panel back to Full) */
    static void restore(boolean[] on) { System.arraycopy(DEFAULT, 0, on, 0, DEFAULT.length); }

    static String label(int row) { return Lang.t(LABEL[row]); }
    static String detail(int row) { return Lang.t(DETAIL[row]); }

    /** the value at a row's right edge: the panel state, on / off, or null for Restore defaults */
    static String value(int row, boolean[] on, int overlay) {
        if (row == PANEL) return DevTools.panelLabel(overlay);
        return hasValue(row) ? Lang.t(on[row] ? "value_on" : "value_off") : null;
    }

    /**
     * Where the preview image sits in a {@code w × h} view: {left, top, right, bottom} of the largest rectangle of the
     * preview's {@code pw × ph} shape, centred, so letterbox or pillarbox bars are left out. The whole view while the
     * preview size is unknown. Whether the camera letterboxes this way or stretches the image over the view is a
     * question for the camera (docs/DEVELOPMENT.md).
     */
    static int[] contentRect(int w, int h, int pw, int ph) {
        if (pw <= 0 || ph <= 0) return new int[] { 0, 0, w, h };
        long wide = (long) w * ph, tall = (long) h * pw;
        if (wide > tall) {                                        // the view is wider than the image: bars left and right
            int cw = (int) (tall / ph), x = (w - cw) / 2;
            return new int[] { x, 0, x + cw, h };
        }
        int ch = (int) (wide / pw), y = (h - ch) / 2;             // taller, or the same shape: bars top and bottom (or none)
        return new int[] { 0, y, w, y + ch };
    }
}
