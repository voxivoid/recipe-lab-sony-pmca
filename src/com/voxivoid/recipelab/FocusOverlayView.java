package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The autofocus boxes over the live view ({@link FocusOverlayModel} decides which and where). Line style as well as
 * colour tells the states apart: amber dashed while acquiring, green solid and heavier on a lock, amber double on a
 * warning, white thin in continuous AF — each over a faint dark line for bright scenes. Takes no keys and no focus.
 */
public class FocusOverlayView extends View {
    private static final int AMBER = 0xFFF2B85C, GREEN = 0xFF4CD964, WHITE = 0xE6FFFFFF, SHADE = 0x80000000;
    private final Paint shade, acquiring, locked, warning, continuous;
    private final float d;
    private final List<int[]> boxes = new ArrayList<int[]>();
    private int state = FocusOverlayModel.IDLE;

    public FocusOverlayView(Context c, AttributeSet a) {
        super(c, a);
        setClickable(false); setFocusable(false);
        d = c.getResources().getDisplayMetrics().density;
        shade = stroke(SHADE, 4 * d);
        acquiring = stroke(AMBER, 2 * d);
        acquiring.setPathEffect(new DashPathEffect(new float[] { 6 * d, 4 * d }, 0));
        locked = stroke(GREEN, 3 * d);
        warning = stroke(AMBER, 2 * d);
        continuous = stroke(WHITE, 2 * d);
    }

    private static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE); p.setColor(color); p.setStrokeWidth(width);
        return p;
    }

    /** the state and its boxes, in view pixels {left, top, right, bottom}; no boxes draws nothing */
    void set(int state, List<int[]> boxes) {
        this.state = state;
        this.boxes.clear(); this.boxes.addAll(boxes);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        Paint p = state == FocusOverlayModel.LOCKED ? locked : state == FocusOverlayModel.WARNING ? warning
                : state == FocusOverlayModel.CONTINUOUS ? continuous : state == FocusOverlayModel.ACQUIRING ? acquiring : null;
        if (p == null) return;
        float in = 4 * d;                                        // the warning's second line, inside the first
        for (int[] b : boxes) {
            c.drawRect(b[0], b[1], b[2], b[3], shade);
            c.drawRect(b[0], b[1], b[2], b[3], p);
            if (p == warning && b[2] - b[0] > 2 * in && b[3] - b[1] > 2 * in) c.drawRect(b[0] + in, b[1] + in, b[2] - in, b[3] - in, p);
        }
    }
}
