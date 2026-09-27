package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Modal list, Canvas-drawn like PromptView. Two shapes: rows to pick from (name + what it does — the app menu and the
 * developer menu), or a read-only page of name / value lines (Controls, About, the key logger). Title on top, icon
 * legend at the bottom.
 */
public class MenuView extends View {
    private static final int ACCENT = 0xFFF2B85C, INK = 0xFF1A1208;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), head = new Paint(Paint.ANTI_ALIAS_FLAG),
            item = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG), row = new Paint(Paint.ANTI_ALIAS_FLAG),
            key = new Paint(Paint.ANTI_ALIAS_FLAG), rule = new Paint();
    private final RectF r = new RectF();
    private final Legend legend;
    private final float d;
    private String title = "";
    private String[] labels = new String[0], details = new String[0];
    private boolean page = false;                                 // read-only name / value lines rather than rows
    private int selected = 0;
    private Keys.Hints hints = Keys.hints(Keys.H_MENU_TOP, Keys.Caps.UNKNOWN);

    public MenuView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        legend = new Legend(d);
        bg.setColor(0xF0141414);
        edge.setColor(0x88F2B85C); edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(d);
        head.setColor(ACCENT); head.setTextSize(9 * d); head.setFakeBoldText(true);
        item.setTextSize(13 * d); item.setFakeBoldText(true);
        small.setTextSize(10 * d);
        key.setTextSize(10 * d); key.setFakeBoldText(true); key.setColor(ACCENT);
        rule.setColor(0x33FFFFFF);
    }

    /** rows to pick from: their titles, their explanation lines, which one is highlighted, and the legend under them */
    public void set(String title, String[] labels, String[] details, int selected, Keys.Hints hints) {
        this.title = title; this.labels = labels; this.details = details; this.selected = selected; this.hints = hints; page = false;
        requestLayout(); invalidate();
    }

    /** a read-only page: one {name, value} line each */
    public void setPage(String title, String[][] lines, Keys.Hints hints) {
        this.title = title; this.hints = hints; page = true; selected = -1;
        labels = new String[lines.length]; details = new String[lines.length];
        for (int i = 0; i < lines.length; i++) { labels[i] = lines[i][0]; details[i] = lines[i][1]; }
        requestLayout(); invalidate();
    }

    private float rowHeight() { return page ? 17 * d : 34 * d; }

    /** the name column of a page: as wide as its widest name */
    private float keyColumn() { float w = 0; for (String l : labels) w = Math.max(w, key.measureText(l)); return w + 12 * d; }

    @Override
    protected void onMeasure(int w, int hh) {
        float wd = head.measureText(title);
        if (page) { float kc = keyColumn(); for (String v : details) wd = Math.max(wd, kc + small.measureText(v)); }
        else for (int i = 0; i < labels.length; i++) wd = Math.max(wd, Math.max(item.measureText(labels[i]), small.measureText(details[i])));
        wd = Math.min(wd + 40 * d, MeasureSpec.getSize(w));
        float h = 14 * d + 12 * d + labels.length * rowHeight() + 12 * d + legend.height() + 12 * d;
        setMeasuredDimension((int) wd, (int) Math.min(h, MeasureSpec.getSize(hh)));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 16 * d;
        r.set(0, 0, w, h); c.drawRoundRect(r, 8 * d, 8 * d, bg); c.drawRoundRect(r, 8 * d, 8 * d, edge);
        c.drawText(title, pad, 14 * d + 7 * d, head);

        float y = 14 * d + 12 * d, rh = rowHeight();
        if (page) {
            float kc = keyColumn();
            small.setColor(0xDDFFFFFF);
            for (int i = 0; i < labels.length; i++, y += rh) {
                c.drawText(labels[i], pad, y + 12 * d, key);
                c.drawText(details[i], pad + kc, y + 12 * d, small);
            }
        } else {
            for (int i = 0; i < labels.length; i++, y += rh) {
                boolean on = i == selected;
                if (on) { r.set(pad - 6 * d, y, w - pad + 6 * d, y + rh - 2 * d); row.setColor(ACCENT); c.drawRoundRect(r, 4 * d, 4 * d, row); }
                item.setColor(on ? INK : 0xFFFFFFFF);
                c.drawText(labels[i], pad, y + 15 * d, item);
                small.setColor(on ? 0xCC1A1208 : 0x99FFFFFF);
                c.drawText(details[i], pad, y + 27 * d, small);
            }
        }
        c.drawLine(pad, y + 2 * d, w - pad, y + 2 * d, rule);
        legend.draw(c, pad, y + 12 * d + legend.height() / 2 - 2 * d, w - 2 * pad, hints);
    }
}
