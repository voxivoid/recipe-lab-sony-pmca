package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * The typeface each display language ({@link Lang}) is drawn in. English keeps the camera's own font. Chinese needs
 * the fonts in assets/fonts — the firmware font has no CJK glyphs — which tools/subset-font.py cuts down to what the
 * tables use, one per script so each gets its own glyph forms. A font that will not load falls back to the camera's:
 * the text is then boxes, but the app still opens.
 */
final class UiFont {
    private UiFont() {}

    private static final String[] ASSET = { null, "fonts/RecipeLabCJKsc-Regular.ttf", "fonts/RecipeLabCJKtc-Regular.ttf", null };
    private static final Typeface[] LOADED = new Typeface[Lang.COUNT];

    /** the typeface of a language, loaded the first time it is asked for */
    static Typeface of(Context c, int lang) {
        if (ASSET[lang] == null) return Typeface.DEFAULT;
        if (LOADED[lang] == null) {
            try { LOADED[lang] = Typeface.createFromAsset(c.getAssets(), ASSET[lang]); }
            catch (Throwable t) { LOADED[lang] = Typeface.DEFAULT; }
        }
        return LOADED[lang];
    }

    /**
     * Every TextView under {@code v} in {@code tf}, each keeping the style its layout gave it. That style is kept in the
     * view's tag the first time, because a typeface without a bold face reports plain once it is set.
     */
    static void apply(View v, Typeface tf) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (!(t.getTag() instanceof Integer)) t.setTag(t.getTypeface() == null ? Typeface.NORMAL : t.getTypeface().getStyle());
            t.setTypeface(tf, (Integer) t.getTag());
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i), tf);
        }
    }
}
