package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal question: title and explanation (word-wrapped to the box), two or more option pills that left / right walk,
 * icon legend. Canvas-drawn. Pills that would overflow the box are drawn smaller rather than cut.
 */
public class PromptView extends View {
    private static final int ACCENT = 0xFFF2B85C, INK = 0xFF1A1208;
    private static final int[] LEGEND_ICONS = { Legend.ENTER, Legend.MENU };

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), title = new Paint(Paint.ANTI_ALIAS_FLAG),
            body = new Paint(Paint.ANTI_ALIAS_FLAG), opt = new Paint(Paint.ANTI_ALIAS_FLAG), pill = new Paint(Paint.ANTI_ALIAS_FLAG), note = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Legend legend;
    private final float d;
    private String titleText = "", bodyText = "", noteText = null;
    private String[] options = new String[0];
    private int selected = 0;
    private final List<String> titleLines = new ArrayList<String>(), bodyLines = new ArrayList<String>();

    public PromptView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        legend = new Legend(d);
        bg.setColor(0xF0141414);
        edge.setColor(0x88F2B85C); edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(d);
        title.setColor(0xFFFFFFFF); title.setTextSize(15 * d); title.setFakeBoldText(true);
        body.setColor(0xCCFFFFFF); body.setTextSize(12 * d);
        opt.setTextSize(13 * d); opt.setFakeBoldText(true); opt.setTextAlign(Paint.Align.CENTER);
        note.setColor(0x88FFFFFF); note.setTextSize(10 * d);
    }

    /** the display language's typeface ({@link UiFont}) */
    public void setTypeface(Typeface tf) {
        title.setTypeface(tf); body.setTypeface(tf); opt.setTypeface(tf); note.setTypeface(tf); legend.setTypeface(tf);
        requestLayout(); invalidate();
    }

    public void set(String titleText, String bodyText, String[] options, int selected, String noteText) {
        this.titleText = titleText; this.bodyText = bodyText; this.options = options; this.selected = selected; this.noteText = noteText;
        requestLayout(); invalidate();                          // a different question needs a different box
    }

    private float titleStep() { return 19 * d; }
    private float bodyStep() { return 15 * d; }

    @Override
    protected void onMeasure(int w, int hh) {
        float max = Math.min(MeasureSpec.getSize(w) - 32 * d, 360 * d), pad = 16 * d;
        float wd = Math.max(title.measureText(titleText), body.measureText(bodyText)) + 2 * pad;
        float ow = 0; for (String o : options) ow += opt.measureText(o) + 36 * d;
        wd = Math.min(Math.max(wd, ow + 20 * d), max);
        wrap(titleText, title, wd - 2 * pad, titleLines);
        wrap(bodyText, body, wd - 2 * pad, bodyLines);
        float h = 14 * d + titleLines.size() * titleStep() + bodyLines.size() * bodyStep() + 12 * d + 30 * d + 14 * d
                + (noteText != null ? 14 * d : 0) + legend.height() + 12 * d;
        setMeasuredDimension((int) wd, (int) h);
    }

    /**
     * Breaks {@code text} into lines no wider than {@code width}: at spaces, and inside a word wider than the line —
     * which a Chinese sentence, having no spaces, always is — where the line is full.
     */
    private static void wrap(String text, Paint p, float width, List<String> out) {
        out.clear();
        String line = "";
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String next = line.isEmpty() ? word : line + " " + word;
            if (p.measureText(next) <= width) { line = next; continue; }
            if (!line.isEmpty()) out.add(line);
            while (p.measureText(word) > width) {
                int n = Math.max(1, p.breakText(word, true, width, null));
                out.add(word.substring(0, n)); word = word.substring(n);
            }
            line = word;
        }
        if (!line.isEmpty() || out.isEmpty()) out.add(line);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 16 * d;
        r.set(0, 0, w, h); c.drawRoundRect(r, 8 * d, 8 * d, bg); c.drawRoundRect(r, 8 * d, 8 * d, edge);
        float y = 14 * d + 15 * d;
        for (String l : titleLines) { c.drawText(l, pad, y, title); y += titleStep(); }
        y -= titleStep() - 18 * d;                              // the gap under the title, as before
        for (String l : bodyLines) { c.drawText(l, pad, y, body); y += bodyStep(); }
        y -= bodyStep() - 12 * d;

        // option pills, centred, smaller when they would not fit
        float k = 1f, total;
        while (true) {
            opt.setTextSize(13 * d * k);
            total = 0; for (String o : options) total += opt.measureText(o) + 28 * d * k;
            total += (options.length - 1) * 8 * d * k;
            if (total <= w - 2 * 8 * d || k <= 0.6f) break;
            k -= 0.05f;
        }
        float x = (w - total) / 2, ph = 24 * d, py = y + 3 * d;
        for (int i = 0; i < options.length; i++) {
            float pw = opt.measureText(options[i]) + 28 * d * k;
            r.set(x, py, x + pw, py + ph);
            pill.setColor(i == selected ? ACCENT : 0x33FFFFFF);
            c.drawRoundRect(r, 5 * d, 5 * d, pill);
            opt.setColor(i == selected ? INK : 0xFFFFFFFF);
            c.drawText(options[i], x + pw / 2, py + ph / 2 - (opt.ascent() + opt.descent()) / 2, opt);
            x += pw + 8 * d * k;
        }
        opt.setTextSize(13 * d);
        y = py + ph + 14 * d;
        if (noteText != null) {
            float ns = 10 * d, avail = w - 2 * pad;
            while (note.measureText(noteText) > avail && ns > 7 * d) { ns -= 0.5f * d; note.setTextSize(ns); }
            c.drawText(noteText, pad, y, note); note.setTextSize(10 * d); y += 14 * d;
        }
        legend.draw(c, pad, y + legend.height() / 2 - 2 * d, w - 2 * pad, LEGEND_ICONS, new String[] { Lang.t("action_confirm"), Lang.t("action_cancel") });
    }
}
