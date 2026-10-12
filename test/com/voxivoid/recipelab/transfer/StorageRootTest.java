package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageRootTest {
    private File base, card;
    private StorageRoot root;

    @BeforeEach void setUp() throws IOException {
        base = Fixtures.tempDir();
        card = new File(base, "card");
        Fixtures.write(new File(card, "DCIM/100MSDCF/DSC00001.ARW"), new byte[] { 1 });
        Fixtures.write(new File(base, "secret.txt"), new byte[] { 2 });
        root = new StorageRoot(card);
    }

    @AfterEach void tearDown() { Fixtures.deleteTree(base); }

    private int rejected(String rel) {
        return assertThrows(HttpException.class, () -> root.resolve(rel)).status;
    }

    @Test void resolvesNormalRelativePaths() throws Exception {
        assertEquals(new File(card, "DCIM/100MSDCF/DSC00001.ARW").getCanonicalFile(), root.resolve("DCIM/100MSDCF/DSC00001.ARW"));
        assertEquals(new File(card, "DCIM/nope.ARW").getCanonicalFile(), root.resolve("DCIM/nope.ARW"));   // need not exist
    }

    @Test void rejectsTraversalAbsoluteAndOddPaths() {
        assertEquals(400, rejected(".."));
        assertEquals(400, rejected("../secret.txt"));
        assertEquals(400, rejected("DCIM/../../secret.txt"));
        assertEquals(400, rejected("DCIM/../DCIM/100MSDCF/DSC00001.ARW"));   // even when it would stay inside
        assertEquals(400, rejected("./DCIM"));
        assertEquals(400, rejected("/etc/passwd"));
        assertEquals(400, rejected(base.getAbsolutePath() + "/secret.txt"));
        assertEquals(400, rejected("DCIM\\..\\..\\secret.txt"));
        assertEquals(400, rejected("DCIM//100MSDCF"));
        assertEquals(400, rejected("DCIM/"));
        assertEquals(400, rejected(""));
        assertEquals(400, rejected(null));
        assertEquals(400, rejected("a\0b"));
    }

    @Test void rejectsSymlinksThatEscapeTheRoot() throws Exception {
        Files.createSymbolicLink(new File(card, "out").toPath(), base.toPath());
        Files.createSymbolicLink(new File(card, "leak.ARW").toPath(), new File(base, "secret.txt").toPath());
        assertEquals(400, rejected("out/secret.txt"));
        assertEquals(400, rejected("leak.ARW"));
    }

    @Test void allowsSymlinksThatStayInside() throws Exception {
        Files.createSymbolicLink(new File(card, "alias").toPath(), new File(card, "DCIM").toPath());
        assertEquals(new File(card, "DCIM/100MSDCF/DSC00001.ARW").getCanonicalFile(), root.resolve("alias/100MSDCF/DSC00001.ARW"));
    }
}
