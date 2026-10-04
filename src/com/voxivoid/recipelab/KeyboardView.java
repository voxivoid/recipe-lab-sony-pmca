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
 * The name editor, full screen and Canvas-drawn like MenuView: a title, the name field, the reason the last OK was
 * refused, the keyboard grid of {@link NameEntry}, and the legend. The placeholder name is drawn dimmed, to say the
 * first character replaces it; a typed name ends in a cursor. Shift and delete are drawn, not typed — the camera font
 * has no arrow glyphs.
 */
public class KeyboardView extends View {
    private static final int ACCENT = 0xFFF2B85C, INK = 0xFF1A1208;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), head = new Paint(Paint.ANTI_ALIAS_FLAG), field = new Paint(Paint.ANTI_ALIAS_FLAG),
            box = new Paint(Paint.ANTI_ALIAS_FLAG), err = new Paint(Paint.ANTI_ALIAS_FLAG), cell = new Paint(Paint.ANTI_ALIAS_FLAG),
            label = new Paint(Paint.ANTI_ALIAS_FLAG), glyph = new Paint(Paint.ANTI_ALIAS_FLAG), rule = new Paint();
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Legend legend;
    private final float d;
    private String title = "";
    private NameEntry entry = NameEntry.blank("");
    private Keys.Hints hints = Keys.hints(Keys.H_NAME, Keys.Caps.UNKNOWN);

    public KeyboardView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        legend = new Legend(d);
        bg.setColor(0xF0101010);
        head.setColor(ACCENT); head.setTextSize(9 * d); head.setFakeBoldText(true);
        field.setTextSize(17 * d); field.setFakeBoldText(true);
        box.setColor(ACCENT); box.setStyle(Paint.Style.STROKE); box.setStrokeWidth(1.5f * d);
        err.setColor(0xFFFF8A65); err.setTextSize(10 * d);
        label.setTextSize(13 * d); label.setFakeBoldText(true); label.setTextAlign(Paint.Align.CENTER);
        glyph.setStyle(Paint.Style.STROKE); glyph.setStrokeWidth(1.5f * d);
        rule.setColor(0x33FFFFFF);
    }

    /** the display language's typeface ({@link UiFont}) */
    public void setTypeface(Typeface tf) {
        head.setTypeface(tf); field.setTypeface(tf); err.setTypeface(tf); label.setTypeface(tf); legend.setTypeface(tf);
        invalidate();
    }

    public void set(String title, NameEntry entry, Keys.Hints hints) { this.title = title; this.entry = entry; this.hints = hints; invalidate(); }

    @Override
    protected void onMeasure(int w, int hh) { setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(hh)); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 16 * d;
        c.drawRect(0, 0, w, h, bg);
        c.drawText(title.toUpperCase(), pad, pad + 7 * d, head);
        float top = pad + 14 * d;
        c.drawLine(pad, top, w - pad, top, rule);

        // ---- the field
        float fy = top + 10 * d, fh = 32 * d;
        r.set(pad, fy, w - pad, fy + fh); c.drawRoundRect(r, 4 * d, 4 * d, box);
        String text = entry.shown();
        field.setColor(entry.isPlaceholder() ? 0x66FFFFFF : 0xFFFFFFFF);
        float base = fy + fh / 2 - (field.ascent() + field.descent()) / 2, tx = pad + 10 * d;
        c.drawText(text, tx, base, field);
        if (!entry.isPlaceholder()) {                           // the cursor after what is typed
            float cx = tx + field.measureText(text) + 2 * d;
            field.setColor(ACCENT);
            c.drawRect(cx, fy + 7 * d, cx + 2 * d, fy + fh - 7 * d, field);
        }
        float ey = fy + fh + 14 * d;
        if (entry.error() != null) c.drawText(entry.error(), pad, ey, err);

        // ---- the keyboard
        float gTop = ey + 8 * d, gBottom = h - pad - 24 * d, rows = NameEntry.rows();
        float rh = Math.min(34 * d, (gBottom - gTop) / rows), gw = w - 2 * pad, gap = 3 * d;
        for (int row = 0; row < rows; row++) {
            int n = NameEntry.cols(row);
            float cw = gw / n, y = gTop + row * rh;
            for (int col = 0; col < n; col++) {
                boolean on = row == entry.row() && col == entry.col();
                float x = pad + col * cw;
                r.set(x + gap / 2, y + gap / 2, x + cw - gap / 2, y + rh - gap / 2);
                cell.setColor(on ? ACCENT : 0x26FFFFFF);
                c.drawRoundRect(r, 4 * d, 4 * d, cell);
                int ink = on ? INK : 0xFFFFFFFF;
                label.setColor(ink); glyph.setColor(ink);
                drawCell(c, NameEntry.cell(row, col), r.centerX(), r.centerY());
            }
        }

        c.drawLine(pad, h - pad - 16 * d, w - pad, h - pad - 16 * d, rule);
        legend.draw(c, pad, h - pad - 6 * d, w - 2 * pad, hints);
    }

    private void drawCell(Canvas c, char k, float cx, float cy) {
        float ty = cy - (label.ascent() + label.descent()) / 2, s = 5 * d;
        switch (k) {
            case NameEntry.OK: c.drawText(Lang.t("key_ok"), cx, ty, label); return;
            case ' ': c.drawText(Lang.t("key_space"), cx, ty, label); return;
            case NameEntry.SHIFT: {                             // an up arrow, filled while letters type in capitals
                path.reset();
                path.moveTo(cx, cy - s); path.lineTo(cx + s, cy); path.lineTo(cx + s / 2, cy); path.lineTo(cx + s / 2, cy + s);
                path.lineTo(cx - s / 2, cy + s); path.lineTo(cx - s / 2, cy); path.lineTo(cx - s, cy); path.close();
                glyph.setStyle(entry.upper() ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
                c.drawPath(path, glyph);
                glyph.setStyle(Paint.Style.STROKE);
                return;
            }
            case NameEntry.BACK: {                              // a key with a cross: delete
                path.reset();
                path.moveTo(cx - 1.6f * s, cy); path.lineTo(cx - 0.8f * s, cy - s); path.lineTo(cx + 1.4f * s, cy - s);
                path.lineTo(cx + 1.4f * s, cy + s); path.lineTo(cx - 0.8f * s, cy + s); path.close();
                c.drawPath(path, glyph);
                c.drawLine(cx - 0.1f * s, cy - 0.45f * s, cx + 0.8f * s, cy + 0.45f * s, glyph);
                c.drawLine(cx - 0.1f * s, cy + 0.45f * s, cx + 0.8f * s, cy - 0.45f * s, glyph);
                return;
            }
            default:
                char shown = Character.isLetter(k) && !entry.upper() ? Character.toLowerCase(k) : k;
                c.drawText(String.valueOf(shown), cx, ty, label);
        }
    }
}
