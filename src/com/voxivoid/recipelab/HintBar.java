package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

/** Key legend under the main panel, for the keys this body has ({@link Keys#hints}). */
public class HintBar extends View {
    public static final int RECIPE = Keys.H_RECIPE, CHIPS = Keys.H_CHIPS, EDIT = Keys.H_EDIT;

    private final Legend legend;
    private int mode = RECIPE;
    private Keys.Caps caps = Keys.Caps.UNKNOWN;

    public HintBar(Context c, AttributeSet a) {
        super(c, a);
        legend = new Legend(c.getResources().getDisplayMetrics().density);
    }

    public void setMode(int m) { if (mode != m) { mode = m; invalidate(); } }

    /** what the key probe found; until it is set only the universal keys are named */
    public void setCaps(Keys.Caps k) { caps = k; invalidate(); }

    @Override
    protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) legend.height()); }

    @Override
    protected void onDraw(Canvas c) {
        legend.draw(c, 0, getHeight() / 2f, getWidth(), Keys.hints(mode, caps));
    }
}
