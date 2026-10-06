package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** Key legend under the main panel, for the keys this body has ({@link Keys#hints}), on as many lines as it needs. */
public class HintBar extends View {
    public static final int RECIPE = Keys.H_RECIPE, CHIPS = Keys.H_CHIPS, EDIT = Keys.H_EDIT;

    private final Legend legend;
    private int mode = RECIPE;
    private Keys.Caps caps = Keys.Caps.UNKNOWN;

    public HintBar(Context c, AttributeSet a) {
        super(c, a);
        legend = new Legend(c.getResources().getDisplayMetrics().density);
    }

    public void setMode(int m) { if (mode != m) { mode = m; requestLayout(); invalidate(); } }   // another row may need another line count

    /** what the key probe found; until it is set only the universal keys are named */
    public void setCaps(Keys.Caps k) { caps = k; requestLayout(); invalidate(); }

    /** the display language's typeface ({@link UiFont}); the labels themselves come from {@link Keys#hints} as it draws */
    public void setTypeface(Typeface tf) { legend.setTypeface(tf); requestLayout(); invalidate(); }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) Math.ceil(legend.height(legend.lines(width, Keys.hints(mode, caps)))));
    }

    @Override
    protected void onDraw(Canvas c) {
        legend.drawWrapped(c, 0, 0, getWidth(), Keys.hints(mode, caps));
    }
}
