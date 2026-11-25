package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import io.nayuki.qrcodegen.QrCode;
import org.junit.jupiter.api.Test;

/**
 * Recipe share links. The vectors file is the same in Recipe Lab Studio, which runs it too: both apps must write the
 * same code for a recipe and refuse the same broken ones.
 */
class RecipeShareTest {
    private static List<String[]> vectors(String kind) throws IOException {
        List<String[]> out = new ArrayList<>();
        for (String l : Files.readAllLines(Paths.get("test/fixtures/recipe-share-vectors.tsv"), StandardCharsets.UTF_8)) {
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] v = l.split("\t", -1);
            if (v[0].equals(kind)) out.add(v);
        }
        return out;
    }

    private static int indexOf(String[] a, String s) {
        for (int i = 0; i < a.length; i++) if (s.equals(a[i])) return i;
        throw new AssertionError("not in table: " + s);
    }

    /** a vector's recipe, built from its file spellings through the camera's own tables */
    private static Recipes.Recipe recipe(String[] v) {
        int style = indexOf(Recipes.STYLE_NAMES, v[3]);
        int wb, kelvin = 0;
        if (v[7].equals("auto")) wb = Params.WB_AUTO;
        else if (v[7].equals("keep")) wb = 0;
        else { wb = Params.WB_KELVIN; kelvin = Integer.parseInt(v[7].replace("K", "")); }
        int dro = v[11].equals("auto") ? Recipes.DRO_AUTO : v[11].equals("off") ? Recipes.DRO_OFF : Integer.parseInt(v[11]);
        int pe = indexOf(Recipes.PE_KEYS, v[12]);
        int sub = v[13].equals("-") ? 0 : indexOf(Recipes.subValues(pe), v[13]);
        return new Recipes.Recipe(Recipes.CUSTOM, v[2], style, Integer.parseInt(v[4]), Integer.parseInt(v[5]),
                Integer.parseInt(v[6]), wb, kelvin, Integer.parseInt(v[8]), Integer.parseInt(v[9]), pe,
                Integer.parseInt(v[10]), dro, sub);
    }

    /** a version 2 vector's recipe: the version 1 columns, then the picture profile in its file spellings */
    private static Recipes.Recipe profiled(String[] v) {
        Recipes.Recipe r = recipe(v);
        int knee = v[19].equals("off") ? IspProfiles.KNEE_OFF : Integer.parseInt(v[19]);
        IspProfiles.Look l = new IspProfiles.Look(indexOf(IspProfiles.CURVE_CODES, v[14]), indexOf(IspProfiles.GAMUT_CODES, v[15]),
                Integer.parseInt(v[16]), Integer.parseInt(v[17]), indexOf(IspProfiles.RANGE_CODES, v[18]), knee,
                Integer.parseInt(v[20]), Integer.parseInt(v[21]), Integer.parseInt(v[22]), Integer.parseInt(v[23]));
        return new Recipes.Recipe(r.group, r.name, r.style, r.sat, r.con, r.sharp, r.wbMode, r.kelvin, r.ab, r.gm, r.pe, r.ev, r.dro, r.sub, l);
    }

    private static void assertSameRecipe(Recipes.Recipe want, Recipes.Recipe got, String why) {
        assertNotNull(got, why);
        assertEquals(want.name, got.name, why);
        assertEquals(want.look, got.look, why);
        assertEquals(Recipes.CUSTOM, got.group, why);
        int[] w = { want.style, want.sat, want.con, want.sharp, want.wbMode, want.kelvin, want.ab, want.gm, want.pe, want.ev, want.dro, want.sub };
        int[] g = { got.style, got.sat, got.con, got.sharp, got.wbMode, got.kelvin, got.ab, got.gm, got.pe, got.ev, got.dro, got.sub };
        assertArrayEquals(w, g, why);
    }

    @Test void theVectorsFileHasBothKinds() throws IOException {
        assertTrue(vectors("valid").size() >= 9);
        assertTrue(vectors("invalid").size() >= 25);
        assertTrue(vectors("profile").size() >= 4);
    }

    @Test void everyProfileVectorEncodesToItsCodeAndBack() throws IOException {
        for (String[] v : vectors("profile")) {
            Recipes.Recipe r = profiled(v);
            assertEquals(RecipeShare.PREFIX + v[1], RecipeShare.encode(r), v[2]);
            assertSameRecipe(r, RecipeShare.decode(RecipeShare.PREFIX + v[1]), v[2]);
            assertSameRecipe(r, RecipeShare.decode("https://www.recipelab.io/r#" + v[1]), "www " + v[2]);
        }
    }

    @Test void onlyARecipeWithAProfileIsWrittenAsVersionTwo() throws IOException {
        for (String[] v : vectors("valid")) assertEquals('A', v[1].charAt(0), "version 1: " + v[2]);    // byte 0 = 1
        for (String[] v : vectors("profile")) assertTrue(v[1].startsWith("Ag"), "version 2: " + v[2]);
        Recipes.Recipe plain = RecipeShare.decode(RecipeShare.PREFIX + "AQAAAwMDAAcHDwYAAEE");
        assertNull(plain.look);
        assertTrue(RecipeShare.encode(plain).startsWith(RecipeShare.PREFIX + "AQ"));
    }

    @Test void aVersionTwoLinkReadsAProfileValueThatDoesNothingAsItsDefault() {
        // S-Log3 Cine with black gamma range wide (no black gamma) and knee slope +2 (no knee): the same look
        Recipes.Recipe r = RecipeShare.decode(RecipeShare.PREFIX + "AgAAAwMDAAcHDwAAAAICDwcCAAcgBwdTLUxvZzMgQ2luZQ");
        assertNotNull(r);
        assertEquals(new IspProfiles.Look(IspProfiles.SLOG3, IspProfiles.SGAMUT3_CINE), r.look);
        assertEquals(IspProfiles.MIDDLE, r.look.range);
        assertEquals(0, r.look.slope);
    }

    @Test void everyValidVectorEncodesToItsCode() throws IOException {
        for (String[] v : vectors("valid")) assertEquals(RecipeShare.PREFIX + v[1], RecipeShare.encode(recipe(v)), v[2]);
    }

    @Test void everyValidVectorDecodesToItsRecipe() throws IOException {
        for (String[] v : vectors("valid")) {
            assertSameRecipe(recipe(v), RecipeShare.decode(RecipeShare.PREFIX + v[1]), v[2]);
            assertSameRecipe(recipe(v), RecipeShare.decode("https://www.recipelab.io/r#" + v[1]), "www " + v[2]);
            assertTrue(RecipeShare.isLink(RecipeShare.PREFIX + v[1]));
        }
    }

    @Test void everyInvalidVectorIsRefused() throws IOException {
        for (String[] v : vectors("invalid")) {
            assertNull(RecipeShare.decode(RecipeShare.PREFIX + v[1]), v[2]);
            assertTrue(RecipeShare.isLink(RecipeShare.PREFIX + v[1]), "still a recipe link: " + v[2]);
        }
    }

    @Test void everyBuiltInRoundTripsUnderAUserName() {
        for (Recipes.Recipe b : Recipes.ALL) {
            String name = CustomRecipes.copyName(b.name, new ArrayList<String>());
            Recipes.Recipe r = CustomRecipes.renamed(b, name);
            if (CustomRecipes.problem(r) != null) continue;
            String link = RecipeShare.encode(r);
            assertNotNull(link, b.name);
            assertEquals(b.hasProfile() ? "Ag" : "AQ", link.substring(RecipeShare.PREFIX.length(), RecipeShare.PREFIX.length() + 2), b.name);
            assertSameRecipe(r, RecipeShare.decode(link), b.name);
        }
    }

    @Test void otherTextIsNotARecipe() {
        String code = "AQAAAwMDAAcHDwYAAEE";
        for (String s : new String[] { null, "", "hello", "http://recipelab.io/r#" + code, "https://recipelab.io/s#" + code,
                "https://evil.io/r#" + code, "https://recipelab.io/r/#" + code, "HTTPS://RECIPELAB.IO/R#" + code }) {
            assertNull(RecipeShare.decode(s), String.valueOf(s));
            assertFalse(RecipeShare.isLink(s), String.valueOf(s));
        }
        assertNull(RecipeShare.decode(RecipeShare.PREFIX));
    }

    @Test void encodeRefusesWhatARecipeCannotHold() {
        Recipes.Recipe ok = RecipeShare.decode(RecipeShare.PREFIX + "AQAAAwMDAAcHDwYAAEE");
        assertNotNull(RecipeShare.encode(ok));
        assertNull(RecipeShare.encode(CustomRecipes.renamed(ok, "a/b")));
        assertNull(RecipeShare.encode(CustomRecipes.renamed(ok, "")));
        assertNull(RecipeShare.encode(CustomRecipes.renamed(ok, "xxxxxxxxxxxxxxxxxxxxxxxxx")));
        assertNull(RecipeShare.encode(new Recipes.Recipe(Recipes.CUSTOM, "A", 13, 0, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, 6, 0)), "unknown style");
        assertNull(RecipeShare.encode(new Recipes.Recipe(Recipes.CUSTOM, "A", 1, 4, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, 6, 0)), "saturation 4");
        assertEquals(RecipeShare.PREFIX + "AQAAAwMDAAcHDwYAAEE", RecipeShare.encode(CustomRecipes.renamed(ok, " A ")), "trimmed");
    }

    @Test void base64UrlRoundTripsEveryLength() {
        for (int n = 1; n < 40; n++) {
            byte[] b = new byte[n];
            for (int i = 0; i < n; i++) b[i] = (byte) (i * 37 + n * 11);
            String s = RecipeShare.base64Url(b);
            assertFalse(s.contains("=") || s.contains("+") || s.contains("/"), s);
            int[] back = RecipeShare.unBase64Url(s);
            assertEquals(n, back.length);
            for (int i = 0; i < n; i++) assertEquals(b[i] & 0xff, back[i]);
        }
        assertEquals("_-8", RecipeShare.base64Url(new byte[] { (byte) 0xff, (byte) 0xef }));
        assertNull(RecipeShare.unBase64Url("A"));
        assertNull(RecipeShare.unBase64Url(""));
        assertNull(RecipeShare.unBase64Url("AA=="));
        assertNull(RecipeShare.unBase64Url("AB"));
    }

    @Test void theLongestLinkIsASmallQrCode() {
        Recipes.Recipe r = new Recipes.Recipe(Recipes.CUSTOM, "xxxxxxxxxxxxxxxxxxxxxxxx", 1, 0, 0, 0, Params.WB_KELVIN, 9900, 0, 0, 1, 0, 6, 4);
        assertTrue(QrCode.encodeText(RecipeShare.encode(r), QrCode.Ecc.MEDIUM).size <= 37, "version 1: a version 5 code");
        r = new Recipes.Recipe(Recipes.CUSTOM, "xxxxxxxxxxxxxxxxxxxxxxxx", 1, 0, 0, 0, Params.WB_KELVIN, 9900, 0, 0, 1, 0, 6, 4,
                new IspProfiles.Look(IspProfiles.HLG, IspProfiles.BT2020, 15, 7, IspProfiles.WIDE, 105, 5, 32, 7, 7));
        QrCode qr = QrCode.encodeText(RecipeShare.encode(r), QrCode.Ecc.MEDIUM);
        assertTrue(qr.size <= 41, "version 2: a version 6 code, modules " + qr.size);
        // finder pattern in the top left corner
        for (int i = 0; i < 7; i++) { assertTrue(qr.getModule(i, 0)); assertTrue(qr.getModule(0, i)); }
        assertFalse(qr.getModule(1, 1));
        assertTrue(qr.getModule(3, 3));
    }

    @Test void theFamilyIsByteOneAndOnlyThisOneIsRead() {
        int[] b = RecipeShare.unBase64Url(RecipeShare.encode(RecipeShare.decode(RecipeShare.PREFIX + "AQAAAwMDAAcHDwYAAEE")).substring(RecipeShare.PREFIX.length()));
        assertEquals(1, b[0]);
        assertEquals(RecipeShare.FAMILY_CODE, b[1]);
        assertEquals(0, RecipeShare.FAMILY_CODE);
        assertNull(RecipeShare.decode(RecipeShare.PREFIX + "AQEAAwMDAAcHDwYAAEE"), "family 1");
        assertNull(RecipeShare.decode(RecipeShare.PREFIX + "AQA"), "a family and nothing after");
    }
}
