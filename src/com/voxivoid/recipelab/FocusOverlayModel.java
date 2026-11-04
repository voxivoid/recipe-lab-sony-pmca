package com.voxivoid.recipelab;

import java.util.ArrayList;
import java.util.List;

/**
 * The autofocus boxes over the live view while the shutter is half-pressed: which of the camera's reported areas to
 * draw, in which state, and where on screen. No Android, covered by test/ — {@link FocusAreaBridge} brings Sony's
 * callbacks here, {@link FocusOverlayView} draws the result, MainActivity calls it on the UI thread only.
 *
 * Nothing is ever guessed: no reported area, an unknown area number or a rectangle that cannot be placed means no box.
 * Releasing the half-press hides the boxes at once, and a callback that arrives after it (or from an earlier camera
 * session) is dropped, so a late result never brings an old box back.
 */
final class FocusOverlayModel {
    /** what the boxes say */
    static final int IDLE = 0, ACQUIRING = 1, LOCKED = 2, WARNING = 3, CONTINUOUS = 4;
    /** CameraEx.AutoFocusDoneListener's statuses (STATUS_LOCK_WARM is Sony's alias of STATUS_LOCK_WARN) */
    static final int STATUS_CLEAR = 0, STATUS_LOCK = 1, STATUS_LOCK_WARN = 2, STATUS_WORKING = 3, STATUS_CONTINUOUS = 4;
    /**
     * The range Sony's area rectangles are taken to span across the live image, as Android's focus areas do and as
     * sony-remote-reboot reads them. A hypothesis until checked on an A6000 with an off-centre Flexible Spot: this is
     * the knob to change if the boxes land off. Anything outside it is not drawn.
     */
    static final int SOURCE_MIN = -1000, SOURCE_MAX = 1000;

    /** one of the camera's AF areas, copied out of its callback: Sony's index, enable flag and rectangle */
    static final class Area {
        final int index;
        final boolean enabled;
        final int left, top, right, bottom;

        Area(int index, boolean enabled, int left, int top, int right, int bottom) {
            this.index = index; this.enabled = enabled;
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }

        /** enabled, not inverted or empty, and inside the source range */
        boolean drawable() {
            return enabled && left < right && top < bottom
                    && left >= SOURCE_MIN && top >= SOURCE_MIN && right <= SOURCE_MAX && bottom <= SOURCE_MAX;
        }
    }

    private static final Area[] NONE = new Area[0];

    private int generation = 0, state = IDLE;
    private boolean pressed = false;
    private Area[] catalog = NONE;
    private List<Area> shown = new ArrayList<Area>();

    /** a camera session starts: everything from an earlier one is forgotten and its late callbacks are dropped */
    int open() { close(); return generation; }

    /** the camera goes away (pause, release): its callbacks still in flight are dropped */
    void close() { generation++; catalog = NONE; clear(); }

    /** the camera session callbacks have to carry to be taken */
    int generation() { return generation; }

    /** S1 down where the boxes may show: acquiring, with no box until the camera says where. A key repeat changes nothing */
    void press() {
        if (pressed) return;
        pressed = true; state = ACQUIRING; shown = new ArrayList<Area>();
    }

    /** S1 up, the shutter, another recipe, a screen over the live view: no box, and none until the next press */
    void clear() { pressed = false; state = IDLE; shown = new ArrayList<Area>(); }

    /** the camera's list of AF areas changed; kept for the area numbers a focus result names. False when it is stale */
    boolean areas(int gen, Area[] areas) {
        if (gen != generation) return false;
        catalog = areas == null ? NONE : areas.clone();
        return true;
    }

    /** AF started: acquiring, and the last result's boxes go. True when there is something to redraw */
    boolean started(int gen) {
        if (gen != generation || !pressed) return false;
        state = ACQUIRING; shown = new ArrayList<Area>();
        return true;
    }

    /** an AF result: its state, and the boxes of the areas it names. True when there is something to redraw */
    boolean done(int gen, int status, int[] ids) {
        if (gen != generation || !pressed) return false;
        state = stateOf(status);
        shown = state == IDLE ? new ArrayList<Area>() : match(catalog, ids);
        return true;
    }

    int state() { return state; }

    List<Area> shown() { return shown; }

    /** a done status as a box state: an unknown one is a warning — never a confirmed lock */
    static int stateOf(int status) {
        switch (status) {
            case STATUS_CLEAR: return IDLE;
            case STATUS_LOCK: return LOCKED;
            case STATUS_WORKING: return ACQUIRING;
            case STATUS_CONTINUOUS: return CONTINUOUS;
            default: return WARNING;
        }
    }

    /**
     * The areas a result names. Each number is matched to an area's own index; only when no area has that index is
     * it taken as a position in the list (sony-remote-reboot's rule). Areas that are disabled or cannot be drawn are
     * left out, and an area named twice is drawn once.
     */
    static List<Area> match(Area[] catalog, int[] ids) {
        List<Area> out = new ArrayList<Area>();
        if (catalog == null || ids == null) return out;
        for (int id : ids) {
            Area a = byIndex(catalog, id);
            if (a == null && id >= 0 && id < catalog.length) a = catalog[id];
            if (a != null && a.drawable() && !out.contains(a)) out.add(a);
        }
        return out;
    }

    /**
     * One LOG.TXT line about the area list Sony sent: how many, how many {@link Area#drawable}, and the first few as
     * given — {@code 9 areas, 0 drawable: #0 on [12,40,52,80] #1 off [..]}. What an on-camera check needs to see why
     * no box shows (another coordinate range, areas flagged off) without a debugger.
     */
    static String describeAreas(Area[] areas) {
        if (areas == null) return "no area list";
        int drawable = 0;
        for (Area a : areas) if (a != null && a.drawable()) drawable++;
        StringBuilder b = new StringBuilder().append(areas.length).append(" areas, ").append(drawable).append(" drawable:");
        for (int i = 0; i < Math.min(3, areas.length); i++) {
            Area a = areas[i];
            if (a == null) { b.append(" null"); continue; }
            b.append(" #").append(a.index).append(a.enabled ? " on [" : " off [").append(a.left).append(',').append(a.top)
                    .append(',').append(a.right).append(',').append(a.bottom).append(']');
        }
        return b.toString();
    }

    private static Area byIndex(Area[] catalog, int id) {
        for (Area a : catalog) if (a != null && a.index == id) return a;
        return null;
    }

    /**
     * Where the live image sits in a view: the preview's aspect fitted inside it, centred, letterboxed or pillarboxed.
     * {left, top, width, height}, or null when either size is unknown — then nothing can be placed.
     */
    static int[] content(int viewW, int viewH, int previewW, int previewH) {
        if (viewW <= 0 || viewH <= 0 || previewW <= 0 || previewH <= 0) return null;
        long wide = (long) viewW * previewH, tall = (long) viewH * previewW;
        int w = wide > tall ? (int) (tall / previewH) : viewW;
        int h = wide > tall ? viewH : (int) (wide / previewW);
        return new int[] { (viewW - w) / 2, (viewH - h) / 2, w, h };
    }

    /**
     * An area in view pixels {left, top, right, bottom}; null when it cannot be drawn or rounds to nothing. It stays
     * inside the live image, and so on screen, since {@link Area#drawable} keeps it inside the source range.
     */
    static int[] map(Area a, int[] content) {
        if (a == null || content == null || !a.drawable()) return null;
        int l = along(a.left, content[0], content[2]), t = along(a.top, content[1], content[3]);
        int r = along(a.right, content[0], content[2]), b = along(a.bottom, content[1], content[3]);
        return l < r && t < b ? new int[] { l, t, r, b } : null;
    }

    private static int along(int v, int start, int length) {
        return start + (int) Math.round((double) (v - SOURCE_MIN) * length / (SOURCE_MAX - SOURCE_MIN));
    }

    /** the boxes to draw now, in view pixels: none while idle (nothing is shown then) or when the live image cannot be placed */
    List<int[]> boxes(int viewW, int viewH, int previewW, int previewH) {
        List<int[]> out = new ArrayList<int[]>();
        int[] c = content(viewW, viewH, previewW, previewH);
        for (Area a : shown) {
            int[] box = map(a, c);
            if (box != null) out.add(box);
        }
        return out;
    }

    /**
     * The line LOG.TXT gets for a session's first drawn box: the sizes, the state, each area as Sony gave it and where
     * it was drawn — what an on-camera check of the alignment needs.
     */
    String describe(int viewW, int viewH, int previewW, int previewH) {
        StringBuilder sb = new StringBuilder("preview ").append(previewW).append('x').append(previewH)
                .append(" view ").append(viewW).append('x').append(viewH).append(" state ").append(state);
        int[] c = content(viewW, viewH, previewW, previewH);
        for (Area a : shown) {
            int[] b = map(a, c);
            sb.append("  #").append(a.index).append(' ').append(a.left).append(',').append(a.top).append(',').append(a.right).append(',').append(a.bottom)
                    .append(" > ").append(b == null ? "-" : b[0] + "," + b[1] + "," + b[2] + "," + b[3]);
        }
        return sb.toString();
    }
}
