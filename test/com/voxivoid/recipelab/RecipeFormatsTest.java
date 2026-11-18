package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The custom recipe file's versions: each file read by the reader of the version it names, the newest one written,
 * every writer readable by its own reader — so a version added later leaves every file written before it loading.
 */
class RecipeFormatsTest {
    /** a version this build does not have yet: reads only a name, under a key formats 1 to 3 do not know */
    private static final RecipeFormat FAKE_V4 = new RecipeFormat() {
        public int version() { return 4; }
        public CustomRecipes.Parsed read(Map<String, String> keys, String fileName) {
            return CustomRecipes.Parsed.ok(CustomRecipes.renamed(Recipes.ALL[Recipes.FACTORY], keys.get("title")));
        }
        public String write(Recipes.Recipe r, String madeOn) { return "format: 4\ntitle: " + r.name + "\n"; }
    };
    private static final RecipeFormat[] WITH_V4 = { new RecipeFormatV1(), new RecipeFormatV2(), new RecipeFormatV3(), FAKE_V4 };

    @Test void versionsAreNumberedOneUpInOrderAndTheLastIsWritten() {
        for (int i = 0; i < RecipeFormats.ALL.length; i++) assertEquals(i + 1, RecipeFormats.ALL[i].version(), "version " + (i + 1));
        assertSame(RecipeFormats.ALL[RecipeFormats.ALL.length - 1], RecipeFormats.current());
    }

    @Test void everyWriterReadsBackThroughItsOwnReader() {
        Recipes.Recipe r = CustomRecipes.renamed(Recipes.ALL[3], "Round Trip");
        for (RecipeFormat f : RecipeFormats.ALL) {
            String text = f.write(r, "ILCE-6000");
            assertTrue(text.contains("format: " + f.version()), "a file names its version, v" + f.version());
            CustomRecipes.Parsed p = RecipeFormats.parse(text, "x.YML");
            assertNull(p.error, "v" + f.version());
            assertEquals(r.name, p.recipe.name);
            assertEquals(r.style, p.recipe.style);
        }
    }

    @Test void eachFileIsReadByTheVersionItNames() {
        CustomRecipes.Parsed v4 = RecipeFormats.parse("format: 4\ntitle: From The Future\n", "x.YML", WITH_V4);
        assertEquals("From The Future", v4.recipe.name, "read by the version 4 reader");
        CustomRecipes.Parsed v1 = RecipeFormats.parse(new RecipeFormatV1().write(CustomRecipes.renamed(Recipes.ALL[3], "Old File"), null), "x.YML", WITH_V4);
        assertNull(v1.error);
        assertEquals("Old File", v1.recipe.name, "a version 1 file still reads with the version 1 reader once later versions exist");
        assertEquals(Recipes.ALL[3].style, v1.recipe.style);
    }

    @Test void aFileWithoutAFormatLineIsVersionOne() {
        assertEquals("Plain", RecipeFormats.parse("name: Plain\n", "x.YML", WITH_V4).recipe.name);
    }

    @Test void aVersionThisBuildDoesNotKnowIsSkippedWhole() {
        assertEquals("made by a newer Recipe Lab (format 4)", RecipeFormats.parse("format: 4\ntitle: x\n", "x.YML").error);
        assertEquals("made by a newer Recipe Lab (format 5)", RecipeFormats.parse("format: 5\n", "x.YML", WITH_V4).error);
        assertEquals("format: 0 is not allowed", RecipeFormats.parse("format: 0\n", "x.YML").error);
        assertEquals("format: two is not allowed", RecipeFormats.parse("format: two\n", "x.YML").error);
    }

    @Test void theEnvelopeIsSharedByEveryVersion() {
        java.util.Map<String, String> kv = new java.util.HashMap<String, String>();
        assertNull(RecipeFormats.keys("﻿---\n# comment\r\nName: A  # trailing\n\nquoted: 'it''s # here'\nescaped: \"say \\\"hi\\\"\"\n...\n", kv));
        assertEquals("A", kv.get("name"), "keys lower-cased, comments, document markers and the byte-order mark dropped");
        assertEquals("it's # here", kv.get("quoted"), "a single-quoted value keeps its #, and '' is a quote");
        assertEquals("say \"hi\"", kv.get("escaped"));
        assertEquals(3, kv.size());
        assertEquals("saturation is set twice", RecipeFormats.keys("saturation: 1\nSATURATION: 2", new java.util.HashMap<String, String>()));
    }

    @Test void anythingButAFlatYamlMappingIsRefusedWithItsLine() {
        java.util.Map<String, String> kv = new java.util.HashMap<String, String>();
        assertEquals("line 2 is not a YAML key: value", RecipeFormats.keys("name: A\nsaturation = 1\n", kv), "an old key = value file");
        assertEquals("line 2 is not a YAML key: value", RecipeFormats.keys("style:\n  - vivid\n", new java.util.HashMap<String, String>()), "a list");
        assertEquals("line 1 is not a YAML key: value", RecipeFormats.keys("  name: A\n", new java.util.HashMap<String, String>()), "nesting");
        assertEquals("line 1 is not a YAML key: value", RecipeFormats.keys("name: \"A\n", new java.util.HashMap<String, String>()), "a quote that does not close");
        assertEquals("line 1 is not a YAML key: value", RecipeFormats.keys("name: 'A' extra\n", new java.util.HashMap<String, String>()));
    }

    @Test void theWrittenFileIsYamlANameQuoted() {
        String text = RecipeFormats.write(CustomRecipes.renamed(Recipes.ALL[3], "Mr. T's (2)"), "ILCE \"6000\"");
        java.util.Map<String, String> kv = new java.util.HashMap<String, String>();
        assertNull(RecipeFormats.keys(text, kv), text);
        assertEquals("Mr. T's (2)", kv.get("name"));
        assertEquals("ILCE \"6000\"", kv.get("made-on"), "a quote in the camera's name survives");
        assertTrue(text.contains("\nname: \"Mr. T's (2)\""), text);
    }

    // ---- format 1, what mutation testing found unasserted
    private static String v1(Recipes.Recipe r, String madeOn) { return new RecipeFormatV1().write(r, madeOn); }
    private static CustomRecipes.Parsed read(String text) { return RecipeFormats.parse(text, "x.YML"); }

    @Test void v1WritesEachValueWithItsRangeInAColumn() {
        String t = v1(CustomRecipes.renamed(Recipes.ALL[3], "Col"), null);
        assertTrue(t.contains("\nsaturation: " + (Recipes.ALL[3].sat > 0 ? "+" : "") + Recipes.ALL[3].sat), t);
        for (String line : t.split("\n")) {
            int hash = line.indexOf(" # ");
            if (line.startsWith("#") || hash < 0) continue;
            assertEquals(28, hash, "the comments line up after the value: " + line);
        }
        assertTrue(t.contains("# standard vivid neutral portrait landscape mono clear deep light sunset night red-leaves sepia\n"), "every known style, and only those");
        assertTrue(t.contains("# off toy-camera pop-color posterization"), "the effects");
        assertTrue(t.contains("\nsaturation: 0 ") || t.contains("\nsaturation: +") || t.contains("\nsaturation: -"), t);
    }

    @Test void v1WritesNoCameraWhenItIsUnknownOrBlank() {
        assertFalse(v1(Recipes.ALL[3], "   ").contains("made-on"));
        assertFalse(v1(Recipes.ALL[3], null).contains("made-on"));
        assertTrue(v1(Recipes.ALL[3], "ILCE-6000").contains("made-on: \"ILCE-6000\""));
    }

    @Test void v1ReadsEveryValueAtItsEdges() {
        Recipes.Recipe r = read("name: E\nsharpness: 3\ncontrast: -3\nexposure: -5.0\ndro: 5\namber-blue: -7\ngreen-magenta: 7\n").recipe;
        assertEquals(3, r.sharp); assertEquals(-3, r.con); assertEquals(-15, r.ev); assertEquals(5, r.dro); assertEquals(-7, r.ab); assertEquals(7, r.gm);
        assertEquals(1, read("name: E\ndro: 1\n").recipe.dro);
        assertEquals("sharpness: 4 is not allowed", read("name: E\nsharpness: 4\n").error);
        assertEquals("exposure: -5.3 is not allowed", read("name: E\nexposure: -5.3\n").error);
        assertEquals("dro: 0 is not allowed", read("name: E\ndro: 0\n").error, "off is spelled off");
        assertEquals("green-magenta: 8 is not allowed", read("name: E\ngreen-magenta: 8\n").error);
    }

    @Test void v1IgnoresAnOptionForAnEffectThatHasNone() {
        Recipes.Recipe r = read("name: E\neffect: off\neffect-option: purple\n").recipe;
        assertNotNull(r);
        assertEquals(0, r.sub);
        assertEquals(2, read("name: E\neffect: soft-high-key\neffect-option: green\n").recipe.sub);
    }

    @Test void aLoneQuoteIsNotAValue() {
        assertEquals("line 1 is not a YAML key: value", RecipeFormats.keys("name: '\n", new java.util.HashMap<String, String>()));
        java.util.Map<String, String> kv = new java.util.HashMap<String, String>();
        assertNull(RecipeFormats.keys("name: ''\nnote:\n", kv));
        assertEquals("", kv.get("name"), "an empty quoted value");
        assertEquals("", kv.get("note"), "a key with nothing after it");
    }

    @Test void formatTwoNamesTheFamilyAndReadsLikeFormatOne() {
        Recipes.Recipe r = CustomRecipes.renamed(Recipes.ALL[3], "Two");
        String v2 = new RecipeFormatV2().write(r, "ILCE-6000"), v1 = new RecipeFormatV1().write(r, "ILCE-6000");
        assertEquals(v1.replace("\nformat: 1\n", "\nformat: 2\nfamily: sony-creative-style  # the cameras it is for\n"), v2,
                "format 1's file with the version and the family line");
        CustomRecipes.Parsed p = RecipeFormats.parse(v2, "x.YML");
        assertNull(p.error);
        assertEquals(r.style, p.recipe.style);
        assertEquals(r.sat, p.recipe.sat);
    }

    @Test void formatTwoWithoutTheFamilyOrForAnotherIsSkipped() {
        assertEquals("family: missing", RecipeFormats.parse("format: 2\nname: A\n", "x.YML").error);
        assertEquals("family: missing", RecipeFormats.parse("format: 2\nfamily:\nname: A\n", "x.YML").error);
        assertEquals("made for other cameras (canon-picture-style)",
                RecipeFormats.parse("format: 2\nfamily: canon-picture-style\nname: A\nstyle: mono\n", "x.YML").error);
        assertEquals("made for other cameras (Sony-Creative-Style)",
                RecipeFormats.parse("format: 2\nfamily: Sony-Creative-Style\nname: A\n", "x.YML").error, "spelled exactly");
        assertNull(RecipeFormats.parse("format: 1\nfamily: canon-picture-style\nname: A\n", "x.YML").error,
                "format 1 knows no family key: ignored, as any key it does not know");
    }

    // ------------------------------------------------------------ format 3: the picture profile
    private static Recipes.Recipe profiled(IspProfiles.Look l) {
        Recipes.Recipe b = Recipes.ALL[3];
        return new Recipes.Recipe(Recipes.CUSTOM, "Log Look", b.style, b.sat, b.con, b.sharp, b.wbMode, b.kelvin, b.ab, b.gm, b.pe, b.ev, b.dro, b.sub, l);
    }

    @Test void formatThreeIsFormatTwoWithTheProfileAtTheEnd() {
        Recipes.Recipe r = profiled(new IspProfiles.Look(IspProfiles.SLOG3, IspProfiles.SGAMUT3_CINE, -2, 3, IspProfiles.WIDE, 95, -1, 8, -2, 4));
        String v3 = new RecipeFormatV3().write(r, "ILCE-6000"), v2 = new RecipeFormatV2().write(r, "ILCE-6000");
        assertTrue(v3.startsWith(v2.replace("\nformat: 2\n", "\nformat: 3\n")), v3);
        String tail = v3.substring(v2.length());
        assertTrue(tail.startsWith("gamma: s-log3"), tail);
        for (String line : new String[] { "color-mode: s-gamut3-cine", "black-level: -2", "black-gamma: +3", "black-gamma-range: wide",
                "knee: 95", "knee-slope: -1", "color-saturation: +8", "color-phase: -2", "s-curve: +4" })
            assertTrue(tail.contains("\n" + line + " "), line + " in\n" + tail);
    }

    @Test void formatThreeReadsBackEveryProfileValue() {
        IspProfiles.Look[] looks = {
            new IspProfiles.Look(IspProfiles.SLOG2, IspProfiles.SGAMUT3),
            new IspProfiles.Look(IspProfiles.HLG, IspProfiles.BT2020, 15, -7, IspProfiles.NARROW, 105, 5, -32, 7, -7),
            new IspProfiles.Look(IspProfiles.LOGC3, IspProfiles.AWG3, -15, 7, IspProfiles.MIDDLE, 75, -5, 32, -7, 7),
            new IspProfiles.Look(IspProfiles.ITU709, IspProfiles.CAMERA, 0, 0, IspProfiles.MIDDLE, IspProfiles.KNEE_OFF, 0, -32, 0, 0),
        };
        for (IspProfiles.Look l : looks) {
            CustomRecipes.Parsed p = RecipeFormats.parse(RecipeFormats.write(profiled(l), null), "x.YML");
            assertNull(p.error, l.toString());
            assertEquals(l, p.recipe.look, l.toString());
            assertEquals(Recipes.ALL[3].style, p.recipe.style);
        }
    }

    @Test void formatThreeWithoutAGammaIsARecipeWithoutAProfile() {
        String base = "format: 3\nfamily: sony-creative-style\nname: A\n";
        assertNull(RecipeFormats.parse(base, "x.YML").recipe.look);
        assertNull(RecipeFormats.parse(base + "gamma: off\nblack-level: +3\n", "x.YML").recipe.look, "the adjustments need a curve");
        IspProfiles.Look l = RecipeFormats.parse(base + "gamma: hlg\n", "x.YML").recipe.look;
        assertEquals(new IspProfiles.Look(IspProfiles.HLG, IspProfiles.CAMERA), l, "a key left out is the neutral value");
    }

    @Test void formatThreeSkipsAProfileValueOutOfRange() {
        String base = "format: 3\nfamily: sony-creative-style\nname: A\ngamma: s-log3\n";
        assertEquals("gamma: cine4 is not allowed", RecipeFormats.parse("format: 3\nfamily: sony-creative-style\nname: A\ngamma: cine4\n", "x.YML").error);
        assertEquals("color-mode: v-gamut is not allowed", RecipeFormats.parse(base + "color-mode: v-gamut\n", "x.YML").error);
        assertEquals("black-level: +16 is not allowed", RecipeFormats.parse(base + "black-level: +16\n", "x.YML").error);
        assertEquals("black-gamma-range: huge is not allowed", RecipeFormats.parse(base + "black-gamma-range: huge\n", "x.YML").error);
        assertEquals("knee: 110 is not allowed", RecipeFormats.parse(base + "knee: 110\n", "x.YML").error);
        assertEquals("knee: 74 is not allowed", RecipeFormats.parse(base + "knee: 74\n", "x.YML").error, "off is spelled off");
        assertEquals("color-saturation: 40 is not allowed", RecipeFormats.parse(base + "color-saturation: 40\n", "x.YML").error);
        assertEquals("s-curve: 1.5 is not allowed", RecipeFormats.parse(base + "s-curve: 1.5\n", "x.YML").error);
        assertEquals("family: missing", RecipeFormats.parse("format: 3\nname: A\ngamma: s-log3\n", "x.YML").error);
    }
}
