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
        for (int k = 0; k < RecipeCardLayout.SHAPES * 4; k++) {
            int s = k % RecipeCardLayout.SHAPES;
            boolean profile = (k / RecipeCardLayout.SHAPES) % 2 == 1, effect = k >= RecipeCardLayout.SHAPES * 2;
            RecipeCardLayout l = RecipeCardLayout.of(s, profile, effect);
            List<float[]> blocks = new ArrayList<float[]>();
            List<String> names = new ArrayList<String>();
            blocks.add(RecipeCardLayout.rect(RecipeCardLayout.MARGIN, l.headerTop, 400, 80)); names.add("header");
            blocks.add(l.nameRect()); names.add("name");
            for (int i = 0; i < l.cells.length; i++) { blocks.add(l.cells[i]); names.add("cell " + i); }
            for (int i = 0; i < l.groups.length; i++) { blocks.add(l.groups[i]); names.add("group " + i); }
            blocks.add(l.tile()); names.add("code");
            float[] t = l.tile();
            blocks.add(new float[] { t[0], l.scanTop, t[2], l.scanTop + 36 }); names.add("scan");
            assertEquals(l.groupStart[3] + (profile ? 8 : 0), l.cells.length, "one per setting");
            assertEquals(profile ? 4 : 3, l.groups.length, "one title per group");
            String card = RecipeCardLayout.LABEL[s] + (profile ? " profile" : "") + (effect ? " effect" : "");
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
            for (int g = 0; g < l.groups.length; g++) {
                float[] title = l.groups[g];
                assertTrue(RecipeCardLayout.TITLE_TOP + RecipeCardLayout.TITLE_SIZE * 1.3f <= title[3] - title[1], card + ": a group title's line fits");
                int first = l.groupStart[g];
                assertEquals(title[0], l.cells[first][0], 0.01, card + ": group " + g + " starts under its title");
                assertTrue(l.cells[first][1] >= title[3] - 0.01f, card + ": group " + g + " under its title");
            }
            for (float[] c : l.cells) {
                assertTrue(l.valueTop + l.valueSize * 1.3f <= c[3] - c[1] + 0.01f, card + ": a value's line stays in its row");
                assertTrue(l.labelSize * 1.3f <= l.valueTop + 4, card + ": a label clear of its value");
                assertTrue(c[2] - c[0] - 24 >= 210, card + ": room for S-Gamut3.Cine");
            }
            assertTrue(l.headerTop < l.nameTop);
            assertEquals(l.qr[2] - l.qr[0], l.qr[3] - l.qr[1], 0.01, "square code");
            assertTrue(l.qr[2] - l.qr[0] >= 400, "about 9 px a module");
            float[] band = l.scanBand();
            assertTrue(band[0] <= t[0] && band[2] >= t[2], "the scan line is centred under the code");
        }
    }

    @Test void theSameCodesAndSizesAsStudio() {
        RecipeCardLayout p = RecipeCardLayout.of(RecipeCardLayout.PORTRAIT);
        assertArrayEquals(new float[] { 320, 800, 760, 1240 }, p.qr, 0.01f);
        assertEquals(84, p.headerTop, 0.01); assertEquals(224, p.nameTop, 0.01); assertEquals(1280, p.scanTop, 0.01);
        assertArrayEquals(new float[] { 26, 36, 36 }, new float[] { p.labelSize, p.valueTop, p.valueSize }, 0.01f);
        assertArrayEquals(new int[] { 0, 4, 5, 7 }, p.groupStart);
        assertArrayEquals(new int[] { 0, 1, 2, 3 }, RecipeCardLayout.of(RecipeCardLayout.PORTRAIT, false, true).groupStart, "an effect: no style settings, no DRO");
        RecipeCardLayout q = RecipeCardLayout.of(RecipeCardLayout.SQUARE);
        assertArrayEquals(new float[] { 588, 340, 1008, 760 }, q.qr, 0.01f);
        assertArrayEquals(new float[] { 26, 30, 32 }, new float[] { q.labelSize, q.valueTop, q.valueSize }, 0.01f);
        RecipeCardLayout s = RecipeCardLayout.of(RecipeCardLayout.STORY);
        assertArrayEquals(new float[] { 280, 1050, 800, 1570 }, s.qr, 0.01f);
        assertEquals(1614, s.scanTop, 0.01);
        assertEquals(10, RecipeCardLayout.TITLE_TOP, 0.01); assertEquals(22, RecipeCardLayout.TITLE_SIZE, 0.01);
        RecipeCardLayout pp = RecipeCardLayout.of(RecipeCardLayout.PORTRAIT, true, false);
        assertArrayEquals(new float[] { 340, 840, 740, 1240 }, pp.qr, 0.01f, "a little smaller");
        assertEquals(208, pp.nameTop, 0.01, "the name a little higher");
        assertArrayEquals(new float[] { 24, 28, 30 }, new float[] { pp.labelSize, pp.valueTop, pp.valueSize }, 0.01f);
        RecipeCardLayout qp = RecipeCardLayout.of(RecipeCardLayout.SQUARE, true, false);
        assertArrayEquals(new float[] { 608, 340, 1008, 740 }, qp.qr, 0.01f);
        assertEquals(780, qp.scanTop, 0.01);
        assertArrayEquals(new float[] { 26, 30, 30 }, new float[] { qp.labelSize, qp.valueTop, qp.valueSize }, 0.01f);
        RecipeCardLayout sp = RecipeCardLayout.of(RecipeCardLayout.STORY, true, false);
        assertArrayEquals(new float[] { 300, 1080, 780, 1560 }, sp.qr, 0.01f);
        assertEquals(400, sp.nameTop, 0.01);
        assertEquals(1604, sp.scanTop, 0.01);
    }

    @Test void theSameCellsAsStudioInEveryVariant() {
        Object[][] cases = {
            { RecipeCardLayout.PORTRAIT, false, false, new float[][] { { 72, 412, 1008, 512 }, { 72, 512, 384, 612 }, { 384, 512, 696, 612 }, { 696, 512, 1008, 612 }, { 72, 652, 384, 752 }, { 384, 652, 696, 752 }, { 696, 652, 1008, 752 } }, new float[][] { { 72, 372, 1008, 412 }, { 72, 612, 384, 652 }, { 384, 612, 1008, 652 } } },
            { RecipeCardLayout.PORTRAIT, false, true, new float[][] { { 72, 412, 1008, 512 }, { 72, 552, 384, 652 }, { 384, 552, 696, 652 } }, new float[][] { { 72, 372, 1008, 412 }, { 72, 512, 384, 552 }, { 384, 512, 1008, 552 } } },
            { RecipeCardLayout.PORTRAIT, true, false, new float[][] { { 72, 340, 1008, 408 }, { 72, 408, 384, 476 }, { 384, 408, 696, 476 }, { 696, 408, 1008, 476 }, { 72, 516, 384, 584 }, { 384, 516, 696, 584 }, { 696, 516, 1008, 584 }, { 72, 624, 384, 692 }, { 384, 624, 696, 692 }, { 696, 624, 1008, 692 }, { 72, 692, 384, 760 }, { 384, 692, 696, 760 }, { 696, 692, 1008, 760 }, { 72, 760, 384, 828 }, { 384, 760, 696, 828 } }, new float[][] { { 72, 300, 1008, 340 }, { 72, 476, 384, 516 }, { 384, 476, 1008, 516 }, { 72, 584, 1008, 624 } } },
            { RecipeCardLayout.PORTRAIT, true, true, new float[][] { { 72, 340, 1008, 408 }, { 72, 448, 384, 516 }, { 384, 448, 696, 516 }, { 72, 556, 384, 624 }, { 384, 556, 696, 624 }, { 696, 556, 1008, 624 }, { 72, 624, 384, 692 }, { 384, 624, 696, 692 }, { 696, 624, 1008, 692 }, { 72, 692, 384, 760 }, { 384, 692, 696, 760 } }, new float[][] { { 72, 300, 1008, 340 }, { 72, 408, 384, 448 }, { 384, 408, 1008, 448 }, { 72, 516, 1008, 556 } } },
            { RecipeCardLayout.SQUARE, false, false, new float[][] { { 72, 352, 548, 424 }, { 72, 424, 548, 496 }, { 72, 496, 548, 568 }, { 72, 568, 548, 640 }, { 72, 680, 548, 752 }, { 72, 792, 548, 864 }, { 72, 864, 548, 936 } }, new float[][] { { 72, 312, 548, 352 }, { 72, 640, 548, 680 }, { 72, 752, 548, 792 } } },
            { RecipeCardLayout.SQUARE, false, true, new float[][] { { 72, 352, 548, 424 }, { 72, 464, 548, 536 }, { 72, 576, 548, 648 } }, new float[][] { { 72, 312, 548, 352 }, { 72, 424, 548, 464 }, { 72, 536, 548, 576 } } },
            { RecipeCardLayout.SQUARE, true, false, new float[][] { { 72, 352, 328, 424 }, { 72, 424, 328, 496 }, { 72, 496, 328, 568 }, { 72, 568, 328, 640 }, { 72, 680, 328, 752 }, { 72, 792, 328, 864 }, { 72, 864, 328, 936 }, { 328, 352, 584, 424 }, { 328, 424, 584, 496 }, { 328, 496, 584, 568 }, { 328, 568, 584, 640 }, { 328, 640, 584, 712 }, { 328, 712, 584, 784 }, { 328, 784, 584, 856 }, { 328, 856, 584, 928 } }, new float[][] { { 72, 312, 328, 352 }, { 72, 640, 328, 680 }, { 72, 752, 328, 792 }, { 328, 312, 584, 352 } } },
            { RecipeCardLayout.SQUARE, true, true, new float[][] { { 72, 352, 328, 424 }, { 72, 464, 328, 536 }, { 72, 576, 328, 648 }, { 328, 352, 584, 424 }, { 328, 424, 584, 496 }, { 328, 496, 584, 568 }, { 328, 568, 584, 640 }, { 328, 640, 584, 712 }, { 328, 712, 584, 784 }, { 328, 784, 584, 856 }, { 328, 856, 584, 928 } }, new float[][] { { 72, 312, 328, 352 }, { 72, 424, 328, 464 }, { 72, 536, 328, 576 }, { 328, 312, 584, 352 } } },
            { RecipeCardLayout.STORY, false, false, new float[][] { { 72, 610, 1008, 715 }, { 72, 715, 384, 820 }, { 384, 715, 696, 820 }, { 696, 715, 1008, 820 }, { 72, 860, 384, 965 }, { 384, 860, 696, 965 }, { 696, 860, 1008, 965 } }, new float[][] { { 72, 570, 1008, 610 }, { 72, 820, 384, 860 }, { 384, 820, 1008, 860 } } },
            { RecipeCardLayout.STORY, false, true, new float[][] { { 72, 610, 1008, 715 }, { 72, 755, 384, 860 }, { 384, 755, 696, 860 } }, new float[][] { { 72, 570, 1008, 610 }, { 72, 715, 384, 755 }, { 384, 715, 1008, 755 } } },
            { RecipeCardLayout.STORY, true, false, new float[][] { { 72, 532, 1008, 608 }, { 72, 608, 384, 684 }, { 384, 608, 696, 684 }, { 696, 608, 1008, 684 }, { 72, 724, 384, 800 }, { 384, 724, 696, 800 }, { 696, 724, 1008, 800 }, { 72, 840, 384, 916 }, { 384, 840, 696, 916 }, { 696, 840, 1008, 916 }, { 72, 916, 384, 992 }, { 384, 916, 696, 992 }, { 696, 916, 1008, 992 }, { 72, 992, 384, 1068 }, { 384, 992, 696, 1068 } }, new float[][] { { 72, 492, 1008, 532 }, { 72, 684, 384, 724 }, { 384, 684, 1008, 724 }, { 72, 800, 1008, 840 } } },
            { RecipeCardLayout.STORY, true, true, new float[][] { { 72, 532, 1008, 608 }, { 72, 648, 384, 724 }, { 384, 648, 696, 724 }, { 72, 764, 384, 840 }, { 384, 764, 696, 840 }, { 696, 764, 1008, 840 }, { 72, 840, 384, 916 }, { 384, 840, 696, 916 }, { 696, 840, 1008, 916 }, { 72, 916, 384, 992 }, { 384, 916, 696, 992 } }, new float[][] { { 72, 492, 1008, 532 }, { 72, 608, 384, 648 }, { 384, 608, 1008, 648 }, { 72, 724, 1008, 764 } } },
        };
        for (Object[] k : cases) {
            RecipeCardLayout l = RecipeCardLayout.of((Integer) k[0], (Boolean) k[1], (Boolean) k[2]);
            String why = RecipeCardLayout.LABEL[(Integer) k[0]] + (((Boolean) k[1]) ? " profile" : "") + (((Boolean) k[2]) ? " effect" : "");
            float[][] cells = (float[][]) k[3], groups = (float[][]) k[4];
            assertEquals(cells.length, l.cells.length, why + " cells");
            assertEquals(groups.length, l.groups.length, why + " groups");
            for (int i = 0; i < cells.length; i++) assertArrayEquals(cells[i], l.cells[i], 0.01f, why + " cell " + i);
            for (int i = 0; i < groups.length; i++) assertArrayEquals(groups[i], l.groups[i], 0.01f, why + " group " + i);
        }
    }
    @Test void theCardListsWhatTheAppShowsForTheRecipe() {
        Recipes.Recipe r = new Recipes.Recipe(Recipes.CUSTOM, "Style", Recipes.AUTUMN, 2, -1, 0, Params.WB_KELVIN, 5600, 3, -2, 0, -4, 0, 0);
        String[][] c = RecipeCardLayout.cells(r);
        String[][] want = {
            { "Creative Style", "Autumn" }, { "Saturation", "+2" }, { "Contrast", "-1" }, { "Sharpness", "0" },
            { "White balance", "5600K A3 M2" }, { "Exposure", "-1.3 EV" }, { "DRO", "Off" },
        };
        assertEquals(want.length, c.length, "a style: its settings, no effect");
        for (int i = 0; i < want.length; i++) assertArrayEquals(want[i], c[i], "cell " + i);
        Recipes.Recipe fx = new Recipes.Recipe(Recipes.CUSTOM, "Effect", Recipes.STD, 2, -1, 0, Params.WB_AUTO, 0, 0, 0, 1, 0, 3, 2);
        String[][] e = RecipeCardLayout.cells(fx);
        assertEquals(3, e.length, "an effect: no style settings, no DRO");
        assertArrayEquals(new String[] { "Picture Effect", "Toy camera · Warm" }, e[0]);
        assertEquals("White balance", e[1][0]);
        assertEquals("Exposure", e[2][0]);
        Recipes.Recipe auto = new Recipes.Recipe(Recipes.CUSTOM, "A", Recipes.STD, 0, 0, 0, Params.WB_AUTO, 0, -7, 7, 0, 0, Recipes.DRO_AUTO, 0);
        String[][] a = RecipeCardLayout.cells(auto);
        assertEquals("Auto B7 G7", a[4][1]);
        assertEquals("0 EV", a[5][1]);
        assertEquals("Auto", a[6][1]);
        Recipes.Recipe keep = new Recipes.Recipe(Recipes.CUSTOM, "K", Recipes.STD, 0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 0);
        assertEquals("As set", RecipeCardLayout.cells(keep)[4][1]);
        assertEquals("+1.0 EV", RecipeCardLayout.cells(keep)[5][1]);
        assertEquals("Level 2", RecipeCardLayout.cells(keep)[6][1], "a DRO level, in words as Studio has it");
        assertArrayEquals(new String[] { "Look", "White balance", "Exposure" }, RecipeCardLayout.groupTitles(3));
        assertEquals("Picture Profile", RecipeCardLayout.groupTitles(4)[3]);
        assertEquals("Scan to add · recipelab.io", RecipeCardLayout.scanLine());
    }

    @Test void aProfileRecipesCardListsItsProfileAfterTheSettings() {
        Recipes.Recipe r = new Recipes.Recipe(Recipes.CUSTOM, "Log", Recipes.STD, 0, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, Recipes.DRO_OFF, 0,
                new IspProfiles.Look(IspProfiles.SLOG3, IspProfiles.SGAMUT3_CINE, -3, 5, IspProfiles.WIDE, 95, -2, 12, -1, 4));
        String[][] c = RecipeCardLayout.cells(r);
        String[][] want = {
            { "Gamma", "S-Log3" }, { "Colour mode", "S-Gamut3.Cine" }, { "Black level", "-3" }, { "Black gamma", "+5 · Wide" },
            { "Knee", "95% · -2" }, { "Colour saturation", "+12" }, { "Colour phase", "-1" }, { "S-curve", "+4" },
        };
        assertEquals(15, c.length);
        assertEquals("DRO", c[6][0], "the last of Exposure");
        for (int i = 0; i < want.length; i++) assertArrayEquals(want[i], c[7 + i], "profile cell " + i);
        Recipes.Recipe neutral = new Recipes.Recipe(Recipes.CUSTOM, "N", Recipes.STD, 0, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, Recipes.DRO_OFF, 0,
                new IspProfiles.Look(IspProfiles.HLG, IspProfiles.CAMERA, 0, 0, IspProfiles.NARROW, IspProfiles.KNEE_OFF, 3, 0, 0, 0));
        String[][] n = RecipeCardLayout.cells(neutral);
        assertEquals("ITU709 Matrix", n[8][1]);
        assertEquals("0", n[10][1], "no black gamma: no range");
        assertEquals("Off", n[11][1], "no knee: no slope");
        Recipes.Recipe flat = new Recipes.Recipe(Recipes.CUSTOM, "K", Recipes.STD, 0, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, Recipes.DRO_OFF, 0,
                new IspProfiles.Look(IspProfiles.ITU709, IspProfiles.BT2020, 0, 0, IspProfiles.MIDDLE, 105, 0, 0, 0, 0));
        assertEquals("105%", RecipeCardLayout.cells(flat)[11][1], "a knee with no slope");
        for (Recipes.Recipe b : Recipes.ALL) {
            RecipeCardLayout l = RecipeCardLayout.of(RecipeCardLayout.PORTRAIT, b);
            assertEquals(l.cells.length, RecipeCardLayout.cells(b).length, b.name + ": a place for every setting");
        }
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
