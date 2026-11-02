package com.voxivoid.recipelab;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read Recipe QR Code: finds a QR code in a live view frame or a photo (the vendored ZXing reader, Apache-2.0) and says
 * what it holds — a recipe this camera can add, a recipe link it cannot read, or something else. No Android here
 * (tools/test.sh): MainActivity hands in the frame's bytes or the photo's pixels.
 */
final class QrScan {
    private QrScan() {}

    /** what a code held: a recipe ({@link #recipe}), a recipe link this build cannot read, or not a recipe at all */
    static final int RECIPE = 0, UNREADABLE = 1, NOT_RECIPE = 2;

    private static final Map<DecodeHintType, Object> HINTS = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
    static {
        HINTS.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        HINTS.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }

    /** how wide a photo is read, in pixels: first quickly, then, when that found no code, closer */
    static final int QUICK = 750, FULL = 1500;

    /**
     * The JPEG decoder's scale for a picture {@code longest} pixels on its long side to come out at least {@code across}:
     * the largest power of two that keeps it there; 1 for a picture already that small.
     */
    static int sample(int longest, int across) {
        int s = 1;
        while (across > 0 && longest / (s * 2) >= across) s *= 2;
        return s;
    }

    /** the text of a QR code in a frame's luminance plane (NV21 and the other YUV formats start with it); null when none */
    static String fromLuminance(byte[] y, int width, int height) {
        if (y == null || width <= 0 || height <= 0 || y.length < width * height) return null;
        return read(new PlanarYUVLuminanceSource(y, width, height, 0, 0, width, height, false));
    }

    /** the text of a QR code in ARGB pixels (a decoded photo); null when none */
    static String fromPixels(int[] argb, int width, int height) {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) return null;
        return read(new RGBLuminanceSource(width, height, argb));
    }

    private static String read(LuminanceSource source) {
        QRCodeReader reader = new QRCodeReader();
        try {
            return reader.decode(new BinaryBitmap(new HybridBinarizer(source)), HINTS).getText();
        } catch (ReaderException e) {
            // a code drawn light on dark (a phone in dark mode, a negative) reads inverted
            try { reader.reset(); return reader.decode(new BinaryBitmap(new HybridBinarizer(source.invert())), HINTS).getText(); }
            catch (ReaderException again) { return null; }
        } catch (RuntimeException e) {
            return null;                                         // a damaged frame must never take the scan down
        }
    }

    /** what {@code text} is, of {@link #RECIPE}, {@link #UNREADABLE}, {@link #NOT_RECIPE} */
    static int kind(String text) {
        if (!RecipeShare.isLink(text)) return NOT_RECIPE;
        return RecipeShare.decode(text) != null ? RECIPE : UNREADABLE;
    }

    /**
     * The pictures a shutter press added: in {@code now} (the card's pictures after) and not in {@code before}, by path.
     * Reading a photo ends with deleting these, and only these, so the card is left as it was.
     */
    static List<String> added(Set<String> before, Set<String> now) {
        List<String> out = new ArrayList<String>();
        for (String p : now) if (before == null || !before.contains(p)) out.add(p);
        Collections.sort(out);
        return out;
    }

    /** the one of {@code added} to read: a JPG when the shot wrote one (cheaper to decode), else the first picture; null for none */
    static String toRead(List<String> added) {
        String first = null;
        for (String p : added) {
            if (p.toUpperCase(java.util.Locale.US).endsWith(".JPG")) return p;
            if (first == null) first = p;
        }
        return first;
    }
}
