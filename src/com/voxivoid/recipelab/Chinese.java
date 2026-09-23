package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.TextView;

/** Display-only font and translations. Font failure must never prevent the camera app from opening. */
final class Chinese {
    private Chinese() {}
    private static Typeface font;
    private static boolean attempted;

    /** Read the small, uncompressed asset directly; no writable cache directory is required. */
    static void init(Context context) {
        if (attempted) return;
        attempted = true;
        try {
            font = Typeface.createFromAsset(context.getAssets(), "fonts/recipe-zh.ttf");
        } catch (RuntimeException e) {
            Log.w("RecipeLab", "Chinese font unavailable; using the original system font", e);
        } catch (OutOfMemoryError e) {
            Log.w("RecipeLab", "Chinese font allocation failed; using the original system font");
        }
    }

    static void setText(TextView view, String text) {
        if (font != null) {
            int style = view.getTypeface() == null ? Typeface.NORMAL : view.getTypeface().getStyle();
            view.setTypeface(font, style);
        }
        view.setText(Zh.text(text));
    }
    private static void bind(Paint paint) { if (font != null) paint.setTypeface(font); }
    static float measure(Paint paint, String text) {
        bind(paint);
        return paint.measureText(Zh.text(text));
    }
    static void draw(Canvas canvas, String text, float x, float y, Paint paint) {
        bind(paint);
        canvas.drawText(Zh.text(text), x, y, paint);
    }
}
