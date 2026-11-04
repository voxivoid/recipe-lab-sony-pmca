package com.voxivoid.recipelab;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Draws a recipe card ({@link RecipeCardLayout}) on a canvas: on screen scaled down for Share's preview, and full size
 * into a bitmap for {@link Playback}. Recipe Lab Studio's card: its colours, its logo drawn the same way, its sizes, and
 * its fonts (static cuts of them, tools/card-fonts.py) with its letter spacing and line heights. A line one of those
 * cannot draw (Chinese, Japanese, Korean) is drawn in the language's own font.
 */
final class CardPainter {
    private static final int BG = 0xFF10161F, INK = 0xFFE7E4DD, FAINT = 0xFF7C8794, MUTED = 0xFFA7B0BA;
    private static final int[] DUSK = { 0xFFE6D49A, 0xFFD9A178, 0xFFC98B86, 0xFFA8433E };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private Typeface face = Typeface.DEFAULT, bold = Typeface.DEFAULT_BOLD;

    /** Studio's styles on the card (Lab.* in its app_theme.dart): the font, the letter spacing in ems, the line height */
    static final int TITLE = 0, VALUE = 1, LABEL = 2, WORDMARK = 3, PLATE = 4;
    private static final String[] FONT = { "fonts/CardInter-SemiBold650.ttf", "fonts/CardInter-SemiBold.ttf",
            "fonts/CardInter-Medium.ttf", "fonts/CardArchivo-Wordmark.ttf", "fonts/CardSpaceMono-Bold.ttf" };
    private static final float[] SPACING = { -0.02f, 0, 0, -0.005f, 0.04f }, HEIGHT = { 1.2f, 1.3f, 1.3f, 1.08f, 1.3f };
    private static Typeface[] studio;
    private Typeface[] fonts = new Typeface[FONT.length];

    /** Studio's fonts, from the APK's assets, loaded once; one that will not load leaves its lines in the camera's */
    void useStudioFonts(AssetManager assets) {
        synchronized (CardPainter.class) {
            if (studio == null) {
                studio = new Typeface[FONT.length];
                for (int i = 0; i < FONT.length; i++)
                    try { studio[i] = Typeface.createFromAsset(assets, FONT[i]); } catch (Throwable t) { studio[i] = null; }
            }
            fonts = studio;
        }
    }

    /** the display language's typeface (a CJK one draws its own bold); null for the default */
    void setTypeface(Typeface tf) {
        face = tf == null ? Typeface.DEFAULT : tf;
        bold = tf == null || tf == Typeface.DEFAULT ? Typeface.DEFAULT_BOLD : tf;
    }

    /** the card at its own size, from (0, 0); scale the canvas for anything else */
    void draw(Canvas c, RecipeCardLayout l, Recipes.Recipe recipe, boolean[] modules, int size) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(BG);
        c.drawRect(0, 0, l.width, l.height, fill);
        float band = l.width / (float) DUSK.length;
        for (int i = 0; i < DUSK.length; i++) { fill.setColor(DUSK[i]); c.drawRect(i * band, 0, (i + 1) * band + 0.5f, RecipeCardLayout.STRIPE, fill); }

        float m = RecipeCardLayout.MARGIN;
        logo(c, m, l.headerTop, 80);
        line(c, "RECIPE LAB", m + 104, l.headerTop + 4, 40, INK, WORDMARK, 0, false);
        line(c, Lang.t("card_heading").toUpperCase(java.util.Locale.US), m + 106, l.headerTop + 52, 22, MUTED, PLATE, 0, false);

        float[] name = l.nameRect();
        line(c, recipe.name, name[0], name[1], 64, INK, TITLE, name[2] - name[0], false);

        String[][] cells = RecipeCardLayout.cells(recipe);
        boolean roomy = l.rowHeight > 90;
        for (int i = 0; i < cells.length; i++) {
            float[] cell = l.cells[i];
            float w = cell[2] - cell[0] - 24;
            line(c, cells[i][0], cell[0], cell[1], 26, FAINT, LABEL, w, false);
            line(c, cells[i][1], cell[0], cell[1] + (roomy ? 36 : 32), roomy ? 36 : 32, INK, VALUE, w, false);
        }

        float[] t = l.tile();
        fill.setColor(0xFFFFFFFF);
        r.set(t[0], t[1], t[2], t[3]);
        c.drawRoundRect(r, 28, 28, fill);
        qr(c, l.qr, modules, size);

        float[] s = l.scanBand();
        line(c, RecipeCardLayout.scanLine(), s[0], s[1], 28, MUTED, LABEL, s[2] - s[0], true);
    }

    /** the code, black on the white the tile already is, four modules of quiet zone around it */
    private void qr(Canvas c, float[] at, boolean[] modules, int size) {
        if (modules == null || size <= 0) return;
        float side = at[2] - at[0], mod = side / (size + 8);
        fill.setColor(0xFF000000);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if (modules[y * size + x]) {
                    float left = at[0] + (x + 4) * mod, top = at[1] + (y + 4) * mod;
                    c.drawRect(left, top, left + mod + 0.5f, top + mod + 0.5f, fill);
                }
    }

    /**
     * One line of text in {@code style}, laid out as Flutter lays out Studio's: its line box ({@link #HEIGHT} × size)
     * from {@code top}, the leading split in proportion to the font's ascent and descent, {@link #SPACING} after every
     * character. Cut with "…" to {@code maxWidth} (0: no limit), centred in it when {@code centre}.
     */
    private void line(Canvas c, String s, float left, float top, float size, int colour, int style, float maxWidth, boolean centre) {
        Typeface studioFace = fonts[style];
        boolean own = studioFace != null && RecipeCardLayout.latin(s);
        boolean strong = style != LABEL;
        text.setTypeface(own ? studioFace : strong ? bold : face);
        text.setFakeBoldText(!own && strong && bold == face);  // a CJK face has no bold of its own
        text.setTextSize(size);
        text.setColor(colour);
        float spacing = own ? SPACING[style] * size : 0;
        String shown = s;
        if (maxWidth > 0 && width(shown, spacing) > maxWidth) {
            float room = maxWidth - width("…", spacing);
            int n = s.length();
            while (n > 0 && width(s.substring(0, n), spacing) > room) n--;
            shown = s.substring(0, n).trim() + "…";
        }
        float x = centre ? left + (maxWidth - width(shown, spacing)) / 2 : left;
        float baseline = RecipeCardLayout.baseline(top, HEIGHT[style] * size, -text.ascent(), text.descent());
        if (spacing == 0) { c.drawText(shown, x, baseline, text); return; }
        for (int i = 0; i < shown.length(); i++) {
            String ch = shown.substring(i, i + 1);
            c.drawText(ch, x, baseline, text);
            x += text.measureText(ch) + spacing;
        }
    }

    /** a line's width with {@code spacing} after each character, as Flutter measures letter spacing */
    private float width(String s, float spacing) {
        if (spacing == 0) return text.measureText(s);
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += text.measureText(s, i, i + 1) + spacing;
        return w;
    }

    /** Recipe Lab's icon, as Studio draws it: a film strip with three sky-over-clay frames, {@code side} across */
    private void logo(Canvas c, float left, float top, float side) {
        c.save();
        c.translate(left, top);
        c.scale(side / 512f, side / 512f);
        fill.setColor(0xFF1A1208);
        r.set(0, 0, 512, 512); c.drawRoundRect(r, 104, 104, fill);
        fill.setColor(0xFFF2B85C);
        c.drawRect(52, 133, 460, 379, fill);
        fill.setColor(0xFF1A1208);
        for (float x : new float[] { 73, 155, 237, 319, 401 })
            for (float y : new float[] { 152, 333 }) { r.set(x, y, x + 38, y + 27); c.drawRoundRect(r, 6, 6, fill); }
        for (float x : new float[] { 73, 199, 325 }) {
            fill.setColor(0xFF1A1208); c.drawRect(x, 194, x + 114, 316, fill);
            fill.setColor(0xFF5C8AAA); c.drawRect(x + 9, 203, x + 105, 263, fill);
            fill.setColor(0xFFBE7846); c.drawRect(x + 9, 263, x + 105, 307, fill);
        }
        c.restore();
    }

    /** the card as a bitmap of its own size, for Playback; RGB_565 halves the memory and the card has no gradients */
    Bitmap bitmap(RecipeCardLayout l, Recipes.Recipe recipe, boolean[] modules, int size) {
        Bitmap b = Bitmap.createBitmap(l.width, l.height, Bitmap.Config.RGB_565);
        draw(new Canvas(b), l, recipe, modules, size);
        return b;
    }
}
