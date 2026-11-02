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
    static final int[] WIDTH = { 1080, 1080, 1080 }, HEIGHT = { 1350, 1080, 1920 };

    static final float MARGIN = 72, STRIPE = 12;
    /** what a story shows clear of the app's own bars, top and bottom */
    static final float STORY_SAFE_TOP = 250, STORY_SAFE_BOTTOM = 1920 - 250;

    final int shape, width, height;
    final float headerTop, nameTop, rowHeight, scanTop;
    /** one per setting, in {@link #cells} order: its label at the top, its value under it */
    final float[][] cells;
    /** the QR code with its quiet zone */
    final float[] qr;

    private RecipeCardLayout(int shape, float headerTop, float nameTop, float[][] cells, float rowHeight, float[] qr, float scanTop) {
        this.shape = shape; width = WIDTH[shape]; height = HEIGHT[shape];
        this.headerTop = headerTop; this.nameTop = nameTop; this.cells = cells; this.rowHeight = rowHeight; this.qr = qr; this.scanTop = scanTop;
    }

    /**
     * Portrait and story: settings in two columns under the name, the code centred below. Square, with no room for
     * both under each other: settings in one column on the left, the code on the right.
     */
    static RecipeCardLayout of(int shape) {
        switch (shape) {
            case SQUARE: {
                float[][] c = new float[8][];
                for (int i = 0; i < 8; i++) c[i] = rect(MARGIN, 312 + i * 84f, 476, 84);
                return new RecipeCardLayout(SQUARE, 72, 200, c, 84, rect(1080 - MARGIN - 420, 340, 420, 420), 340 + 420 + 40);
            }
            case STORY:
                return new RecipeCardLayout(STORY, 270, 420, twoColumns(570, 105), 105, rect((1080 - 520) / 2f, 1050, 520, 520), 1050 + 520 + 44);
            default:
                return new RecipeCardLayout(PORTRAIT, 84, 224, twoColumns(372, 100), 100, rect((1080 - 440) / 2f, 800, 440, 440), 1280);
        }
    }

    private static float[][] twoColumns(float top, float row) {
        float column = (1080 - 2 * MARGIN) / 2;
        float[][] c = new float[8][];
        for (int i = 0; i < 8; i++) c[i] = rect(MARGIN + (i % 2) * column, top + (i / 2) * row, column, row);
        return c;
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
    /** the settings a card lists, label and value, in the display language — as Recipe Lab Studio's card lists them */
    static String[][] cells(Recipes.Recipe r) {
        String effect = r.pe == 0 ? Lang.t("value_off") : Recipes.peLabel(r.pe);
        String sub = r.pe == 0 ? null : Recipes.subLabel(r.pe, r.sub);
        if (sub != null) effect += " · " + sub;
        String wb = r.wbMode == Params.WB_AUTO ? Lang.t("value_auto") : r.wbMode == Params.WB_KELVIN ? r.kelvin + "K" : Lang.t("card_keep");
        if (r.ab != 0) wb += " " + (r.ab > 0 ? "A" + r.ab : "B" + (-r.ab));
        if (r.gm != 0) wb += " " + (r.gm > 0 ? "G" + r.gm : "M" + (-r.gm));
        return new String[][] {
            { Lang.t("card_style"), Recipes.styleLabel(r.style) },
            { Lang.t("card_effect"), effect },
            { Lang.t("card_saturation"), signed(r.sat) },
            { Lang.t("card_contrast"), signed(r.con) },
            { Lang.t("card_sharpness"), signed(r.sharp) },
            { Lang.t("card_dro"), Recipes.droLabel(r.dro) },
            { Lang.t("card_white_balance"), wb },
            { Lang.t("card_exposure"), Recipes.evLabel(r.ev) + " EV" },
        };
    }

    /** the line under the code: "Scan to add · recipelab.io" */
    static String scanLine() { return Lang.t("card_scan") + " · recipelab.io"; }

    private static String signed(int v) { return v > 0 ? "+" + v : String.valueOf(v); }
}
