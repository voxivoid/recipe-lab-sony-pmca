package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The custom recipe file's versions: each file read by the reader of the version it names, the newest one written,
 * every writer readable by its own reader — so a version added later leaves every file written before it loading.
 */
class RecipeFormatsTest {
    /** a version this build does not have yet: reads only a name, under a key format 1 does not know */
    private static final RecipeFormat FAKE_V2 = new RecipeFormat() {
        public int version() { return 2; }
        public CustomRecipes.Parsed read(Map<String, String> keys, String fileName) {
            return CustomRecipes.Parsed.ok(CustomRecipes.renamed(Recipes.ALL[Recipes.FACTORY], keys.get("title")));
        }
        public String write(Recipes.Recipe r, String madeOn) { return "format: 2\ntitle: " + r.name + "\n"; }
    };
    private static final RecipeFormat[] WITH_V2 = { new RecipeFormatV1(), FAKE_V2 };

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
        CustomRecipes.Parsed v2 = RecipeFormats.parse("format: 2\ntitle: From The Future\n", "x.YML", WITH_V2);
        assertEquals("From The Future", v2.recipe.name, "read by the version 2 reader");
        CustomRecipes.Parsed v1 = RecipeFormats.parse(new RecipeFormatV1().write(CustomRecipes.renamed(Recipes.ALL[3], "Old File"), null), "x.YML", WITH_V2);
        assertNull(v1.error);
        assertEquals("Old File", v1.recipe.name, "a version 1 file still reads with the version 1 reader once version 2 exists");
        assertEquals(Recipes.ALL[3].style, v1.recipe.style);
    }

    @Test void aFileWithoutAFormatLineIsVersionOne() {
        assertEquals("Plain", RecipeFormats.parse("name: Plain\n", "x.YML", WITH_V2).recipe.name);
    }

    @Test void aVersionThisBuildDoesNotKnowIsSkippedWhole() {
        assertEquals("made by a newer Recipe Lab (format 2)", RecipeFormats.parse("format: 2\ntitle: x\n", "x.YML").error);
        assertEquals("made by a newer Recipe Lab (format 3)", RecipeFormats.parse("format: 3\n", "x.YML", WITH_V2).error);
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
}
