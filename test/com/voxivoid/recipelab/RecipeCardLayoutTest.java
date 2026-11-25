package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The recipe card Share saves: Recipe Lab Studio's shapes and layout, and what it says. */
class RecipeCardLayoutTest {
    @Test void theShapesAreStudiosFourFiveFirst() {
        assertEquals(Arrays.asList("4:5", "1:1", "9:16"), Arrays.asList(RecipeCardLayout.LABEL));
        assertEquals(RecipeCardLayout.SHAPES, RecipeCardLayout.LABEL.length);
        int[][] sizes = { { 1080, 1352 }, { 1080, 1080 }, { 1080, 1920 } };
        for (int s = 0; s < RecipeCardLayout.SHAPES; s++) {
            RecipeCardLayout l = RecipeCardLayout.of(s);
            assertEquals(s, l.shape);
            assertEquals(sizes[s][0], l.width);
            assertEquals(sizes[s][1], l.height);
            String[] wh = RecipeCardLayout.LABEL[s].split(":");
            float ratio = (float) l.width / l.height, want = Float.parseFloat(wh[0]) / Float.parseFloat(wh[1]);
            assertEquals(want, ratio, want * 0.002f, "its own ratio, within what the camera's sizes allow");
            assertEquals(0, l.width % 2, "UYVY: an even width");
            assertEquals(0, l.height % 8, "the camera makes images in heights of 8 rows: it refused 1350");
        }
        assertEquals(RecipeCardLayout.PORTRAIT, 0, "the default");
    }

    @Test void theShapeStepsRoundBothWays() {
        assertEquals(RecipeCardLayout.SQUARE, RecipeCardLayout.next(RecipeCardLayout.PORTRAIT, +1));
        assertEquals(RecipeCardLayout.STORY, RecipeCardLayout.next(RecipeCardLayout.SQUARE, +1));
        assertEquals(RecipeCardLayout.PORTRAIT, RecipeCardLayout.next(RecipeCardLayout.STORY, +1));
        assertEquals(RecipeCardLayout.STORY, RecipeCardLayout.next(RecipeCardLayout.PORTRAIT, -1));
        assertEquals(RecipeCardLayout.SQUARE, RecipeCardLayout.next(RecipeCardLayout.PORTRAIT, -5));
    }

    private static boolean overlap(float[] a, float[] b) {
        return Math.min(a[2], b[2]) - Math.max(a[0], b[0]) > 0.5f && Math.min(a[3], b[3]) - Math.max(a[1], b[1]) > 0.5f;
    }

    @Test void everythingIsInsideNothingOnAnythingElseAndTheCodeIsBig() {
        for (int k = 0; k < RecipeCardLayout.SHAPES * 2; k++) {
            int s = k % RecipeCardLayout.SHAPES;
            boolean profile = k >= RecipeCardLayout.SHAPES;
            RecipeCardLayout l = RecipeCardLayout.of(s, profile);
            List<float[]> blocks = new ArrayList<float[]>();
            List<String> names = new ArrayList<String>();
            blocks.add(RecipeCardLayout.rect(RecipeCardLayout.MARGIN, l.headerTop, 400, 80)); names.add("header");
            blocks.add(l.nameRect()); names.add("name");
            for (int i = 0; i < l.cells.length; i++) { blocks.add(l.cells[i]); names.add("cell " + i); }
            blocks.add(l.tile()); names.add("code");
            float[] t = l.tile();
            blocks.add(new float[] { t[0], l.scanTop, t[2], l.scanTop + 36 }); names.add("scan");
            assertEquals(profile ? 16 : 8, l.cells.length, "one per setting");
            String card = RecipeCardLayout.LABEL[s] + (profile ? " profile" : "");
            for (int i = 0; i < blocks.size(); i++) {
                float[] b = blocks.get(i);
                String why = card + " " + names.get(i);
                assertTrue(b[0] >= RecipeCardLayout.MARGIN - 12 && b[2] <= l.width - RecipeCardLayout.MARGIN + 12, why + " keeps the margin");
                assertTrue(b[1] >= 0 && b[3] <= l.height, why + " inside");
                if (s == RecipeCardLayout.STORY)
                    assertTrue(b[1] >= RecipeCardLayout.STORY_SAFE_TOP && b[3] <= RecipeCardLayout.STORY_SAFE_BOTTOM, why + " clear of the story bars");
                for (int j = i + 1; j < blocks.size(); j++)
                    assertFalse(overlap(b, blocks.get(j)), card + " " + names.get(i) + " and " + names.get(j));
            }
            for (float[] c : l.cells) {
                assertTrue(l.valueTop + l.valueSize * 1.3f <= c[3] - c[1] + 0.01f, card + ": a value's line stays in its row");
                assertTrue(c[2] - c[0] - 24 >= 210, card + ": room for S-Gamut3.Cine");
            }
            assertTrue(l.headerTop < l.nameTop);
            assertEquals(l.qr[2] - l.qr[0], l.qr[3] - l.qr[1], 0.01, "square code");
            assertTrue(l.qr[2] - l.qr[0] >= 400, "about 9 px a module");
            float[] band = l.scanBand();
            assertTrue(band[0] <= t[0] && band[2] >= t[2], "the scan line is centred under the code");
        }
    }

    @Test void theSameNumbersAsStudio() {
        RecipeCardLayout p = RecipeCardLayout.of(RecipeCardLayout.PORTRAIT);
        assertArrayEquals(new float[] { 320, 800, 760, 1240 }, p.qr, 0.01f);
        assertEquals(84, p.headerTop, 0.01); assertEquals(224, p.nameTop, 0.01); assertEquals(1280, p.scanTop, 0.01);
        assertArrayEquals(new float[] { 72, 372, 540, 472 }, p.cells[0], 0.01f);
        assertArrayEquals(new float[] { 540, 372, 1008, 472 }, p.cells[1], 0.01f);
        RecipeCardLayout q = RecipeCardLayout.of(RecipeCardLayout.SQUARE);
        assertArrayEquals(new float[] { 588, 340, 1008, 760 }, q.qr, 0.01f);
        assertArrayEquals(new float[] { 72, 312 + 7 * 84, 548, 312 + 8 * 84 }, q.cells[7], 0.01f);
        RecipeCardLayout s = RecipeCardLayout.of(RecipeCardLayout.STORY);
        assertArrayEquals(new float[] { 280, 1050, 800, 1570 }, s.qr, 0.01f);
        assertEquals(1614, s.scanTop, 0.01);
        assertEquals(36, p.valueSize, 0.01); assertEquals(36, p.valueTop, 0.01);
        assertEquals(32, q.valueSize, 0.01); assertEquals(32, q.valueTop, 0.01);
    }

    @Test void theSameProfileNumbersAsStudio() {
        RecipeCardLayout p = RecipeCardLayout.of(RecipeCardLayout.PORTRAIT, true);
        assertArrayEquals(new float[] { 320, 800, 760, 1240 }, p.qr, 0.01f, "the code as without a profile");
        assertEquals(1280, p.scanTop, 0.01);
        assertArrayEquals(new float[] { 72, 316, 384, 392 }, p.cells[0], 0.01f);
        assertArrayEquals(new float[] { 696, 316, 1008, 392 }, p.cells[2], 0.01f);
        assertArrayEquals(new float[] { 384, 468, 696, 544 }, p.cells[7], 0.01f, "exposure, the third row's middle");
        assertArrayEquals(new float[] { 72, 544, 384, 620 }, p.cells[8], 0.01f, "the profile from a row of its own");
        assertArrayEquals(new float[] { 384, 696, 696, 772 }, p.cells[15], 0.01f);
        assertEquals(32, p.valueSize, 0.01); assertEquals(32, p.valueTop, 0.01);
        RecipeCardLayout q = RecipeCardLayout.of(RecipeCardLayout.SQUARE, true);
        assertArrayEquals(new float[] { 608, 340, 1008, 740 }, q.qr, 0.01f);
        assertEquals(780, q.scanTop, 0.01);
        assertArrayEquals(new float[] { 72, 312, 328, 396 }, q.cells[0], 0.01f);
        assertArrayEquals(new float[] { 328, 312, 584, 396 }, q.cells[8], 0.01f, "the profile the right column");
        assertArrayEquals(new float[] { 328, 900, 584, 984 }, q.cells[15], 0.01f);
        assertEquals(30, q.valueSize, 0.01); assertEquals(32, q.valueTop, 0.01);
        RecipeCardLayout s = RecipeCardLayout.of(RecipeCardLayout.STORY, true);
        assertArrayEquals(new float[] { 280, 1050, 800, 1570 }, s.qr, 0.01f);
        assertArrayEquals(new float[] { 72, 556, 384, 632 }, s.cells[0], 0.01f);
        assertArrayEquals(new float[] { 384, 936, 696, 1012 }, s.cells[15], 0.01f);
        assertEquals(32, s.valueSize, 0.01);
    }

    @Test void aProfileRecipesCardListsItsProfileAfterTheSettings() {
        Recipes.Recipe plain = new Recipes.Recipe(Recipes.CUSTOM, "A", Recipes.STD, 0, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(8, RecipeCardLayout.cells(plain).length, "no profile: the 8 settings");
        Recipes.Recipe r = new Recipes.Recipe(Recipes.CUSTOM, "Log", Recipes.STD, 0, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, Recipes.DRO_OFF, 0,
                new IspProfiles.Look(IspProfiles.SLOG3, IspProfiles.SGAMUT3_CINE, -3, 5, IspProfiles.WIDE, 95, -2, 12, -1, 4));
        String[][] c = RecipeCardLayout.cells(r);
        String[][] want = {
            { "Gamma", "S-Log3" }, { "Colour mode", "S-Gamut3.Cine" }, { "Black level", "-3" }, { "Black gamma", "+5 · Wide" },
            { "Knee", "95% · -2" }, { "Colour saturation", "+12" }, { "Colour phase", "-1" }, { "S-curve", "+4" },
        };
        assertEquals(16, c.length);
        assertEquals("Creative Style", c[0][0]);
        assertEquals("Exposure", c[7][0]);
        for (int i = 0; i < want.length; i++) assertArrayEquals(want[i], c[8 + i], "profile cell " + i);
        Recipes.Recipe neutral = new Recipes.Recipe(Recipes.CUSTOM, "N", Recipes.STD, 0, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, Recipes.DRO_OFF, 0,
                new IspProfiles.Look(IspProfiles.HLG, IspProfiles.CAMERA, 0, 0, IspProfiles.NARROW, IspProfiles.KNEE_OFF, 3, 0, 0, 0));
        String[][] n = RecipeCardLayout.cells(neutral);
        assertEquals("ITU709 Matrix", n[9][1]);
        assertEquals("0", n[11][1], "no black gamma: no range");
        assertEquals("off", n[12][1], "no knee: no slope");
        Recipes.Recipe flat = new Recipes.Recipe(Recipes.CUSTOM, "K", Recipes.STD, 0, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, Recipes.DRO_OFF, 0,
                new IspProfiles.Look(IspProfiles.ITU709, IspProfiles.BT2020, 0, 0, IspProfiles.MIDDLE, 105, 0, 0, 0, 0));
        assertEquals("105%", RecipeCardLayout.cells(flat)[12][1], "a knee with no slope");
        for (Recipes.Recipe b : Recipes.ALL)
            assertEquals(b.hasProfile() ? 16 : 8, RecipeCardLayout.cells(b).length, b.name);
    }

    @Test void theCardListsEverySettingAsStudiosDoes() {
        Recipes.Recipe r = new Recipes.Recipe(Recipes.CUSTOM, "Warm", Recipes.AUTUMN, 2, -1, 0, Params.WB_KELVIN, 5600, 3, -2, 1, -4, 0, 2);
        String[][] c = RecipeCardLayout.cells(r);
        String[][] want = {
            { "Creative Style", "Autumn" }, { "Picture Effect", "Toy · warm" }, { "Saturation", "+2" }, { "Contrast", "-1" },
            { "Sharpness", "0" }, { "DRO", "off" }, { "White balance", "5600K A3 M2" }, { "Exposure", "-1.3 EV" },
        };
        for (int i = 0; i < want.length; i++) assertArrayEquals(want[i], c[i], "cell " + i);
        Recipes.Recipe auto = new Recipes.Recipe(Recipes.CUSTOM, "A", Recipes.STD, 0, 0, 0, Params.WB_AUTO, 0, -7, 7, 0, 0, Recipes.DRO_AUTO, 0);
        String[][] a = RecipeCardLayout.cells(auto);
        assertEquals("off", a[1][1], "no effect");
        assertEquals("auto B7 G7", a[6][1]);
        assertEquals("0 EV", a[7][1]);
        assertEquals("auto", a[5][1]);
        Recipes.Recipe keep = new Recipes.Recipe(Recipes.CUSTOM, "K", Recipes.STD, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 0);
        assertEquals("As set", RecipeCardLayout.cells(keep)[6][1]);
        assertEquals("+1.0 EV", RecipeCardLayout.cells(keep)[7][1]);
        assertTrue(RecipeCardLayout.cells(keep)[5][1].contains("2"), "a DRO level");
        assertEquals("Scan to add · recipelab.io", RecipeCardLayout.scanLine());
    }

    @Test void studiosFontsDrawLatinAndVietnameseLines() {
        assertTrue(RecipeCardLayout.latin("Sony PT (portrait) 2"));
        assertTrue(RecipeCardLayout.latin("Toy camera · Normal"), "the middle dot");
        assertTrue(RecipeCardLayout.latin("Auto A5 G−2"), "the minus sign");
        assertTrue(RecipeCardLayout.latin("Cân bằng trắng"), "Vietnamese");
        assertTrue(RecipeCardLayout.latin("ƠƯ ơư Ỹ"), "Vietnamese's own letters, both ends of its block");
        assertTrue(RecipeCardLayout.latin("Quét để thêm · recipelab.io"));
        assertTrue(RecipeCardLayout.latin(""));
        assertTrue(RecipeCardLayout.latin("Naïve – “x” … → •"));
        assertFalse(RecipeCardLayout.latin("クリエイティブスタイル"), "Japanese: its own font");
        assertFalse(RecipeCardLayout.latin("白平衡"));
        assertFalse(RecipeCardLayout.latin("화이트 밸런스"));
        assertFalse(RecipeCardLayout.latin("Auto\u00a0\u1EFA"), "past Vietnamese");
        assertFalse(RecipeCardLayout.latin("\u0180"), "past Latin Extended-A");
        assertFalse(RecipeCardLayout.latin("a\tb"), "a control character");
        assertFalse(RecipeCardLayout.latin("\u007f"));
        assertFalse(RecipeCardLayout.latin("x\u2190"), "an arrow the cut does not hold");
    }

    @Test void aLineSitsInItsBoxAsFlutterPutsIt() {
        // Inter at 64 px (ascent 1984, descent 494 per 2048) in Lab.title's 1.2 line: 76.8 px, split 62 : 15.44
        assertEquals(100 + 76.8f * 62f / 77.44f, RecipeCardLayout.baseline(100, 76.8f, 62f, 15.44f), 1e-3f);
        assertEquals(10 + 30f, RecipeCardLayout.baseline(10, 40, 30, 10), 1e-4f, "3 : 1 of a 40 px line");
        assertEquals(10 + 15f, RecipeCardLayout.baseline(10, 20, 30, 10), 1e-4f, "a line smaller than the font");
        assertEquals(5f, RecipeCardLayout.baseline(5, 20, 0, 0), "no metrics: the top");
    }
}
