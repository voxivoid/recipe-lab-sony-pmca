package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import io.nayuki.qrcodegen.QrCode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Read Recipe QR Code off the camera: a code drawn by the Share screen's generator, put into a frame the way the live
 * view hands one in (a luminance plane, then colour) or a photo's pixels, is read back to the same link.
 */
class QrScanTest {
    private static final String LINK = RecipeShare.PREFIX + "AQAAAgUDAAoIDgYAAENsYXNzaWMgQ2hyb21l";   // Classic Chrome

    /** a {@code w}×{@code h} grey frame with {@code text}'s code at ({@code x0}, {@code y0}), {@code scale} pixels a module */
    private static int[] grey(String text, int w, int h, int x0, int y0, int scale, boolean inverted) {
        QrCode q = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int[] g = new int[w * h];
        java.util.Arrays.fill(g, inverted ? 30 : 200);          // a lit room behind a phone, or a dark-mode screen
        int side = (q.size + 8) * scale;
        for (int y = 0; y < side; y++)
            for (int x = 0; x < side; x++) {
                int mx = x / scale - 4, my = y / scale - 4;
                boolean dark = mx >= 0 && my >= 0 && mx < q.size && my < q.size && q.getModule(mx, my);
                g[(y0 + y) * w + x0 + x] = dark != inverted ? 20 : 235;
            }
        return g;
    }

    /** a frame as NV21: the luminance plane, then a chroma plane half its size */
    private static byte[] nv21(int[] grey, int w, int h) {
        byte[] b = new byte[w * h * 3 / 2];
        for (int i = 0; i < w * h; i++) b[i] = (byte) grey[i];
        for (int i = w * h; i < b.length; i++) b[i] = (byte) 128;
        return b;
    }

    private static int[] argb(int[] grey) {
        int[] p = new int[grey.length];
        for (int i = 0; i < grey.length; i++) p[i] = 0xFF000000 | grey[i] << 16 | grey[i] << 8 | grey[i];
        return p;
    }

    @Test void aPhotoIsScaledToAtLeastWhatIsAsked() {
        assertEquals(4, QrScan.sample(6000, QrScan.FULL), "24 MP: 1500 across");
        assertEquals(8, QrScan.sample(6000, QrScan.QUICK), "24 MP: 750 across");
        assertEquals(2, QrScan.sample(1616, QrScan.QUICK), "an ARW's preview: 808 across");
        assertEquals(1, QrScan.sample(1616, QrScan.FULL), "already small: as it is");
        assertEquals(2, QrScan.sample(3000, QrScan.FULL), "exactly twice: halved, not below");
        assertEquals(1, QrScan.sample(2999, QrScan.FULL), "just under twice: as it is");
        assertEquals(1, QrScan.sample(6000, 0), "nothing asked: as it is");
        assertTrue(QrScan.QUICK < QrScan.FULL, "the quick pass is the smaller");
    }

    @Test void aLiveViewFrameIsRead() {
        int w = 640, h = 480;
        assertEquals(LINK, QrScan.fromLuminance(nv21(grey(LINK, w, h, 200, 100, 6, false), w, h), w, h));
    }

    @Test void aSmallCodeOffCentreIsRead() {
        int w = 640, h = 480;
        assertEquals(LINK, QrScan.fromLuminance(nv21(grey(LINK, w, h, 30, 260, 4, false), w, h), w, h), "4 pixels a module, in a corner");
    }

    @Test void aCodeLightOnDarkIsRead() {
        int w = 640, h = 480;
        assertEquals(LINK, QrScan.fromLuminance(nv21(grey(LINK, w, h, 200, 100, 6, true), w, h), w, h));
    }

    @Test void aPhotoIsRead() {
        int w = 1500, h = 1000;
        assertEquals(LINK, QrScan.fromPixels(argb(grey(LINK, w, h, 400, 200, 12, false)), w, h));
    }

    @Test void noCodeIsNull() {
        int w = 320, h = 240;
        byte[] noise = new byte[w * h * 3 / 2];
        new Random(7).nextBytes(noise);
        assertNull(QrScan.fromLuminance(noise, w, h));
        assertNull(QrScan.fromLuminance(new byte[w * h], w, h), "black");
        assertNull(QrScan.fromPixels(new int[w * h], w, h));
    }

    @Test void framesThatAreNotOneAreRefused() {
        assertNull(QrScan.fromLuminance(null, 10, 10));
        assertNull(QrScan.fromLuminance(new byte[99], 10, 10), "shorter than its luminance plane");
        assertNull(QrScan.fromLuminance(new byte[100], 0, 10));
        assertNull(QrScan.fromLuminance(new byte[100], 10, 0));
        assertNull(QrScan.fromPixels(null, 10, 10));
        assertNull(QrScan.fromPixels(new int[99], 10, 10));
        assertNull(QrScan.fromPixels(new int[100], 0, 10));
        assertNull(QrScan.fromPixels(new int[100], 10, 0));
    }

    @Test void whatACodeHolds() {
        assertEquals(QrScan.RECIPE, QrScan.kind(LINK));
        assertEquals(QrScan.UNREADABLE, QrScan.kind(RecipeShare.PREFIX + "AQEAAwMDAAcHDwYAAEE"), "a family this build does not know");
        assertEquals(QrScan.UNREADABLE, QrScan.kind(RecipeShare.PREFIX + "Ag"), "a newer version");
        assertEquals(QrScan.NOT_RECIPE, QrScan.kind("WIFI:S:DIRECT-xx;T:WPA;P:secret;;"));
        assertEquals(QrScan.NOT_RECIPE, QrScan.kind("https://recipelab.io/studio"));
        assertEquals(QrScan.NOT_RECIPE, QrScan.kind(null));
    }

    private static Set<String> set(String... p) { return new HashSet<String>(Arrays.asList(p)); }

    @Test void theShutterAddsWhatWasNotThereBefore() {
        Set<String> before = set("D/903/DSC1.JPG", "D/903/DSC1.ARW");
        Set<String> now = set("D/903/DSC1.JPG", "D/903/DSC1.ARW", "D/903/DSC2.JPG", "D/903/DSC2.ARW");
        assertEquals(Arrays.asList("D/903/DSC2.ARW", "D/903/DSC2.JPG"), QrScan.added(before, now), "both files of the shot, sorted");
        assertEquals(Arrays.asList(), QrScan.added(now, now), "nothing new: nothing to delete");
        assertEquals(Arrays.asList(), QrScan.added(before, set()), "a card that lost pictures added none");
        assertEquals(Arrays.asList("D/903/DSC1.ARW", "D/903/DSC1.JPG"), QrScan.added(null, before), "no before: all of them");
    }

    @Test void theJpgIsReadWhenTheShotWroteOne() {
        assertEquals("D/DSC2.JPG", QrScan.toRead(Arrays.asList("D/DSC2.ARW", "D/DSC2.JPG")));
        assertEquals("D/DSC2.jpg", QrScan.toRead(Arrays.asList("D/DSC2.ARW", "D/DSC2.jpg")), "any case");
        assertEquals("D/DSC2.ARW", QrScan.toRead(Arrays.asList("D/DSC2.ARW")), "a RAW-only shot is read through the JPEG it carries");
        assertNull(QrScan.toRead(Arrays.<String>asList()));
    }

    @Test void aPhotoReadARowAtATimeReadsAsItDidWhole() {
        int w = 1500, h = 1000;
        int[] px = argb(grey(LINK, w, h, 400, 200, 12, false));
        byte[] y = new byte[w * h];
        int[] row = new int[w];
        for (int r = 0; r < h; r++) {
            System.arraycopy(px, r * w, row, 0, w);
            QrScan.luminance(row, w, y, r * w);
        }
        assertEquals(LINK, QrScan.fromLuminance(y, w, h));
    }

    @Test void luminanceIsZxingsWeighting() {
        byte[] out = new byte[5];
        int[] px = { 0xFF000000, 0xFFFFFFFF, 0xFF102030, 0x00FF0000, 0xFF0000FF };
        QrScan.luminance(px, 4, out, 1);
        assertEquals(0, out[0], "before where it was asked to start: untouched");
        assertEquals(0, out[1] & 0xFF, "black");
        assertEquals(255, out[2] & 0xFF, "white");
        assertEquals((0x10 + 2 * 0x20 + 0x30) / 4, out[3] & 0xFF, "green counts twice");
        assertEquals(0xFF / 4, out[4] & 0xFF, "alpha ignored; only n pixels");
    }
}
