package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Full-screen modal list, Canvas-drawn like PickerView. Two shapes: rows to pick from (name + what it does — the app
 * menu and the developer menu), or a read-only page of name / value lines (About, the key logger). A row may carry a
 * value (panel visibility, settle delay) that left / right change in place: it is drawn at the row's right edge between
 * two arrows, drawn rather than typed because the camera font has no arrow glyphs. Title on top, icon legend at the bottom.
 */
public class MenuView extends View {
    private static final int ACCENT = 0xFFF2B85C, INK = 0xFF1A1208;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), head = new Paint(Paint.ANTI_ALIAS_FLAG),
            item = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG), row = new Paint(Paint.ANTI_ALIAS_FLAG),
            key = new Paint(Paint.ANTI_ALIAS_FLAG), value = new Paint(Paint.ANTI_ALIAS_FLAG), arrow = new Paint(Paint.ANTI_ALIAS_FLAG),
            rule = new Paint(), track = new Paint(Paint.ANTI_ALIAS_FLAG), thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path tri = new Path(), shape = new Path();
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Legend legend;
    private final float d;
    private String title = "";
    private String[] labels = new String[0], details = new String[0], values = new String[0];
    private Typeface face = Typeface.DEFAULT;
    private Typeface[] valueFaces = new Typeface[0];              // a value in another script than the menu's (the Language row)
    private boolean page = false;                                 // read-only name / value lines rather than rows
    private int selected = 0;
    private int[] icons;                                           // DevTools.IC_* per row, or null for none
    private String[] footer;                                       // a page's centred footer (About's sponsor ask), or null
    private Keys.Hints hints = Keys.hints(Keys.H_MENU_TOP, Keys.Caps.UNKNOWN);

    public MenuView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        legend = new Legend(d);
        bg.setColor(0xF0101010);
        head.setColor(ACCENT); head.setTextSize(9 * d); head.setFakeBoldText(true);
        item.setTextSize(13 * d); item.setFakeBoldText(true);
        small.setTextSize(10 * d);
        key.setTextSize(10 * d); key.setFakeBoldText(true); key.setColor(ACCENT);
        value.setTextSize(13 * d); value.setFakeBoldText(true); value.setTextAlign(Paint.Align.CENTER);
        arrow.setStyle(Paint.Style.FILL);
        glyph.setStyle(Paint.Style.STROKE); glyph.setStrokeWidth(1.5f * d); glyph.setStrokeCap(Paint.Cap.ROUND);
        rule.setColor(0x33FFFFFF);
        track.setColor(0x26FFFFFF); thumb.setColor(0xCCF2B85C);
    }

    /** the display language's typeface ({@link UiFont}) */
    public void setTypeface(Typeface tf) {
        face = tf;
        head.setTypeface(tf); item.setTypeface(tf); small.setTypeface(tf); key.setTypeface(tf); value.setTypeface(tf); legend.setTypeface(tf);
        invalidate();
    }

    /**
     * Rows to pick from: their titles, their explanation lines, their values (null for a row without one — or the whole
     * array null), the typeface of a value that is not in the menu's script (null for the menu's own — or the whole
     * array null), which one is highlighted, and the legend under them.
     */
    public void set(String title, String[] labels, String[] details, String[] values, Typeface[] valueFaces, int[] icons, int selected, Keys.Hints hints) {
        this.title = title; this.labels = labels; this.details = details; this.selected = selected; this.hints = hints; page = false;
        this.icons = icons; footer = null;
        this.values = values != null ? values : new String[labels.length];
        this.valueFaces = valueFaces != null ? valueFaces : new Typeface[labels.length];
        requestLayout(); invalidate();
    }

    /** a read-only page: one {name, value} line each */
    public void setPage(String title, String[][] lines, Keys.Hints hints) { setPage(title, lines, null, hints); }

    /** a read-only page with a centred footer under its lines: the first footer line after a heart, the rest below it */
    public void setPage(String title, String[][] lines, String[] footer, Keys.Hints hints) {
        this.title = title; this.hints = hints; page = true; selected = -1; this.footer = footer;
        labels = new String[lines.length]; details = new String[lines.length]; values = new String[lines.length]; valueFaces = new Typeface[lines.length];
        for (int i = 0; i < lines.length; i++) { labels[i] = lines[i][0]; details[i] = lines[i][1]; }
        requestLayout(); invalidate();
    }

    private float rowHeight() { return page ? 20 * d : 38 * d; }

    /** the name column of a page: as wide as its widest name */
    private float keyColumn() { float w = 0; for (String l : labels) w = Math.max(w, key.measureText(l)); return w + 12 * d; }

    @Override
    protected void onMeasure(int w, int hh) { setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(hh)); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 16 * d;
        c.drawRect(0, 0, w, h, bg);
        c.drawText(title, pad, pad + 7 * d, head);
        float legTop = h - pad - legend.height(legend.lines(w - 2 * pad, hints)) + 2 * d;   // the legend, on as many lines as it needs
        float top = pad + 14 * d, bottom = legTop - 6 * d;          // header / footer reserved, as in the brand list
        c.drawLine(pad, top, w - pad, top, rule);

        float y = top + 8 * d, rh = rowHeight();
        int visible = Math.max(1, (int) ((bottom - y) / rh)), n = labels.length;
        int first = n > visible && selected >= 0 ? Math.max(0, Math.min(selected - visible / 2, n - visible)) : 0;
        boolean scroll = n > visible && !page;                     // more rows than fit: a scrollbar, as in the brand list
        float sbW = 4 * d, right = w - pad - (scroll ? sbW + 8 * d : 0), listTop = y;
        if (page) {
            float kc = keyColumn();
            small.setColor(0xDDFFFFFF);
            for (int i = first; i < Math.min(n, first + visible); i++, y += rh) {
                c.drawText(labels[i], pad, y + 13 * d, key);
                c.drawText(details[i], pad + kc, y + 13 * d, small);
            }
            if (footer != null && footer.length > 0) {             // centred: heart + the ask, then the addresses
                float fy = Math.max(y, top + 8 * d + Math.min(n, visible) * rh) + 14 * d;
                small.setTextAlign(Paint.Align.LEFT);
                float tw = small.measureText(footer[0]), hs = 4 * d, x0 = (w - (tw + 4 * hs)) / 2;
                glyph.setStyle(Paint.Style.FILL); glyph.setColor(Legend.HEART_PINK);
                Legend.heart(c, x0 + hs, fy - 3.5f * d, hs, glyph);
                small.setColor(0xFFFFFFFF);
                c.drawText(footer[0], x0 + 4 * hs, fy, small);
                small.setColor(0xCCF2B85C);
                small.setTextAlign(Paint.Align.CENTER);
                for (int k = 1; k < footer.length; k++) c.drawText(footer[k], w / 2, fy + k * 16 * d, small);
                small.setTextAlign(Paint.Align.LEFT);
            }
        } else {
            for (int i = first; i < Math.min(n, first + visible); i++, y += rh) {
                boolean on = i == selected;
                if (on) { r.set(pad - 6 * d, y, right + 6 * d, y + rh - 3 * d); row.setColor(ACCENT); c.drawRoundRect(r, 4 * d, 4 * d, row); }
                item.setColor(on ? INK : 0xFFFFFFFF);
                float tx = pad;
                if (icons != null && i < icons.length) {             // the row's icon, then its text beside it
                    glyph.setColor(on ? INK : ACCENT);
                    drawIcon(c, icons[i], pad + 9 * d, y + rh / 2 - 1.5f * d, 7 * d);
                    tx = pad + 26 * d;
                }
                c.drawText(labels[i], tx, y + 16 * d, item);
                small.setColor(on ? 0xCC1A1208 : 0x99FFFFFF);
                c.drawText(details[i], tx, y + 29 * d, small);
                if (values[i] != null) {
                    value.setTypeface(valueFaces[i] != null ? valueFaces[i] : face);
                    drawValue(c, values[i], right, y + rh / 2 - 1.5f * d, on);
                }
            }
            if (scroll) scrollbar(c, w - pad - sbW + 4 * d, listTop, visible * rh, sbW, first, visible, n);
        }
        c.drawLine(pad, legTop - 2 * d, w - pad, legTop - 2 * d, rule);
        legend.drawWrapped(c, pad, legTop, w - 2 * pad, hints);
    }

    /** "◀ Full ▶" ending at {@code right}, centred on {@code cy}: the arrows say left / right change it */
    private void drawValue(Canvas c, String text, float right, float cy, boolean on) {
        float a = 4.5f * d, gap = 7 * d, tw = Math.max(value.measureText(text), value.measureText("English"));   // a steady width as it changes
        float rx = right - 4 * d, lx = rx - a - gap - tw - gap - a;
        int col = on ? INK : 0xCCFFFFFF;
        arrow.setColor(on ? INK : 0x88FFFFFF); value.setColor(col);
        triangle(c, lx, cy, lx + a, cy - a, lx + a, cy + a);                        // left
        triangle(c, rx, cy, rx - a, cy - a, rx - a, cy + a);                        // right
        c.drawText(text, lx + a + gap + tw / 2, cy - (value.ascent() + value.descent()) / 2f, value);
    }

    /** a menu row's icon, centred on (cx, cy), about 2 * s across, in {@link #glyph} */
    private void drawIcon(Canvas c, int icon, float cx, float cy, float s) {
        glyph.setStyle(Paint.Style.STROKE);
        switch (icon) {
            case DevTools.IC_BROWSE:                                // a list: three bullets and lines
                for (int k = -1; k <= 1; k++) {
                    float y = cy + k * s * 0.65f;
                    c.drawCircle(cx - s * 0.75f, y, d * 0.9f, glyph);
                    c.drawLine(cx - s * 0.35f, y, cx + s, y, glyph);
                }
                return;
            case DevTools.IC_NEW:                                   // a plus in a ring
                c.drawCircle(cx, cy, s, glyph);
                c.drawLine(cx - s * 0.5f, cy, cx + s * 0.5f, cy, glyph);
                c.drawLine(cx, cy - s * 0.5f, cx, cy + s * 0.5f, glyph);
                return;
            case DevTools.IC_PANEL:                                 // a screen with the panel along its foot
                box.set(cx - s, cy - s * 0.75f, cx + s, cy + s * 0.75f);
                c.drawRoundRect(box, 2 * d, 2 * d, glyph);
                c.drawLine(cx - s * 0.6f, cy + s * 0.35f, cx + s * 0.6f, cy + s * 0.35f, glyph);
                return;
            case DevTools.IC_LANGUAGE:                              // a globe: ring, meridian, equator
                c.drawCircle(cx, cy, s, glyph);
                box.set(cx - s * 0.45f, cy - s, cx + s * 0.45f, cy + s);
                c.drawOval(box, glyph);
                c.drawLine(cx - s, cy, cx + s, cy, glyph);
                return;
            case DevTools.IC_RESET: {                               // a circular arrow
                box.set(cx - s * 0.85f, cy - s * 0.85f, cx + s * 0.85f, cy + s * 0.85f);
                c.drawArc(box, -60, 300, false, glyph);
                float ax = cx + s * 0.85f * 0.5f, ay = cy - s * 0.85f * 0.866f;   // where the arc starts, at -60°
                shape.reset(); shape.moveTo(ax + s * 0.45f, ay - s * 0.05f); shape.lineTo(ax - s * 0.05f, ay - s * 0.4f); shape.lineTo(ax - s * 0.05f, ay + s * 0.3f); shape.close();
                glyph.setStyle(Paint.Style.FILL); c.drawPath(shape, glyph);
                return;
            }
            case DevTools.IC_ABOUT:                                 // an i in a ring
                c.drawCircle(cx, cy, s, glyph);
                c.drawLine(cx, cy - s * 0.1f, cx, cy + s * 0.5f, glyph);
                glyph.setStyle(Paint.Style.FILL); c.drawCircle(cx, cy - s * 0.45f, d, glyph);
                return;
            case DevTools.IC_DEV:                                   // < >
                c.drawLine(cx - s * 0.3f, cy - s * 0.6f, cx - s, cy, glyph); c.drawLine(cx - s, cy, cx - s * 0.3f, cy + s * 0.6f, glyph);
                c.drawLine(cx + s * 0.3f, cy - s * 0.6f, cx + s, cy, glyph); c.drawLine(cx + s, cy, cx + s * 0.3f, cy + s * 0.6f, glyph);
                return;
            case DevTools.IC_SNAPSHOT:                              // a camera: body, lens, viewfinder hump
                box.set(cx - s, cy - s * 0.5f, cx + s, cy + s * 0.75f);
                c.drawRoundRect(box, 2 * d, 2 * d, glyph);
                c.drawCircle(cx, cy + s * 0.12f, s * 0.38f, glyph);
                c.drawLine(cx - s * 0.35f, cy - s * 0.5f, cx - s * 0.2f, cy - s * 0.8f, glyph);
                c.drawLine(cx - s * 0.2f, cy - s * 0.8f, cx + s * 0.2f, cy - s * 0.8f, glyph);
                c.drawLine(cx + s * 0.2f, cy - s * 0.8f, cx + s * 0.35f, cy - s * 0.5f, glyph);
                return;
            case DevTools.IC_LOCK:                                  // a padlock: body and shackle
                box.set(cx - s * 0.75f, cy - s * 0.1f, cx + s * 0.75f, cy + s * 0.9f);
                c.drawRoundRect(box, 1.5f * d, 1.5f * d, glyph);
                box.set(cx - s * 0.45f, cy - s * 0.9f, cx + s * 0.45f, cy + s * 0.2f);
                c.drawArc(box, 180, 180, false, glyph);
                c.drawLine(cx - s * 0.45f, cy - s * 0.35f, cx - s * 0.45f, cy - s * 0.1f, glyph);
                c.drawLine(cx + s * 0.45f, cy - s * 0.35f, cx + s * 0.45f, cy - s * 0.1f, glyph);
                return;
            case DevTools.IC_SAMPLES:                               // a gallery: four frames
                for (int k = 0; k < 4; k++) {
                    float x0 = cx + (k % 2 == 0 ? -s : s * 0.12f), y0 = cy + (k < 2 ? -s : s * 0.12f);
                    box.set(x0, y0, x0 + s * 0.88f, y0 + s * 0.88f);
                    c.drawRoundRect(box, d, d, glyph);
                }
                return;
            case DevTools.IC_CLOCK:                                 // a clock
                c.drawCircle(cx, cy, s, glyph);
                c.drawLine(cx, cy, cx, cy - s * 0.6f, glyph);
                c.drawLine(cx, cy, cx + s * 0.45f, cy + s * 0.2f, glyph);
                return;
            case DevTools.IC_KEYS:                                  // a keyboard: case, two rows of keys, space bar
                box.set(cx - s, cy - s * 0.65f, cx + s, cy + s * 0.65f);
                c.drawRoundRect(box, 2 * d, 2 * d, glyph);
                glyph.setStyle(Paint.Style.FILL);
                for (int k = 0; k < 4; k++) {
                    float kx = cx - s * 0.6f + k * s * 0.4f;
                    c.drawCircle(kx, cy - s * 0.25f, d * 0.8f, glyph);
                    c.drawCircle(kx, cy + s * 0.05f, d * 0.8f, glyph);
                }
                glyph.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - s * 0.4f, cy + s * 0.35f, cx + s * 0.4f, cy + s * 0.35f, glyph);
                return;
        }
    }

    /** vertical scrollbar: track + thumb proportional to the visible window, as PickerView draws it */
    private void scrollbar(Canvas c, float x, float top, float height, float width, int first, int visible, int total) {
        r.set(x, top, x + width, top + height); c.drawRoundRect(r, width / 2, width / 2, track);
        float thumbH = Math.max(12 * d, height * visible / total);
        float thumbY = top + (height - thumbH) * first / Math.max(1, total - visible);
        r.set(x, thumbY, x + width, thumbY + thumbH); c.drawRoundRect(r, width / 2, width / 2, thumb);
    }

    private void triangle(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        tri.reset(); tri.moveTo(x1, y1); tri.lineTo(x2, y2); tri.lineTo(x3, y3); tri.close(); c.drawPath(tri, arrow);
    }
}
