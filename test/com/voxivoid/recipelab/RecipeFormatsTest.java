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
        public String write(Recipes.Recipe r, String madeOn) { return "format = 2\ntitle = " + r.name + "\n"; }
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
            assertTrue(text.contains("format = " + f.version()), "a file names its version, v" + f.version());
            CustomRecipes.Parsed p = RecipeFormats.parse(text, "x.txt");
            assertNull(p.error, "v" + f.version());
            assertEquals(r.name, p.recipe.name);
            assertEquals(r.style, p.recipe.style);
        }
    }

    @Test void eachFileIsReadByTheVersionItNames() {
        CustomRecipes.Parsed v2 = RecipeFormats.parse("format = 2\ntitle = From The Future\n", "x.txt", WITH_V2);
        assertEquals("From The Future", v2.recipe.name, "read by the version 2 reader");
        CustomRecipes.Parsed v1 = RecipeFormats.parse(new RecipeFormatV1().write(CustomRecipes.renamed(Recipes.ALL[3], "Old File"), null), "x.txt", WITH_V2);
        assertNull(v1.error);
        assertEquals("Old File", v1.recipe.name, "a version 1 file still reads with the version 1 reader once version 2 exists");
        assertEquals(Recipes.ALL[3].style, v1.recipe.style);
    }

    @Test void aFileWithoutAFormatLineIsVersionOne() {
        assertEquals("Plain", RecipeFormats.parse("name = Plain\n", "x.txt", WITH_V2).recipe.name);
    }

    @Test void aVersionThisBuildDoesNotKnowIsSkippedWhole() {
        assertEquals("made by a newer Recipe Lab (format 2)", RecipeFormats.parse("format = 2\ntitle = x\n", "x.txt").error);
        assertEquals("made by a newer Recipe Lab (format 3)", RecipeFormats.parse("format = 3\n", "x.txt", WITH_V2).error);
        assertEquals("format = 0 is not allowed", RecipeFormats.parse("format = 0\n", "x.txt").error);
        assertEquals("format = two is not allowed", RecipeFormats.parse("format = two\n", "x.txt").error);
    }

    @Test void theEnvelopeIsSharedByEveryVersion() {
        java.util.Map<String, String> kv = new java.util.HashMap<String, String>();
        assertNull(RecipeFormats.keys("﻿# comment\r\nName = A  # trailing\nnot a setting\n", kv));
        assertEquals("A", kv.get("name"), "keys lower-cased, comments and the byte-order mark dropped");
        assertEquals(1, kv.size(), "a line without = is skipped");
        assertEquals("saturation is set twice", RecipeFormats.keys("saturation = 1\nSATURATION = 2", new java.util.HashMap<String, String>()));
    }
}
