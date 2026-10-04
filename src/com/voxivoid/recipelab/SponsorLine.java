package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * The small line at the top of the live view on the Full panel (No keys, Label and Hidden hide it): a heart and the
 * sponsor ask, as the README opens with. The heart is drawn — neither the camera font nor the bundled Chinese ones have
 * an emoji or a ♥ glyph. The About page lists the addresses (DevTools.about).
 */
public class SponsorLine extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), heart = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;
    private String line = "";

    public SponsorLine(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        text.setColor(0xB3FFFFFF); text.setTextSize(9 * d);
        text.setShadowLayer(2 * d, 0, d, 0xAA000000);
        heart.setColor(0xFFDB61A2);                                 // the pink of the README's sponsor badge
    }

    public void setTypeface(Typeface tf) { text.setTypeface(tf); requestLayout(); invalidate(); }

    public void setText(String s) { if (!s.equals(line)) { line = s; requestLayout(); invalidate(); } }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) (14 * d + text.measureText(line)) + 1, (int) (14 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = 4 * d, cx = s + d, cy = getHeight() / 2f;
        // a heart: two lobes and a point, from a path
        path.reset();
        path.moveTo(cx, cy + s);
        path.cubicTo(cx - 2.2f * s, cy - 0.2f * s, cx - 0.9f * s, cy - 1.6f * s, cx, cy - 0.5f * s);
        path.cubicTo(cx + 0.9f * s, cy - 1.6f * s, cx + 2.2f * s, cy - 0.2f * s, cx, cy + s);
        path.close();
        c.drawPath(path, heart);
        c.drawText(line, 14 * d, cy - (text.ascent() + text.descent()) / 2, text);
    }
}
