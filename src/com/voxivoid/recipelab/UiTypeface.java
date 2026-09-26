package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;

/** Selects the bundled regional CJK font on Chinese systems and applies it to normal Android text views. */
public final class UiTypeface {
    private static Typeface simplified, traditional;
    private static boolean triedSimplified, triedTraditional;

    private UiTypeface() {}

    public static Typeface load(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        if (locale == null || !"zh".equalsIgnoreCase(locale.getLanguage())) return Typeface.DEFAULT;
        String country = locale.getCountry();
        boolean tc = "TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country);
        return loadAsset(context, tc);
    }

    private static synchronized Typeface loadAsset(Context context, boolean tc) {
        if (tc) {
            if (!triedTraditional) {
                triedTraditional = true;
                traditional = create(context, "fonts/RecipeLabCJKtc-Regular.ttf");
            }
            return traditional;
        }
        if (!triedSimplified) {
            triedSimplified = true;
            simplified = create(context, "fonts/RecipeLabCJKsc-Regular.ttf");
        }
        return simplified;
    }

    private static Typeface create(Context context, String asset) {
        try { return Typeface.createFromAsset(context.getAssets(), asset); }
        catch (Throwable ignored) { return Typeface.DEFAULT; }
    }

    /** Applies the chosen family recursively while preserving each TextView's normal/bold/italic style. */
    public static void apply(View view, Typeface typeface) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            Typeface current = text.getTypeface();
            text.setTypeface(typeface, current == null ? Typeface.NORMAL : current.getStyle());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i), typeface);
        }
    }
}
