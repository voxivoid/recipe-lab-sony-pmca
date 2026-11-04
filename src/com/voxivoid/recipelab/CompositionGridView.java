package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * The rule-of-thirds grid over the live view (Display overlays): two lines each way at a third and two thirds of the
 * preview image ({@link DisplayOptions#contentRect}), so letterbox bars are left alone. Passive: no keys, no camera.
 */
public class CompositionGridView extends View {
    private final Paint line = new Paint();
    private int previewW, previewH;                                // 0 while the camera has not said

    public CompositionGridView(Context c, AttributeSet a) {
        super(c, a);
        line.setColor(0x66FFFFFF);
        line.setStrokeWidth(Math.max(1, c.getResources().getDisplayMetrics().density));
    }

    /** the camera's preview size, which says where in the view the image is */
    public void setPreviewSize(int w, int h) { previewW = w; previewH = h; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        int[] r = DisplayOptions.contentRect(getWidth(), getHeight(), previewW, previewH);
        for (int k = 1; k <= 2; k++) {
            float x = r[0] + (r[2] - r[0]) * k / 3f, y = r[1] + (r[3] - r[1]) * k / 3f;
            c.drawLine(x, r[1], x, r[3], line);
            c.drawLine(r[0], y, r[2], y, line);
        }
    }
}
