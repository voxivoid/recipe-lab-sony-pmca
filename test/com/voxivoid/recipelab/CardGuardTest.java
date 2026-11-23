package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the app does around creating a file on the card (issue #130). Whether the KEEP folder stops a NEX-5R restarting,
 * and whether the marks survive that restart, needs the camera.
 */
class CardGuardTest {
    @TempDir File root;
    @TempDir File camera;
    private File app;

    @BeforeEach void open() { app = new File(root, CardLog.DIR); CardGuard.open(root); }
    @AfterEach void close() { CardGuard.open(null); }

    private File mark(String name) { return new File(app, name); }

    /** the camera went down during a create: what is left on the card is the TRYING folder */
    private void crashDuringCreate() { app.mkdirs(); mark(CardGuard.TRYING).mkdir(); CardGuard.open(root); }

    @Test void marksAreNamesTheCardTakes() {
        for (String n : new String[] { CardGuard.TRYING, CardGuard.STRIKE, CardGuard.BLOCKED, CardGuard.KEEP }) assertTrue(n.matches("[A-Z0-9]{1,8}"), n);
    }

    @Test void firstFileInAnEmptyFolderGetsAKeepFolderFirst() throws IOException {
        File dir = new File(root, "RECIPES"); dir.mkdirs();
        File f = new File(dir, "A.YML");
        CardGuard.ensure(f);
        assertTrue(f.isFile());
        assertTrue(new File(dir, CardGuard.KEEP).isDirectory());
        assertFalse(mark(CardGuard.TRYING).exists(), "a finished create leaves no mark");
    }

    @Test void aFolderWithSomethingInItGetsNoKeepFolder() throws IOException {
        File dir = new File(root, "RECIPES"); dir.mkdirs();
        Files.write(new File(dir, "OLD.YML").toPath(), new byte[1]);
        CardGuard.ensure(new File(dir, "A.YML"));
        assertFalse(new File(dir, CardGuard.KEEP).exists());
    }

    @Test void aFileAlreadyThereIsLeftAlone() throws IOException {
        File f = new File(root, "A.TXT");
        Files.write(f.toPath(), "kept".getBytes(StandardCharsets.UTF_8));
        CardGuard.ensure(f);
        assertEquals("kept", new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        assertFalse(app.exists(), "no marks for a file that needs no create");
    }

    @Test void oneUnfinishedCreateIsAStrikeNotABlock() {
        crashDuringCreate();
        assertTrue(mark(CardGuard.STRIKE).isDirectory());
        assertFalse(mark(CardGuard.TRYING).exists());
        assertFalse(CardGuard.blocked());
    }

    @Test void aCreateThatFinishesClearsTheStrike() throws IOException {
        crashDuringCreate();
        CardGuard.ensure(new File(root, "A.TXT"));
        assertFalse(mark(CardGuard.STRIKE).exists());
        crashDuringCreate();
        assertFalse(CardGuard.blocked(), "two crashes with a good create between them are not in a row");
    }

    @Test void twoInARowBlockTheCardAndItStaysBlocked() {
        crashDuringCreate();
        crashDuringCreate();
        assertTrue(CardGuard.blocked());
        assertFalse(mark(CardGuard.STRIKE).exists());
        CardGuard.open(root);
        assertTrue(CardGuard.blocked(), "the mark is on the card");
    }

    @Test void aBlockedCardGetsNoNewFileButExistingOnesStayWritable() throws IOException {
        crashDuringCreate();
        crashDuringCreate();
        File there = new File(app, "LOG.TXT");
        Files.write(there.toPath(), new byte[1]);
        CardGuard.ensure(there);
        File fresh = new File(app, "NEW.TXT");
        IOException e = assertThrows(IOException.class, () -> CardGuard.ensure(fresh));
        assertEquals(Lang.t("card_no_files"), e.getMessage());
        assertFalse(fresh.exists());
        assertFalse(mark(CardGuard.TRYING).exists(), "a refused create touches nothing");
    }

    @Test void deletingTheMarkOnAComputerUndoesTheBlock() {
        crashDuringCreate();
        crashDuringCreate();
        assertTrue(mark(CardGuard.BLOCKED).delete());
        CardGuard.open(root);
        assertFalse(CardGuard.blocked());
    }

    @Test void aFileOutsideTheCardIsNotGuarded() throws IOException {
        crashDuringCreate();
        crashDuringCreate();
        File f = new File(camera, "A.YML");
        CardGuard.ensure(f);
        assertFalse(f.exists(), "its writer creates it");
    }

    @Test void theLogGoesThroughTheGuard() throws IOException {
        File logs = CardLog.dir(root);
        CardLog.append(logs, CardLog.LOG, "line\n");
        assertTrue(new File(logs, CardGuard.KEEP).isDirectory());
        assertEquals("line\n", new String(Files.readAllBytes(new File(logs, CardLog.LOG).toPath()), StandardCharsets.UTF_8));
    }

    @Test void aBlockedCardSavesNoRecipeOnTheCard() {
        crashDuringCreate();
        crashDuringCreate();
        File dir = new File(root, CustomRecipes.DIR);
        assertThrows(IOException.class, () -> CustomRecipes.save(dir, Recipes.ALL[0], "NEX-5R", null));
        assertEquals(0, dir.list().length);
    }

    @Test void onABlockedCardTheCameraKeepsTheRecipes() throws IOException {
        crashDuringCreate();
        crashDuringCreate();
        File card = new File(root, CustomRecipes.DIR), kept = new File(camera, CustomRecipes.DIR);
        card.mkdirs();
        Files.write(new File(card, "A.YML").toPath(), "from card".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(card, "NOTES.TXT").toPath(), new byte[1]);
        CustomRecipes.adopt(card, kept);
        assertEquals("from card", new String(Files.readAllBytes(new File(kept, "A.YML").toPath()), StandardCharsets.UTF_8));
        assertFalse(new File(kept, "NOTES.TXT").exists());
        Files.write(new File(kept, "A.YML").toPath(), "edited".getBytes(StandardCharsets.UTF_8));
        CustomRecipes.adopt(card, kept);
        assertEquals("edited", new String(Files.readAllBytes(new File(kept, "A.YML").toPath()), StandardCharsets.UTF_8), "the camera's copy wins");
        assertNotNull(CustomRecipes.save(kept, Recipes.ALL[0], "NEX-5R", null));
    }

    @Test void theCameraKeepsALimitedNumberOfRecipes() throws IOException {
        crashDuringCreate();
        crashDuringCreate();
        File card = new File(root, CustomRecipes.DIR), kept = new File(camera, CustomRecipes.DIR);
        card.mkdirs();
        for (int i = 0; i < CardGuard.MAX_IN_CAMERA + 5; i++) Files.write(new File(card, "R" + i + ".YML").toPath(), new byte[1]);
        CustomRecipes.adopt(card, kept);
        assertEquals(CardGuard.MAX_IN_CAMERA, kept.list().length);
        IOException e = assertThrows(IOException.class, () -> CustomRecipes.save(kept, Recipes.ALL[0], "NEX-5R", null));
        assertEquals(Lang.t("camera_recipes_full", CardGuard.MAX_IN_CAMERA), e.getMessage());
    }
}
