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
        int[][] sizes = { { 1080, 1350 }, { 1080, 1080 }, { 1080, 1920 } };
        for (int s = 0; s < RecipeCardLayout.SHAPES; s++) {
            RecipeCardLayout l = RecipeCardLayout.of(s);
            assertEquals(s, l.shape);
            assertEquals(sizes[s][0], l.width);
            assertEquals(sizes[s][1], l.height);
            String[] wh = RecipeCardLayout.LABEL[s].split(":");
            assertEquals(l.width * Integer.parseInt(wh[1]), l.height * Integer.parseInt(wh[0]), "its own ratio");
            assertEquals(0, l.width % 2, "UYVY: an even width");
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
        for (int s = 0; s < RecipeCardLayout.SHAPES; s++) {
            RecipeCardLayout l = RecipeCardLayout.of(s);
            List<float[]> blocks = new ArrayList<float[]>();
            List<String> names = new ArrayList<String>();
            blocks.add(RecipeCardLayout.rect(RecipeCardLayout.MARGIN, l.headerTop, 400, 80)); names.add("header");
            blocks.add(l.nameRect()); names.add("name");
            for (int i = 0; i < l.cells.length; i++) { blocks.add(l.cells[i]); names.add("cell " + i); }
            blocks.add(l.tile()); names.add("code");
            float[] t = l.tile();
            blocks.add(new float[] { t[0], l.scanTop, t[2], l.scanTop + 36 }); names.add("scan");
            assertEquals(8, l.cells.length, "one per setting");
            for (int i = 0; i < blocks.size(); i++) {
                float[] b = blocks.get(i);
                String why = RecipeCardLayout.LABEL[s] + " " + names.get(i);
                assertTrue(b[0] >= RecipeCardLayout.MARGIN - 12 && b[2] <= l.width - RecipeCardLayout.MARGIN + 12, why + " keeps the margin");
                assertTrue(b[1] >= 0 && b[3] <= l.height, why + " inside");
                if (s == RecipeCardLayout.STORY)
                    assertTrue(b[1] >= RecipeCardLayout.STORY_SAFE_TOP && b[3] <= RecipeCardLayout.STORY_SAFE_BOTTOM, why + " clear of the story bars");
                for (int j = i + 1; j < blocks.size(); j++)
                    assertFalse(overlap(b, blocks.get(j)), RecipeCardLayout.LABEL[s] + " " + names.get(i) + " and " + names.get(j));
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
}
