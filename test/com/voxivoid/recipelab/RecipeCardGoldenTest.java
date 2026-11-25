package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * test/fixtures/card-golden.txt: what this app's recipe card (Share) says and where it puts it, so Recipe Lab Studio's
 * card is the same picture. Studio keeps the same file (test/fixtures/card-golden.txt) and checks its recipeCardCells
 * and RecipeCardLayout against it. Each recipe line starts with the recipe's share code, which Studio decodes, so both
 * apps read the same recipe. The file is written from this class: after a change to the card, run the tests with
 * RECIPELAB_WRITE_GOLDEN=1 and copy the file to Studio.
 */
class RecipeCardGoldenTest {

    static final Path FILE = Paths.get("test/fixtures/card-golden.txt");
    static final String HEAD =
            "# Recipe Lab (camera) recipe card, in English: what it lists and where; the same file is in Recipe Lab Studio.\n"
          + "# Written by RecipeCardGoldenTest (RECIPELAB_WRITE_GOLDEN=1 tools/test.sh RecipeCardGolden), never by hand.\n"
          + "# cells|share code|label=value|...  (every built-in recipe, then every style, effect and option, white balance,\n"
          + "#   DRO, exposure and picture profile setting)\n"
          + "# layout|shape,profile,effect|headerTop,nameTop,rowHeight,labelSize,valueTop,valueSize,scanTop|qr|scan band|name|groups|cells\n"
          + "#   each rectangle left,top,right,bottom; a list of them separated by ';'\n";

    static final int AUTO = Params.WB_AUTO, KEEP = 0, K = Params.WB_KELVIN;

    static Recipes.Recipe recipe(int style, int sat, int con, int sharp, int wb, int kelvin, int ab, int gm, int pe, int ev, int dro, int sub, IspProfiles.Look l) {
        return new Recipes.Recipe(Recipes.CUSTOM, "Card", style, sat, con, sharp, wb, kelvin, ab, gm, pe, ev, dro, sub, l);
    }

    /** every value each setting on the card can show, each at least once */
    static List<Recipes.Recipe> recipes() {
        List<Recipes.Recipe> all = new ArrayList<Recipes.Recipe>();
        for (Recipes.Recipe r : Recipes.ALL) all.add(CustomRecipes.renamed(r, "Card"));
        for (int s = 0; s < Recipes.STYLE_NAMES.length; s++)
            if (Recipes.styleKnown(s)) all.add(recipe(s, s % 7 - 3, 3 - s % 7, s % 5 - 2, AUTO, 0, 0, 0, 0, 0, Recipes.DRO_AUTO, 0, null));
        for (int pe = 1; pe < Recipes.PE_KEYS.length; pe++) {
            String[] v = Recipes.subValues(pe);
            for (int sub = 0; sub < (v == null ? 1 : v.length); sub++) all.add(recipe(1, 0, 0, 0, AUTO, 0, 0, 0, pe, 0, Recipes.DRO_AUTO, sub, null));
        }
        int[][] wb = { { KEEP, 0, 0, 0 }, { K, 2500, -7, 7 }, { K, 5600, 3, 0 }, { K, 9900, 0, -4 }, { AUTO, 0, 7, -7 } };
        for (int[] w : wb) all.add(recipe(1, 0, 0, 0, w[0], w[1], w[2], w[3], 0, 0, Recipes.DRO_AUTO, 0, null));
        for (int dro = 0; dro <= Recipes.DRO_AUTO; dro++) all.add(recipe(1, 0, 0, 0, AUTO, 0, 0, 0, 0, 0, dro, 0, null));
        for (int ev = -15; ev <= 15; ev += 4) all.add(recipe(1, 0, 0, 0, AUTO, 0, 0, 0, 0, ev, Recipes.DRO_AUTO, 0, null));
        for (String[] l : IspGoldenTest.LOOKS) all.add(recipe(1, 0, 0, 0, AUTO, 0, 0, 0, 0, 0, Recipes.DRO_OFF, 0, IspGoldenTest.look(l[1])));
        all.add(recipe(1, 0, 0, 0, AUTO, 0, 0, 0, 9, 0, Recipes.DRO_OFF, 0, IspGoldenTest.look("cine4,bt2020,0,-3,narrow,95,0,0,0,0")));
        return all;
    }

    static String num(float f) { return f == (long) f ? String.valueOf((long) f) : String.valueOf(f); }

    static String rect(float[] r) { return num(r[0]) + "," + num(r[1]) + "," + num(r[2]) + "," + num(r[3]); }

    static String rects(float[][] rs) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < rs.length; i++) s.append(i == 0 ? "" : ";").append(rect(rs[i]));
        return s.toString();
    }

    static String file() {
        StringBuilder s = new StringBuilder(HEAD);
        for (Recipes.Recipe r : recipes()) {
            String link = RecipeShare.encode(r);
            assertNotNull(link, r.name);
            s.append("cells|").append(link.substring(link.indexOf('#') + 1));
            for (String[] c : RecipeCardLayout.cells(r)) s.append('|').append(c[0]).append('=').append(c[1]);
            s.append('\n');
        }
        for (int shape = 0; shape < RecipeCardLayout.SHAPES; shape++)
            for (int profile = 0; profile < 2; profile++)
                for (int effect = 0; effect < 2; effect++) {
                    RecipeCardLayout l = RecipeCardLayout.of(shape, profile == 1, effect == 1);
                    s.append("layout|").append(RecipeCardLayout.LABEL[shape]).append(',').append(profile == 1).append(',').append(effect == 1)
                     .append('|').append(num(l.headerTop)).append(',').append(num(l.nameTop)).append(',').append(num(l.rowHeight))
                     .append(',').append(num(l.labelSize)).append(',').append(num(l.valueTop)).append(',').append(num(l.valueSize))
                     .append(',').append(num(l.scanTop))
                     .append('|').append(rect(l.qr)).append('|').append(rect(l.scanBand())).append('|').append(rect(l.nameRect()))
                     .append('|').append(rects(l.groups)).append('|').append(rects(l.cells)).append('\n');
                }
        return s.toString();
    }

    @Test void theFileIsWhatThisAppDraws() throws Exception {
        String want = file();
        if ("1".equals(System.getenv("RECIPELAB_WRITE_GOLDEN"))) Files.write(FILE, want.getBytes(StandardCharsets.UTF_8));
        assertEquals(want, new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8),
                "run RECIPELAB_WRITE_GOLDEN=1 tools/test.sh RecipeCardGolden, then copy the file to Studio");
    }

    @Test void theFileShowsEveryEffectOptionAndDroAsStudioDoes() throws Exception {
        String f = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8);
        for (String s : new String[] { "Picture Effect=Soft high-key · Blue", "Picture Effect=Posterization · B&W", "Picture Effect=Posterization · Colour",
                "Picture Effect=Toy camera · Normal", "Picture Effect=HDR art|", "DRO=Auto", "DRO=Off", "DRO=Level 3", "Knee=Off",
                "White balance=Auto A7 M7", "White balance=As set", "Gamma=Cine4" })
            assertTrue(f.contains(s), s);
        assertFalse(f.contains("Picture Effect=HDR art|Saturation"), "an effect lists no style settings");
    }
}
