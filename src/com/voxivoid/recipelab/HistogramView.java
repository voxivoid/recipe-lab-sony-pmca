package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * The live luminance histogram, top right of the live view (Display overlays): dark on the left, bright on the right,
 * on a see-through dark plate. It draws only heights it is given ({@link HistogramMath#heights}) — with none, nothing at
 * all, never a placeholder curve. No camera access here; {@link SonyHistogramSource} feeds it.
 */
public class HistogramView extends View {
    private final Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            guide = new Paint(), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF r = new RectF();
    private final float d;
    private float[] heights;                                       // null: nothing valid to draw

    public HistogramView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        plate.setColor(0x80000000);
        edge.setColor(0x40FFFFFF); edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(Math.max(1, d));
        guide.setColor(0x26FFFFFF); guide.setStrokeWidth(Math.max(1, d));
        fill.setColor(0xD9FFFFFF);
    }

    /** a new frame's bar heights (each 0..1), or null to clear the graph; the array is not changed after it is handed in */
    public void setHeights(float[] h) {
        if (h == null && heights == null) return;
        heights = h;
        invalidate();
    }

    /** the layout's size, but never wider than 30% of the screen */
    @Override
    protected void onMeasure(int w, int h) {
        int max = (int) (0.3f * getResources().getDisplayMetrics().widthPixels);
        setMeasuredDimension(Math.min(MeasureSpec.getSize(w), max), MeasureSpec.getSize(h));
    }

    @Override
    protected void onDraw(Canvas c) {
        float[] h = heights;
        if (h == null) return;
        float w = getWidth(), ht = getHeight(), pad = 4 * d;
        r.set(0, 0, w, ht);
        c.drawRoundRect(r, 4 * d, 4 * d, plate);
        c.drawRoundRect(r, 4 * d, 4 * d, edge);
        float left = pad, right = w - pad, top = pad, bottom = ht - pad, bar = (right - left) / h.length;
        for (int k = 1; k <= 2; k++) { float x = left + (right - left) * k / 3; c.drawLine(x, top, x, bottom, guide); }
        shape.reset();
        shape.moveTo(left, bottom);
        for (int i = 0; i < h.length; i++) {
            float y = bottom - h[i] * (bottom - top);
            shape.lineTo(left + i * bar, y);
            shape.lineTo(left + (i + 1) * bar, y);
        }
        shape.lineTo(right, bottom);
        shape.close();
        c.drawPath(shape, fill);
    }
}
