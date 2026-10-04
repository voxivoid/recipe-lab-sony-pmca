package com.voxivoid.recipelab;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Key legend drawn with Canvas (camera firmware font has no arrow / symbol glyphs).
 * A row that does not fit the width at full size wraps onto more lines ({@link Keys#lineCounts}); within a line the
 * gaps between items squeeze, and only an item wider than the whole line is scaled down.
 */
public class Legend {
    public static final int WHEEL = Keys.I_WHEEL, UPDOWN = Keys.I_UPDOWN, LEFTRIGHT = Keys.I_LEFTRIGHT, DIAL = Keys.I_DIAL,
            ENTER = Keys.I_ENTER, TRASH = Keys.I_TRASH, MENU = Keys.I_MENU, FN = Keys.I_FN;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), keyText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Canvas nowhere = new Canvas();      // measuring pass draws into this
    private final float d;

    public Legend(float density) {
        d = density;
        fill.setColor(0xCCFFFFFF); fill.setStyle(Paint.Style.FILL);
        stroke.setColor(0xCCFFFFFF); stroke.setStyle(Paint.Style.STROKE);
        text.setColor(0x99FFFFFF);
        keyText.setColor(0xCCFFFFFF); keyText.setTextAlign(Paint.Align.CENTER); keyText.setFakeBoldText(true);
    }

    /** the labels' typeface: the display language's ({@link UiFont}); the key caps keep the camera's */
    public void setTypeface(Typeface tf) { text.setTypeface(tf); }

    private static final Path STAR = new Path();

    /** a five-point star centred on (cx, cy) with outer radius r — the favourite mark, shared by every view that draws one */
    public static void star(Canvas c, float cx, float cy, float r, Paint p) {
        STAR.reset();
        for (int i = 0; i < 10; i++) {
            double ang = Math.toRadians(-90 + i * 36);
            float rr = (i % 2 == 0) ? r : r * 0.45f;
            float x = cx + (float) Math.cos(ang) * rr, y = cy + (float) Math.sin(ang) * rr;
            if (i == 0) STAR.moveTo(x, y); else STAR.lineTo(x, y);
        }
        STAR.close();
        c.drawPath(STAR, p);
    }

    private static final Path HEART = new Path();

    /** a heart centred on (cx, cy), about 2 * s across — the sponsor mark: no font the app has carries an emoji or ♥ */
    public static void heart(Canvas c, float cx, float cy, float s, Paint p) {
        HEART.reset();
        HEART.moveTo(cx, cy + s);
        HEART.cubicTo(cx - 2.2f * s, cy - 0.2f * s, cx - 0.9f * s, cy - 1.6f * s, cx, cy - 0.5f * s);
        HEART.cubicTo(cx + 0.9f * s, cy - 1.6f * s, cx + 2.2f * s, cy - 0.2f * s, cx, cy + s);
        HEART.close();
        c.drawPath(HEART, p);
    }

    /** the heart's colour: the pink of the README's sponsor badge */
    public static final int HEART_PINK = 0xFFDB61A2;

    /** natural height for a legend row at scale 1 */
    public float height() { return 16 * d; }

    /** the height of a legend of {@code lines} lines */
    public float height(int lines) { return height() + Math.max(0, lines - 1) * LINE_STEP * d; }

    /** distance between the centres of two wrapped lines, in dp */
    private static final float LINE_STEP = 18, WRAP_GAP = 8;

    /** how many lines a row needs in {@code width} at full size */
    public int lines(float width, Keys.Hints h) { return lineCounts(width, h).length; }

    private int[] lineCounts(float width, Keys.Hints h) {
        setScale(1f);
        float s = 6 * d, gap = WRAP_GAP * d;
        float[] w = new float[h.icons.length];
        for (int i = 0; i < w.length; i++) w[i] = keys(nowhere, h.icons[i], h.alts[i], 0, 0, s, 0, 1f) + 4 * d + text.measureText(h.labels[i]);
        return Keys.lineCounts(w, width, gap);
    }

    /** draws a legend row from {@code top}, wrapped onto as many lines as it needs; returns the height used */
    public float drawWrapped(Canvas c, float x, float top, float width, Keys.Hints h) {
        int[] counts = lineCounts(width, h);
        float cy = top + height() / 2;
        for (int line = 0, at = 0; line < counts.length; at += counts[line], line++, cy += LINE_STEP * d) {
            int n = counts[line];
            int[] icons = new int[n], alts = new int[n]; String[] labels = new String[n];
            System.arraycopy(h.icons, at, icons, 0, n); System.arraycopy(h.alts, at, alts, 0, n); System.arraycopy(h.labels, at, labels, 0, n);
            draw(c, x, cy, width, icons, alts, labels);
        }
        return height(counts.length);
    }

    /** draws a legend row built by {@link Keys#hints}: a shortcut icon, where there is one, goes before its key as "Fn / MENU" */
    public float draw(Canvas c, float x, float cy, float width, Keys.Hints h) { return draw(c, x, cy, width, h.icons, h.alts, h.labels); }

    /** draws icons+labels starting at x, vertically centred on cy, within width; returns the width actually used */
    public float draw(Canvas c, float x, float cy, float width, int[] icons, String[] labels) { return draw(c, x, cy, width, icons, null, labels); }

    private float draw(Canvas c, float x, float cy, float width, int[] icons, int[] alts, String[] labels) {
        float scale = 1f, gap = 14 * d, minGap = 5 * d;
        float need = measure(scale, minGap, icons, alts, labels);
        if (need > width) scale = Math.max(0.6f, width / need);          // shrink everything, gaps stay minimal
        float used = measure(scale, minGap, icons, alts, labels);
        if (used < width) gap = Math.min(14 * d, minGap + (width - used) / Math.max(1, icons.length - 1));
        else gap = minGap;
        setScale(scale);
        float s = 6 * d * scale, ty = cy - (text.ascent() + text.descent()) / 2f, x0 = x;
        for (int i = 0; i < icons.length; i++) {
            x += keys(c, icons[i], alts == null ? Keys.I_NONE : alts[i], x, cy, s, ty, scale) + 4 * d * scale;
            c.drawText(labels[i], x, ty, text);
            x += text.measureText(labels[i]) + (i < icons.length - 1 ? gap : 0);
        }
        return x - x0;
    }

    private float measure(float scale, float gap, int[] icons, int[] alts, String[] labels) {
        setScale(scale);
        float s = 6 * d * scale, w = 0;
        for (int i = 0; i < icons.length; i++)
            w += keys(nowhere, icons[i], alts == null ? Keys.I_NONE : alts[i], 0, 0, s, 0, scale) + 4 * d * scale + text.measureText(labels[i]) + (i < icons.length - 1 ? gap : 0);
        return w;
    }

    /** the key icon of one item, with its shortcut icon and a slash before it when it has one; returns the width */
    private float keys(Canvas c, int icon, int alt, float x, float cy, float s, float ty, float scale) {
        if (alt == Keys.I_NONE) return icon(c, icon, x, cy, s);
        float x0 = x;
        x += icon(c, alt, x, cy, s) + 2 * d * scale;
        c.drawText("/", x, ty, text);
        x += text.measureText("/") + 2 * d * scale;
        x += icon(c, icon, x, cy, s);
        return x - x0;
    }

    private void setScale(float k) {
        text.setTextSize(10 * d * k);
        keyText.setTextSize(6.5f * d * k);
        stroke.setStrokeWidth(1.2f * d * k);
    }

    private void tri(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close(); c.drawPath(path, fill);
    }

    private float keyLabel(Canvas c, float x, float cy, float s, float w, String label) {
        rect.set(x, cy - s * 0.8f, x + w, cy + s * 0.8f);
        c.drawRoundRect(rect, 2 * d, 2 * d, stroke);
        c.drawText(label, x + w / 2, cy - (keyText.ascent() + keyText.descent()) / 2f, keyText);
        return w;
    }

    /** draws icon with left edge at x, vertically centred on cy, half-size s; returns width */
    private float icon(Canvas c, int icon, float x, float cy, float s) {
        float a = s * 0.55f, k = s / (6 * d);                     // a = arrow size, k = scale
        switch (icon) {
            case WHEEL: {                                          // ring + left/right arrows outside
                float cx = x + a + s + 1.5f * d * k;
                c.drawCircle(cx, cy, s * 0.8f, stroke);
                c.drawCircle(cx, cy, s * 0.25f, fill);
                tri(c, x, cy, x + a, cy - a * 0.8f, x + a, cy + a * 0.8f);
                float r = cx + s + 1.5f * d * k;
                tri(c, r + a, cy, r, cy - a * 0.8f, r, cy + a * 0.8f);
                return 2 * a + 2 * s + 3 * d * k;
            }
            case DIAL: {                                           // top dial: arc with ticks + arrows
                float cx = x + a + s + 1.5f * d * k;
                rect.set(cx - s, cy - s * 0.4f, cx + s, cy + s * 1.6f);
                c.drawArc(rect, 200, 140, false, stroke);
                for (int i = -2; i <= 2; i++) {
                    double ang = Math.toRadians(270 + i * 28);
                    float ox = (float) Math.cos(ang), oy = (float) Math.sin(ang), ccy = cy + s * 0.6f;
                    c.drawLine(cx + ox * s * 0.75f, ccy + oy * s * 0.75f, cx + ox * s, ccy + oy * s, stroke);
                }
                tri(c, x, cy, x + a, cy - a * 0.8f, x + a, cy + a * 0.8f);
                float r = cx + s + 1.5f * d * k;
                tri(c, r + a, cy, r, cy - a * 0.8f, r, cy + a * 0.8f);
                return 2 * a + 2 * s + 3 * d * k;
            }
            case UPDOWN: {
                float cx = x + a;
                tri(c, cx, cy - s, cx - a * 0.8f, cy - s + a, cx + a * 0.8f, cy - s + a);
                tri(c, cx, cy + s, cx - a * 0.8f, cy + s - a, cx + a * 0.8f, cy + s - a);
                return 2 * a;
            }
            case LEFTRIGHT: {
                tri(c, x, cy, x + a, cy - a * 0.8f, x + a, cy + a * 0.8f);
                float r = x + a + 3 * d * k;
                tri(c, r + a, cy, r, cy - a * 0.8f, r, cy + a * 0.8f);
                return 2 * a + 3 * d * k;
            }
            case ENTER: {                                          // centre button: ring + dot
                float cx = x + s;
                c.drawCircle(cx, cy, s * 0.85f, stroke);
                c.drawCircle(cx, cy, s * 0.4f, fill);
                return 2 * s;
            }
            case FN: return keyLabel(c, x, cy, s, 2.0f * s, "Fn");
            case TRASH: {                                          // bin: lid + body
                float w = 1.6f * s, cx = x + w / 2, top = cy - s * 0.9f, bot = cy + s * 0.9f, u = d * k;
                c.drawLine(x, top + 2 * u, x + w, top + 2 * u, stroke);
                c.drawLine(cx - 2 * u, top, cx + 2 * u, top, stroke);
                rect.set(x + 2 * u, top + 2 * u, x + w - 2 * u, bot);
                c.drawRoundRect(rect, 1.5f * u, 1.5f * u, stroke);
                c.drawLine(cx, top + 5 * u, cx, bot - 3 * u, stroke);
                return w;
            }
            case MENU: {                                           // rounded key with three lines
                float u = d * k;
                rect.set(x, cy - s * 0.8f, x + 2 * s, cy + s * 0.8f);
                c.drawRoundRect(rect, 2 * u, 2 * u, stroke);
                for (int i = -1; i <= 1; i++) c.drawLine(x + 3.5f * u, cy + i * 3 * u, x + 2 * s - 3.5f * u, cy + i * 3 * u, stroke);
                return 2 * s;
            }
        }
        return 0;
    }
}
