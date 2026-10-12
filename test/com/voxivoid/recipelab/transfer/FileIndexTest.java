package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileIndexTest {
    private File base, card;

    @BeforeEach void setUp() throws IOException {
        base = Fixtures.tempDir();
        card = new File(base, "card");
        card.mkdirs();
    }

    @AfterEach void tearDown() { Fixtures.deleteTree(base); }

    private void file(String rel, int size, long modified) throws IOException {
        File f = Fixtures.write(new File(card, rel), new byte[size]);
        assertTrue(f.setLastModified(modified));
    }

    private List<String> paths() throws IOException {
        List<String> out = new ArrayList<String>();
        for (FileIndex.Entry e : FileIndex.scan(new StorageRoot(card))) out.add(e.path);
        return out;
    }

    private List<String> recipePaths() throws IOException {
        List<String> out = new ArrayList<String>();
        for (FileIndex.Entry e : FileIndex.recipes(new StorageRoot(card))) out.add(e.path);
        return out;
    }

    @Test void listsArwsAndJpegsCaseInsensitivelyNewestFirst() throws IOException {
        file("DCIM/100MSDCF/DSC00001.ARW", 10, 1_000_000_000_000L);
        file("DCIM/100MSDCF/DSC00002.arw", 20, 1_000_000_003_000L);
        file("DCIM/101MSDCF/DSC00003.Arw", 30, 1_000_000_002_000L);
        file("DCIM/100MSDCF/DSC00002.JPG", 5, 1_000_000_009_000L);
        file("DCIM/100MSDCF/DSC00004.jpeg", 5, 1_000_000_008_000L);
        file("DCIM/100MSDCF/DSC00005.Jpg", 5, 1_000_000_000_500L);
        file("DCIM/100MSDCF/notes.txt", 5, 1_000_000_009_000L);
        file("DCIM/100MSDCF/DSC00006.SRF", 5, 1_000_000_009_000L);
        file("ROOT.ARW", 1, 1_000_000_001_000L);
        List<String> expected = new ArrayList<String>();
        expected.add("DCIM/100MSDCF/DSC00002.JPG");
        expected.add("DCIM/100MSDCF/DSC00004.jpeg");
        expected.add("DCIM/100MSDCF/DSC00002.arw");
        expected.add("DCIM/101MSDCF/DSC00003.Arw");
        expected.add("ROOT.ARW");
        expected.add("DCIM/100MSDCF/DSC00005.Jpg");
        expected.add("DCIM/100MSDCF/DSC00001.ARW");
        assertEquals(expected, paths());
    }

    @Test void eachEntrySaysWhetherItIsAnArwOrAJpeg() throws IOException {
        file("DCIM/100MSDCF/DSC00001.ARW", 1, 1_000_000_000_000L);
        file("DCIM/100MSDCF/DSC00001.JPG", 1, 1_000_000_001_000L);
        file("DCIM/100MSDCF/DSC00002.jpeg", 1, 1_000_000_002_000L);
        List<FileIndex.Entry> e = FileIndex.scan(new StorageRoot(card));
        assertEquals("jpg", e.get(0).type);
        assertEquals("jpg", e.get(1).type);
        assertEquals("arw", e.get(2).type);
    }

    @Test void theRecipesFolderIsNotPhotosInAnyCase() throws IOException {
        file("DCIM/100MSDCF/DSC00001.JPG", 1, 1_000_000_000_000L);
        file("RECIPES/GOLDENHO.YML", 1, 1_000_000_000_000L);
        file("RECIPES/COVER.JPG", 1, 1_000_000_000_000L);
        file("Recipes/old.arw", 1, 1_000_000_000_000L);
        List<String> expected = new ArrayList<String>();
        expected.add("DCIM/100MSDCF/DSC00001.JPG");
        assertEquals(expected, paths());
    }

    @Test void equalTimesSortByPathDescending() throws IOException {
        file("DCIM/100MSDCF/DSC00001.ARW", 1, 1_000_000_000_000L);
        file("DCIM/100MSDCF/DSC00002.ARW", 1, 1_000_000_000_000L);
        assertEquals("DCIM/100MSDCF/DSC00002.ARW", paths().get(0));
    }

    @Test void skipsPrivateAvfInfoAndDotFiles() throws IOException {
        file("DCIM/100MSDCF/DSC00001.ARW", 1, 1_000_000_000_000L);
        file("PRIVATE/SONY/X.ARW", 1, 1_000_000_000_000L);
        file("private/y.arw", 1, 1_000_000_000_000L);
        file("AVF_INFO/Z.ARW", 1, 1_000_000_000_000L);
        file("DCIM/100MSDCF/._DSC00001.ARW", 1, 1_000_000_000_000L);    // macOS AppleDouble
        file(".Trashes/DSC00009.ARW", 1, 1_000_000_000_000L);
        List<String> expected = new ArrayList<String>();
        expected.add("DCIM/100MSDCF/DSC00001.ARW");
        assertEquals(expected, paths());
    }

    @Test void skipsSymlinksLeavingTheRoot() throws IOException {
        Fixtures.write(new File(base, "outside/E.ARW"), new byte[1]);
        Files.createSymbolicLink(new File(card, "link").toPath(), new File(base, "outside").toPath());
        Files.createSymbolicLink(new File(card, "F.ARW").toPath(), new File(base, "outside/E.ARW").toPath());
        assertEquals(new ArrayList<String>(), paths());
    }

    @Test void emptyCardIsAnEmptyList() throws IOException {
        assertEquals("{\"files\":[]}", FileIndex.toJson(FileIndex.scan(new StorageRoot(card))));
    }

    @Test void jsonShape() throws IOException {
        file("DCIM/100MSDCF/DSC01542.ARW", 1234, 1507981649000L);
        file("DCIM/100MSDCF/DSC01543.ARW", 99, 1507981650000L);
        file("DCIM/100MSDCF/DSC01543.JPG", 42, 1507981650000L);
        assertEquals("{\"files\":["
                + "{\"path\":\"DCIM/100MSDCF/DSC01543.JPG\",\"size\":42,\"modified\":1507981650000,\"type\":\"jpg\"},"
                + "{\"path\":\"DCIM/100MSDCF/DSC01543.ARW\",\"size\":99,\"modified\":1507981650000,\"type\":\"arw\"},"
                + "{\"path\":\"DCIM/100MSDCF/DSC01542.ARW\",\"size\":1234,\"modified\":1507981649000,\"type\":\"arw\"}]}",
            FileIndex.toJson(FileIndex.scan(new StorageRoot(card))));
    }

    @Test void isArw() {
        assertTrue(FileIndex.isArw("DSC00001.ARW"));
        assertTrue(FileIndex.isArw("dsc00001.arw"));
        assertFalse(FileIndex.isArw(".ARW"));
        assertFalse(FileIndex.isArw("DSC00001.ARW.JPG"));
        assertFalse(FileIndex.isArw("DSC00001.SRF"));
        assertFalse(FileIndex.isArw("._DSC00001.ARW"));
    }

    @Test void isJpegAndType() {
        assertTrue(FileIndex.isJpeg("DSC00001.JPG"));
        assertTrue(FileIndex.isJpeg("dsc00001.jpeg"));
        assertTrue(FileIndex.isJpeg("DSC00001.Jpeg"));
        assertFalse(FileIndex.isJpeg(".JPG"));
        assertFalse(FileIndex.isJpeg("._DSC00001.JPG"));
        assertFalse(FileIndex.isJpeg("DSC00001.JPG.ARW"));
        assertEquals("arw", FileIndex.type("DSC00001.ARW"));
        assertEquals("jpg", FileIndex.type("DSC00001.jpeg"));
        assertNull(FileIndex.type("GOLDENHO.YML"));
        assertNull(FileIndex.type("C0001.MP4"));
    }

    // ---- the custom recipe files

    @Test void recipesAreTheYmlFilesInRecipesByPath() throws IOException {
        file("RECIPES/VELVIA2.YML", 120, 1_000_000_000_000L);
        file("RECIPES/GOLDENHO.YML", 340, 1_000_000_005_000L);
        file("RECIPES/notes.yml", 7, 1_000_000_001_000L);
        file("RECIPES/SAVING.TMP", 7, 1_000_000_001_000L);
        file("RECIPES/README.TXT", 7, 1_000_000_001_000L);
        file("RECIPES/._GOLDENHO.YML", 7, 1_000_000_001_000L);
        file("RECIPES/SUB/DEEP.YML", 7, 1_000_000_001_000L);
        file("DCIM/LOOSE.YML", 7, 1_000_000_001_000L);
        List<String> expected = new ArrayList<String>();
        expected.add("RECIPES/GOLDENHO.YML");
        expected.add("RECIPES/VELVIA2.YML");
        expected.add("RECIPES/notes.yml");
        assertEquals(expected, recipePaths());
        FileIndex.Entry golden = FileIndex.recipes(new StorageRoot(card)).get(0);
        assertEquals(340, golden.size);
        assertEquals(1_000_000_005_000L, golden.modified);
        assertNull(golden.type);
    }

    @Test void aLowerCaseRecipesFolderIsFoundAndItsNameKept() throws IOException {
        file("recipes/goldenho.yml", 3, 1_000_000_000_000L);
        List<String> expected = new ArrayList<String>();
        expected.add("recipes/goldenho.yml");
        assertEquals(expected, recipePaths(), "the path is the one /files/ needs, as the card spells it");
    }

    @Test void noRecipesFolderIsAnEmptyList() throws IOException {
        file("DCIM/100MSDCF/DSC00001.ARW", 1, 1_000_000_000_000L);
        assertEquals("{\"files\":[]}", FileIndex.toJson(FileIndex.recipes(new StorageRoot(card))));
        Fixtures.write(new File(card, "RECIPES"), new byte[1]);                   // a file where the folder would be
        assertEquals(new ArrayList<String>(), recipePaths());
    }

    @Test void recipeJsonHasNoType() throws IOException {
        file("RECIPES/GOLDENHO.YML", 210, 1507981649000L);
        assertEquals("{\"files\":[{\"path\":\"RECIPES/GOLDENHO.YML\",\"size\":210,\"modified\":1507981649000}]}",
            FileIndex.toJson(FileIndex.recipes(new StorageRoot(card))));
    }

    @Test void recipesThatLinkOutOfTheCardAreLeftOut() throws IOException {
        Fixtures.write(new File(base, "outside/STOLEN.YML"), new byte[1]);
        Files.createSymbolicLink(new File(card, "RECIPES").toPath(), new File(base, "outside").toPath());
        assertEquals(new ArrayList<String>(), recipePaths(), "the whole folder leads out");
        Fixtures.deleteTree(new File(card, "RECIPES"));
        file("RECIPES/MINE.YML", 1, 1_000_000_000_000L);
        Files.createSymbolicLink(new File(card, "RECIPES/LEAK.YML").toPath(), new File(base, "outside/STOLEN.YML").toPath());
        List<String> expected = new ArrayList<String>();
        expected.add("RECIPES/MINE.YML");
        assertEquals(expected, recipePaths(), "one file leads out");
    }
}
