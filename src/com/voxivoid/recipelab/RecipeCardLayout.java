package com.voxivoid.recipelab;

/**
 * A recipe card: the picture Share saves to playback, with the recipe's name, its settings and the QR code that adds
 * it. Recipe Lab Studio draws the same card (its RecipeCardLayout, same shapes, same numbers); the camera's fonts are
 * its own. This holds where everything goes and what it says, so CardPainter only draws. No Android here (tools/test.sh).
 *
 * Rectangles are {left, top, right, bottom} in the card's pixels.
 */
final class RecipeCardLayout {
    /** the shapes, the common ones of feeds and chats: 4:5 a post, 1:1 a square, 9:16 a story. The first is the default */
    static final int PORTRAIT = 0, SQUARE = 1, STORY = 2, SHAPES = 3;
    static final String[] LABEL = { "4:5", "1:1", "9:16" };
    /**
     * Studio's sizes, but 4:5 two rows taller: the camera makes its images in heights of 8 rows, and refused 1350 (the
     * A6000, 2026-11-03), so the card fills 1352 — 0.15 % off 4:5, which no feed notices.
     */
    static final int[] WIDTH = { 1080, 1080, 1080 }, HEIGHT = { 1352, 1080, 1920 };

    static final float MARGIN = 72, STRIPE = 12;
    /** what a story shows clear of the app's own bars, top and bottom */
    static final float STORY_SAFE_TOP = 250, STORY_SAFE_BOTTOM = 1920 - 250;

    /** the picture profile's settings, listed after the others when the recipe has a profile */
    static final int PROFILE_SETTINGS = 8;
    /**
     * The groups the settings are in, Recipe Lab Studio's Settings groups (the camera app's), each listing what the
     * app shows for the recipe: Look (a Creative Style with its saturation, contrast and sharpness, or a Picture
     * Effect alone — the camera has one or the other), White balance, Exposure (exposure, and DRO unless an effect),
     * Picture Profile (a recipe with one).
     */
    static final int LOOK = 0, WHITE_BALANCE = 1, EXPOSURE = 2, PICTURE_PROFILE = 3;

    final int shape, width, height;
    final float headerTop, nameTop, rowHeight, scanTop;
    /** a setting's label and value sizes, and how far under the label the value goes: smaller where the rows are */
    final float labelSize, valueTop, valueSize;
    /** one per setting, in {@link #cells} order: its label at the top, its value under it */
    final float[][] cells;
    /** one per group, its title's band: the title sits {@link #TITLE_TOP} down it, nearer its settings than the ones above */
    final float[][] groups;
    static final float TITLE_TOP = 10, TITLE_SIZE = 22;
    /** each group's first setting in {@link #cells}; the last entry is where the picture profile's start (the end without one) */
    final int[] groupStart;
    /** the QR code with its quiet zone */
    final float[] qr;

    private RecipeCardLayout(int shape, float headerTop, float nameTop, Grid g, float[] qr, float scanTop) {
        this.shape = shape; width = WIDTH[shape]; height = HEIGHT[shape];
        this.headerTop = headerTop; this.nameTop = nameTop; this.qr = qr; this.scanTop = scanTop;
        cells = g.cells; groups = g.groups; groupStart = g.start; rowHeight = g.row; labelSize = g.label; valueTop = g.valueTop; valueSize = g.value;
    }

    /** the card of a recipe with a Creative Style and no picture profile */
    static RecipeCardLayout of(int shape) { return of(shape, false, false); }

    /** the card of {@code r}: what its settings are decides what the card lists */
    static RecipeCardLayout of(int shape, Recipes.Recipe r) { return of(shape, r.hasProfile(), r.pe != 0); }

    /**
     * Portrait and story: the settings under the name in their groups, the code centred below — the style or effect
     * across a row, the rest three to a row, White balance and Exposure side by side. Square, with no room for both
     * under each other: the settings in one column on the left, the code on the right.
     *
     * A recipe with a picture profile lists its 8 profile settings too, in the same space: portrait and story as a
     * fourth group, in shorter rows under a name moved up and over a code a little smaller; square in a second column
     * beside a code a little smaller. A Picture Effect has no style settings and no DRO, so its card is shorter.
     */
    static RecipeCardLayout of(int shape, boolean profile, boolean effect) {
        switch (shape) {
            case SQUARE:
                return profile
                        ? new RecipeCardLayout(SQUARE, 72, 200, column(312, 256, effect, true, new Grid(40, 72, 26, 30, 30)), rect(1080 - MARGIN - 400, 340, 400, 400), 340 + 400 + 40)
                        : new RecipeCardLayout(SQUARE, 72, 200, column(312, 476, effect, false, new Grid(40, 72, 26, 30, 32)), rect(1080 - MARGIN - 420, 340, 420, 420), 340 + 420 + 40);
            case STORY:
                return profile
                        ? new RecipeCardLayout(STORY, 270, 400, wide(492, effect, true, new Grid(40, 76, 26, 32, 32)), rect((1080 - 480) / 2f, 1080, 480, 480), 1080 + 480 + 44)
                        : new RecipeCardLayout(STORY, 270, 420, wide(570, effect, false, new Grid(40, 105, 26, 36, 36)), rect((1080 - 520) / 2f, 1050, 520, 520), 1050 + 520 + 44);
            default:
                return profile
                        ? new RecipeCardLayout(PORTRAIT, 84, 208, wide(300, effect, true, new Grid(40, 68, 24, 28, 30)), rect((1080 - 400) / 2f, 840, 400, 400), 1280)
                        : new RecipeCardLayout(PORTRAIT, 84, 224, wide(372, effect, false, new Grid(40, 100, 26, 36, 36)), rect((1080 - 440) / 2f, 800, 440, 440), 1280);
        }
    }

    /** the cells and group titles being laid out, with their sizes: a title's line, a row, a label, a value's offset and size */
    private static final class Grid {
        final float title, row, label, valueTop, value;
        float[][] cells, groups;
        int[] start;
        Grid(float title, float row, float label, float valueTop, float value) {
            this.title = title; this.row = row; this.label = label; this.valueTop = valueTop; this.value = value;
        }
        void begin(boolean effect, boolean profile) {
            int look = effect ? 1 : 4, exposure = effect ? 1 : 2;
            start = new int[] { 0, look, look + 1, look + 1 + exposure };
            cells = new float[start[3] + (profile ? PROFILE_SETTINGS : 0)][];
            groups = new float[profile ? 4 : 3][];
        }
    }

    /** portrait and story: across the card, from {@code top} */
    private static Grid wide(float top, boolean effect, boolean profile, Grid g) {
        g.begin(effect, profile);
        float full = 1080 - 2 * MARGIN, third = full / 3, y = top;
        g.groups[LOOK] = rect(MARGIN, y, full, g.title); y += g.title;
        g.cells[0] = rect(MARGIN, y, full, g.row); y += g.row;                                       // style or effect: a long name
        if (!effect) {
            for (int i = 1; i < 4; i++) g.cells[i] = rect(MARGIN + (i - 1) * third, y, third, g.row);  // saturation, contrast, sharpness
            y += g.row;
        }
        g.groups[WHITE_BALANCE] = rect(MARGIN, y, third, g.title);
        g.groups[EXPOSURE] = rect(MARGIN + third, y, 2 * third, g.title);
        y += g.title;
        g.cells[g.start[WHITE_BALANCE]] = rect(MARGIN, y, third, g.row);
        for (int k = 0; k < g.start[3] - g.start[EXPOSURE]; k++) g.cells[g.start[EXPOSURE] + k] = rect(MARGIN + (1 + k) * third, y, third, g.row);  // exposure, DRO
        y += g.row;
        if (profile) {
            g.groups[PICTURE_PROFILE] = rect(MARGIN, y, full, g.title); y += g.title;
            for (int k = 0; k < PROFILE_SETTINGS; k++) g.cells[g.start[3] + k] = rect(MARGIN + (k % 3) * third, y + (k / 3) * g.row, third, g.row);
        }
        return g;
    }

    /** square: one column {@code w} wide from {@code top}, each group's title over its settings; the profile in a second */
    private static Grid column(float top, float w, boolean effect, boolean profile, Grid g) {
        g.begin(effect, profile);
        float y = top;
        for (int grp = LOOK; grp <= EXPOSURE; grp++) {
            g.groups[grp] = rect(MARGIN, y, w, g.title); y += g.title;
            for (int i = g.start[grp]; i < g.start[grp + 1]; i++) { g.cells[i] = rect(MARGIN, y, w, g.row); y += g.row; }
        }
        if (profile) {
            g.groups[PICTURE_PROFILE] = rect(MARGIN + w, top, w, g.title);
            for (int k = 0; k < PROFILE_SETTINGS; k++) g.cells[g.start[3] + k] = rect(MARGIN + w, top + g.title + k * g.row, w, g.row);
        }
        return g;
    }

    static float[] rect(float left, float top, float w, float h) { return new float[] { left, top, left + w, top + h }; }

    /** the white tile behind the code */
    float[] tile() { return new float[] { qr[0] - 12, qr[1] - 12, qr[2] + 12, qr[3] + 12 }; }

    /** the band the "Scan to add" line is centred in: under the code — the right half of a square card, else the width */
    float[] scanBand() { return shape == SQUARE ? new float[] { tile()[0] - 40, scanTop, width, scanTop + 36 } : new float[] { 0, scanTop, width, scanTop + 36 }; }

    /** the space the name may take */
    float[] nameRect() { return rect(MARGIN, nameTop, width - 2 * MARGIN, 80); }

    /** the shape after / before {@code shape}, wrapping */
    static int next(int shape, int dir) { return ((shape + dir) % SHAPES + SHAPES) % SHAPES; }

    // ------------------------------------------------------------ what the card says
    /**
     * The settings a card lists, label and value, in the display language — as Recipe Lab Studio's card lists them,
     * and the app: the Creative Style with its saturation, contrast and sharpness, or the Picture Effect alone; white
     * balance with its fine shift; exposure, and DRO unless an effect; then the profile's 8 for a recipe with one. In
     * their groups' order ({@link #groupStart}).
     */
    static String[][] cells(Recipes.Recipe r) {
        boolean effect = r.pe != 0;
        String look = Recipes.styleLabel(r.style);
        if (effect) {
            look = Recipes.peLabel(r.pe);
            String sub = Recipes.subLabel(r.pe, r.sub);
            if (sub != null) look += " · " + sub;
        }
        String wb = r.wbMode == Params.WB_AUTO ? Lang.t("value_auto") : r.wbMode == Params.WB_KELVIN ? r.kelvin + "K" : Lang.t("card_keep");
        if (r.ab != 0) wb += " " + (r.ab > 0 ? "A" + r.ab : "B" + (-r.ab));
        if (r.gm != 0) wb += " " + (r.gm > 0 ? "G" + r.gm : "M" + (-r.gm));
        java.util.ArrayList<String[]> c = new java.util.ArrayList<String[]>();
        c.add(new String[] { Lang.t(effect ? "card_effect" : "card_style"), look });
        if (!effect) {
            c.add(new String[] { Lang.t("card_saturation"), signed(r.sat) });
            c.add(new String[] { Lang.t("card_contrast"), signed(r.con) });
            c.add(new String[] { Lang.t("card_sharpness"), signed(r.sharp) });
        }
        c.add(new String[] { Lang.t("card_white_balance"), wb });
        c.add(new String[] { Lang.t("card_exposure"), Recipes.evLabel(r.ev) + " EV" });
        if (!effect) c.add(new String[] { Lang.t("card_dro"), Recipes.droLabel(r.dro) });
        if (r.hasProfile()) {
            IspProfiles.Look p = r.look.normal();
            c.add(new String[] { Lang.t("card_gamma"), IspProfiles.curveLabel(p.curve) });
            c.add(new String[] { Lang.t("card_color_mode"), IspProfiles.gamutLabel(p.gamut) });
            c.add(new String[] { Lang.t("card_black_level"), signed(p.black) });
            c.add(new String[] { Lang.t("card_black_gamma"), p.blackGamma == 0 ? "0" : signed(p.blackGamma) + " · " + IspProfiles.rangeLabel(p.range) });
            c.add(new String[] { Lang.t("card_knee"), p.knee == IspProfiles.KNEE_OFF ? Lang.t("value_off") : p.knee + "%" + (p.slope == 0 ? "" : " · " + signed(p.slope)) });
            c.add(new String[] { Lang.t("card_color_saturation"), signed(p.sat) });
            c.add(new String[] { Lang.t("card_color_phase"), signed(p.phase) });
            c.add(new String[] { Lang.t("card_s_curve"), signed(p.contrast) });
        }
        return c.toArray(new String[c.size()][]);
    }

    /** the groups' titles, in the display language, as Studio's Settings names them; {@code count} of them */
    static String[] groupTitles(int count) {
        String[] all = { Lang.t("card_look"), Lang.t("card_white_balance"), Lang.t("card_exposure"), Lang.t("card_picture_profile") };
        String[] t = new String[count];
        System.arraycopy(all, 0, t, 0, count);
        return t;
    }

    /**
     * Whether Studio's card fonts (tools/card-fonts.py: ASCII, Latin-1, Latin Extended-A, Vietnamese and a few
     * punctuation marks) hold every character of {@code s}; a line that is not is drawn in the language's own font.
     */
    static boolean latin(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean ok = (ch >= 0x20 && ch < 0x7F) || (ch >= 0xA0 && ch < 0x180) || ch == 0x1A0 || ch == 0x1A1 || ch == 0x1AF
                    || ch == 0x1B0 || (ch >= 0x300 && ch < 0x304) || ch == 0x309 || ch == 0x323 || (ch >= 0x1EA0 && ch < 0x1EFA)
                    || ch == 0x2013 || ch == 0x2014 || ch == 0x2018 || ch == 0x2019 || ch == 0x201C || ch == 0x201D
                    || ch == 0x2022 || ch == 0x2026 || ch == 0x2192 || ch == 0x2212;
            if (!ok) return false;
        }
        return true;
    }

    /**
     * Where a line's baseline goes, as Flutter places it: a line box {@code lineHeight} tall from {@code top}, its
     * leading split above and below in proportion to the font's {@code ascent} and {@code descent} (both positive).
     */
    static float baseline(float top, float lineHeight, float ascent, float descent) {
        return ascent + descent <= 0 ? top : top + ascent * lineHeight / (ascent + descent);
    }

    /** the line under the code: "Scan to add · recipelab.io" */
    static String scanLine() { return Lang.t("card_scan") + " · recipelab.io"; }

    private static String signed(int v) { return v > 0 ? "+" + v : String.valueOf(v); }
}
