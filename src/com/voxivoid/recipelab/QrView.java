package com.voxivoid.recipelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipe QR codes on screen. {@link #share} fills the screen with a recipe's code, white with its quiet zone, big enough
 * for a phone or another camera to read from the camera's screen, with the recipe's name and a line on how to read it.
 * {@link #scan} is Read Recipe QR Code: the live view shows through a frame to put the code in, under a title, with
 * what to do (or what is going on) below it. Everything is drawn: the camera font has no symbols.
 */
public class QrView extends View {
    private static final int ACCENT = 0xFFF2B85C;
    private static final int SHARE = 1, SCAN = 2, COVER = 3, CARD = 4;
    private static final long SPIN_MS = 900, FRAME_MS = 40;

    private final float d;
    private final Paint bg = new Paint(), white = new Paint(), ink = new Paint(), frame = new Paint(Paint.ANTI_ALIAS_FLAG),
            title = new Paint(Paint.ANTI_ALIAS_FLAG), body = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Legend legend;
    private int mode = 0;
    private boolean spinning = false;                          // COVER: the ring turns while a photo is read
    // CARD: Share's screen — the card, and the rows beside it
    private CardPainter painter;
    private RecipeCardLayout layout;
    private Recipes.Recipe recipe;
    private String[] rows = new String[0];
    private String shapeValue;
    private int selected;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG), option = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean[] modules;
    private int size;
    private String titleText = "", bodyText = "";
    private Keys.Hints hints = Keys.hints(Keys.H_PAGE, Keys.Caps.UNKNOWN);

    public QrView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        bg.setColor(0xFF000000);
        white.setColor(0xFFFFFFFF);
        ink.setColor(0xFF000000);
        frame.setColor(ACCENT); frame.setStyle(Paint.Style.STROKE); frame.setStrokeWidth(3 * d);
        title.setColor(0xFFFFFFFF); title.setTextSize(16 * d); title.setFakeBoldText(true);
        body.setColor(0xDDFFFFFF); body.setTextSize(12 * d);
        ring.setStyle(Paint.Style.STROKE); ring.setStrokeCap(Paint.Cap.ROUND); ring.setStrokeWidth(5 * d);
        pill.setColor(ACCENT);
        option.setTextSize(14 * d); option.setFakeBoldText(true);
        legend = new Legend(d);
    }

    public void setTypeface(Typeface tf) { title.setTypeface(tf); body.setTypeface(tf); option.setTypeface(tf); legend.setTypeface(tf); invalidate(); }

    /**
     * Share: the recipe's card as it will be saved, in {@code layout}'s shape, and beside it {@code rows} — the first
     * the shape, its value {@code shapeValue} between arrows — with {@code selected} highlighted.
     */
    public void card(CardPainter painter, RecipeCardLayout layout, Recipes.Recipe recipe, boolean[] modules, int size,
                     String[] rows, String shapeValue, int selected, Keys.Hints hints) {
        mode = CARD; this.painter = painter; this.layout = layout; this.recipe = recipe; this.modules = modules; this.size = size;
        this.rows = rows; this.shapeValue = shapeValue; this.selected = selected; this.hints = hints; titleText = recipe.name;
        invalidate();
    }

    /** a recipe's code: {@code modules} row by row, {@code size} on a side, quiet zone not included */
    public void share(boolean[] modules, int size, String name, String hint, Keys.Hints hints) {
        mode = SHARE; this.modules = modules; this.size = size; titleText = name; bodyText = hint; this.hints = hints;
        invalidate();
    }

    /** the scanner: the live view through a frame, what to do below it */
    public void scan(String titleText, String status, Keys.Hints hints) {
        mode = SCAN; this.titleText = titleText; bodyText = status; this.hints = hints;
        invalidate();
    }

    /**
     * The live view covered in black, under {@code status}, with a ring turning while {@code spinning}: a photo is being
     * read, and the live view behind it would only show a camera that is doing nothing.
     */
    public void cover(String titleText, String status, boolean spinning, Keys.Hints hints) {
        mode = COVER; this.titleText = titleText; bodyText = status; this.spinning = spinning; this.hints = hints;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 12 * d;
        float legTop = h - pad - legend.height(legend.lines(w - 2 * pad, hints));
        if (mode == SHARE) drawShare(c, w, h, pad, legTop);
        else if (mode == SCAN) drawScan(c, w, h, pad, legTop);
        else if (mode == COVER) drawCover(c, w, h, pad, legTop);
        else if (mode == CARD) drawCard(c, w, h, pad, legTop);
        legend.drawWrapped(c, pad, legTop, w - 2 * pad, hints);
    }

    private void drawShare(Canvas c, float w, float h, float pad, float legTop) {
        c.drawRect(0, 0, w, h, bg);
        // the code on the left as tall as the screen allows, the words on the right
        float side = legTop - 2 * pad, x0 = pad, y0 = pad;
        c.drawRect(x0, y0, x0 + side, y0 + side, white);
        if (modules != null && size > 0) {
            float m = side / (size + 8);                         // four light modules of quiet zone each side
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                    if (modules[y * size + x]) {
                        float l = x0 + (x + 4) * m, t = y0 + (y + 4) * m;
                        c.drawRect(l, t, l + m + 0.5f, t + m + 0.5f, ink);
                    }
        }
        float tx = x0 + side + 2 * pad, tw = w - tx - pad;
        float y = drawLines(c, wrap(titleText, title, tw), title, tx, y0 + 18 * d, 20 * d);
        drawLines(c, wrap(bodyText, body, tw), body, tx, y + 12 * d, 16 * d);
    }

    private void drawCover(Canvas c, float w, float h, float pad, float legTop) {
        c.drawRect(0, 0, w, h, bg);
        c.drawText(titleText, pad, pad + 16 * d, title);
        float cx = w / 2, cy = (pad + legTop) / 2 - 8 * d, radius = 22 * d;
        r.set(cx - radius, cy - radius, cx + radius, cy + radius);
        ring.setColor(0x33FFFFFF);                              // the track
        c.drawArc(r, 0, 360, false, ring);
        if (spinning) {
            ring.setColor(ACCENT);
            float turn = (SystemClock.uptimeMillis() % SPIN_MS) * 360f / SPIN_MS;
            c.drawArc(r, turn - 90, 100, false, ring);          // a quarter of the ring, going round
            postInvalidateDelayed(FRAME_MS);
        }
        float y = cy + radius + 24 * d;
        for (String line : wrap(bodyText, body, w - 2 * pad)) {
            c.drawText(line, (w - body.measureText(line)) / 2, y, body);
            y += 16 * d;
        }
    }

    private void drawCard(Canvas c, float w, float h, float pad, float legTop) {
        c.drawRect(0, 0, w, h, bg);
        // the card on the left, as tall as the screen allows
        float avail = legTop - 2 * pad, scale = Math.min(avail / layout.height, (w * 0.5f) / layout.width);
        float cw = layout.width * scale, ch = layout.height * scale;
        c.save();
        c.translate(pad, pad + (avail - ch) / 2);
        c.scale(scale, scale);
        painter.draw(c, layout, recipe, modules, size);
        c.restore();
        // the rows on the right: the shape with its value between arrows, then what can be done
        float x = pad * 2 + cw, rw = w - x - pad, y = pad + 18 * d;
        for (String line : wrap(titleText, title, rw)) { c.drawText(line, x, y, title); y += 20 * d; }
        y += 10 * d;
        float rh = 30 * d;
        for (int i = 0; i < rows.length; i++) {
            boolean on = i == selected;
            r.set(x, y, x + rw, y + rh);
            if (on) c.drawRoundRect(r, rh / 2, rh / 2, pill);
            option.setColor(on ? 0xFF1A1208 : 0xFFFFFFFF);
            float base = y + rh / 2 - (option.ascent() + option.descent()) / 2;
            c.drawText(rows[i], x + 14 * d, base, option);
            if (i == 0 && shapeValue != null) {                  // ◂ 4:5 ▸, drawn: the camera font has no arrows
                float vw = option.measureText(shapeValue), a = 4 * d, right = x + rw - 14 * d;
                c.drawText(shapeValue, right - a * 2 - 6 * d - vw, base, option);
                arrow(c, right - a * 2 - 12 * d - vw - a, y + rh / 2, a, -1, on);
                arrow(c, right - a, y + rh / 2, a, +1, on);
            }
            y += rh + 6 * d;
        }
    }

    private void arrow(Canvas c, float cx, float cy, float a, int dir, boolean on) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(on ? 0xFF1A1208 : 0x99FFFFFF);
        android.graphics.Path t = new android.graphics.Path();
        t.moveTo(cx + dir * a, cy); t.lineTo(cx - dir * a, cy - a); t.lineTo(cx - dir * a, cy + a); t.close();
        c.drawPath(t, p);
    }

    private void drawScan(Canvas c, float w, float h, float pad, float legTop) {
        // a square in the middle left clear for the live view, the rest dimmed
        float top = pad + 26 * d, bottom = legTop - 34 * d, side = Math.min(bottom - top, w - 2 * pad);
        float l = (w - side) / 2, t = top + (bottom - top - side) / 2;
        Paint dim = new Paint(); dim.setColor(0x99000000);
        c.drawRect(0, 0, w, t, dim); c.drawRect(0, t + side, w, h, dim);
        c.drawRect(0, t, l, t + side, dim); c.drawRect(l + side, t, w, t + side, dim);
        r.set(l, t, l + side, t + side);
        c.drawRect(r, frame);
        c.drawText(titleText, pad, pad + 16 * d, title);
        drawLines(c, wrap(bodyText, body, w - 2 * pad), body, pad, t + side + 20 * d, 15 * d);
    }

    private static float drawLines(Canvas c, List<String> lines, Paint p, float x, float y, float step) {
        for (String s : lines) { c.drawText(s, x, y, p); y += step; }
        return y;
    }

    /** {@code text} broken into lines no wider than {@code width}, at spaces where there are any, else anywhere */
    private static List<String> wrap(String text, Paint p, float width) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        String rest = text.trim();
        while (!rest.isEmpty()) {
            int n = p.breakText(rest, true, width, null);
            if (n >= rest.length()) { out.add(rest); break; }
            int sp = rest.lastIndexOf(' ', n);
            int cut = sp > 0 ? sp : Math.max(1, n);
            out.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        return out;
    }
}
