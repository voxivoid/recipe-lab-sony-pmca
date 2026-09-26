package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

/** Key legend under the main panel. */
public class HintBar extends View {
    public static final int RECIPE = 0, CHIPS = 1, EDIT = 2;
    private static final int[][] ICONS = {
        { Legend.FN, Legend.ENTER, Legend.ENTER, Legend.TRASH, Legend.AEL, Legend.MENU },
        { Legend.ENTER, Legend.FN, Legend.TRASH, Legend.AEL, Legend.MENU },
        { Legend.ENTER } };
    private final Legend legend;
    private final String[][] text;
    private int mode = RECIPE;

    public HintBar(Context c, AttributeSet a) {
        super(c, a);
        legend = new Legend(c.getResources().getDisplayMetrics().density);
        legend.setTypeface(UiTypeface.load(c));
        UiText ui = new UiText(new AndroidTextCatalog(c));
        text = new String[][] {
            { ui.text("action_browse"), ui.text("action_pick"), ui.text("action_favourite_hold"), ui.text("action_factory"), ui.text("action_hide"), ui.text("action_exit") },
            { ui.text("action_edit"), ui.text("action_browse"), ui.text("action_factory"), ui.text("action_hide"), ui.text("action_exit") },
            { ui.text("action_done") } };
    }

    public void setMode(int m) { if (mode != m) { mode = m; invalidate(); } }

    @Override
    protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) legend.height()); }

    @Override
    protected void onDraw(Canvas c) {
        legend.draw(c, 0, getHeight() / 2f, getWidth(), ICONS[mode], text[mode]);
    }
}
