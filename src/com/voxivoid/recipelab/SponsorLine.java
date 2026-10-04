package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * The small line at the top of the live view on the Full panel (No keys, Label and Hidden hide it): a heart and the
 * sponsor ask, as the README opens with. The heart is drawn — neither the camera font nor the bundled Chinese ones have
 * an emoji or a ♥ glyph. The About page lists the addresses (DevTools.about).
 */
public class SponsorLine extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), heart = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    /** padding inside the dark pill, in dp */
    private static final float PAD = 6;
    private final float d;
    private String line = "";

    public SponsorLine(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        text.setColor(0xDDFFFFFF); text.setTextSize(9 * d);
        bg.setColor(0x99000000);                                    // a dark pill, see-through: readable over a bright scene
        heart.setColor(Legend.HEART_PINK);
    }

    public void setTypeface(Typeface tf) { text.setTypeface(tf); requestLayout(); invalidate(); }

    public void setText(String s) { if (!s.equals(line)) { line = s; requestLayout(); invalidate(); } }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) (2 * PAD * d + 14 * d + text.measureText(line)) + 1, (int) (16 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        r.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(r, getHeight() / 2f, getHeight() / 2f, bg);
        float s = 4 * d, cx = PAD * d + s + d, cy = getHeight() / 2f;
        Legend.heart(c, cx, cy, s, heart);
        c.drawText(line, PAD * d + 14 * d, cy - (text.ascent() + text.descent()) / 2, text);
    }
}
