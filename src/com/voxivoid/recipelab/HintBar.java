package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

/** Key legend under the main panel. */
public class HintBar extends View {
    public static final int RECIPE = 0, CHIPS = 1, EDIT = 2, BROWSER = 3;
    private static final int[][] ICONS = {
        { Legend.FN, Legend.ENTER, Legend.ENTER, Legend.TRASH, Legend.AEL, Legend.MENU },
        { Legend.ENTER, Legend.FN, Legend.TRASH, Legend.AEL, Legend.MENU },
        { Legend.ENTER } };
    private static final String[][] TEXT = {
        { "browse", "pick", "fav (hold)", "factory", "hide", "exit" },
        { "edit", "browse", "factory", "hide", "exit" },
        { "done" } };

    private static final int[][] A5100_ICONS = {
        { Legend.ENTER, Legend.ENTER, Legend.TRASH, Legend.MOVIE, Legend.MENU },
        { Legend.ENTER, Legend.TRASH, Legend.MOVIE, Legend.MENU },
        { Legend.ENTER },
        { Legend.ENTER, Legend.UPDOWN, Legend.MOVIE, Legend.MENU } };
    private static final String[][] A5100_TEXT = {
        { "保存", "长按收藏", "标准", "切换显示", "退出" },
        { "编辑", "标准", "切换显示", "退出" },
        { "完成" },
        { "打开列表", "选择入口", "切换显示", "退出" } };
    private boolean a5100;
    public void setA5100(boolean value) { a5100 = value; invalidate(); }
    private final Legend legend;
    private int mode = RECIPE;

    public HintBar(Context c, AttributeSet a) {
        super(c, a);
        legend = new Legend(c.getResources().getDisplayMetrics().density);
    }

    public void setMode(int m) { if (mode != m) { mode = m; invalidate(); } }

    @Override
    protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) legend.height()); }

    @Override
    protected void onDraw(Canvas c) {
        legend.draw(c, 0, getHeight() / 2f, getWidth(), a5100 ? A5100_ICONS[mode] : ICONS[mode], a5100 ? A5100_TEXT[mode] : TEXT[mode]);
    }
}
