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
            rule = new Paint();
    private final RectF r = new RectF();
    private final Path tri = new Path();
    private final Legend legend;
    private final float d;
    private String title = "";
    private String[] labels = new String[0], details = new String[0], values = new String[0];
    private Typeface face = Typeface.DEFAULT;
    private Typeface[] valueFaces = new Typeface[0];              // a value in another script than the menu's (the Language row)
    private boolean page = false;                                 // read-only name / value lines rather than rows
    private int selected = 0;
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
        rule.setColor(0x33FFFFFF);
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
    public void set(String title, String[] labels, String[] details, String[] values, Typeface[] valueFaces, int selected, Keys.Hints hints) {
        this.title = title; this.labels = labels; this.details = details; this.selected = selected; this.hints = hints; page = false;
        this.values = values != null ? values : new String[labels.length];
        this.valueFaces = valueFaces != null ? valueFaces : new Typeface[labels.length];
        requestLayout(); invalidate();
    }

    /** a read-only page: one {name, value} line each */
    public void setPage(String title, String[][] lines, Keys.Hints hints) {
        this.title = title; this.hints = hints; page = true; selected = -1;
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
        float top = pad + 14 * d, bottom = h - pad - 20 * d;       // header / footer reserved, as in the brand list
        c.drawLine(pad, top, w - pad, top, rule);

        float y = top + 8 * d, rh = rowHeight();
        int visible = Math.max(1, (int) ((bottom - y) / rh)), n = labels.length;
        int first = n > visible && selected >= 0 ? Math.max(0, Math.min(selected - visible / 2, n - visible)) : 0;
        if (page) {
            float kc = keyColumn();
            small.setColor(0xDDFFFFFF);
            for (int i = first; i < Math.min(n, first + visible); i++, y += rh) {
                c.drawText(labels[i], pad, y + 13 * d, key);
                c.drawText(details[i], pad + kc, y + 13 * d, small);
            }
        } else {
            for (int i = first; i < Math.min(n, first + visible); i++, y += rh) {
                boolean on = i == selected;
                if (on) { r.set(pad - 6 * d, y, w - pad + 6 * d, y + rh - 3 * d); row.setColor(ACCENT); c.drawRoundRect(r, 4 * d, 4 * d, row); }
                item.setColor(on ? INK : 0xFFFFFFFF);
                c.drawText(labels[i], pad, y + 16 * d, item);
                small.setColor(on ? 0xCC1A1208 : 0x99FFFFFF);
                c.drawText(details[i], pad, y + 29 * d, small);
                if (values[i] != null) {
                    value.setTypeface(valueFaces[i] != null ? valueFaces[i] : face);
                    drawValue(c, values[i], w - pad, y + rh / 2 - 1.5f * d, on);
                }
            }
        }
        c.drawLine(pad, h - pad - 16 * d, w - pad, h - pad - 16 * d, rule);
        legend.draw(c, pad, h - pad - 6 * d, w - 2 * pad, hints);
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

    private void triangle(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        tri.reset(); tri.moveTo(x1, y1); tri.lineTo(x2, y2); tri.lineTo(x3, y3); tri.close(); c.drawPath(tri, arrow);
    }
}
