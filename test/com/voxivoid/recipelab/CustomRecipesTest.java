package com.voxivoid.recipelab;

import static com.voxivoid.recipelab.Fixtures.*;
import static com.voxivoid.recipelab.Params.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Custom recipes (issues #14, #15): the file format and its versions, what a file may hold, names, and the folder on
 * the card. What the camera does with a recipe read from a card is not here — that needs a camera and a power cycle.
 */
class CustomRecipesTest {
    @TempDir File dir;

    private static String x25() { return "xxxxxxxxxxxxxxxxxxxxxxxxx"; }

    private static Recipes.Recipe mine(String name, String builtIn) { return CustomRecipes.renamed(recipe(builtIn), name); }

    private static Recipes.Recipe parse(String text) {
        CustomRecipes.Parsed p = RecipeFormats.parse(text, "file.YML");
        assertNull(p.error, text);
        return p.recipe;
    }

    private static String error(String text) {
        CustomRecipes.Parsed p = RecipeFormats.parse(text, "file.YML");
        assertNull(p.recipe, text);
        return p.error;
    }

    private static void assertSameLook(Recipes.Recipe a, Recipes.Recipe b) {
        assertEquals(Arrays.asList(a.style, a.sat, a.con, a.sharp, a.wbMode, a.kelvin, a.ab, a.gm, a.pe, a.ev, a.dro, a.sub),
                Arrays.asList(b.style, b.sat, b.con, b.sharp, b.wbMode, b.kelvin, b.ab, b.gm, b.pe, b.ev, b.dro, b.sub), b.name);
    }

    // ---- the file
    @Test void everyBuiltInLookSurvivesAFileRoundTrip() {
        for (Recipes.Recipe r : Recipes.ALL) {
            Recipes.Recipe c = CustomRecipes.renamed(r, "Copy");
            Recipes.Recipe back = parse(RecipeFormats.write(c, "ILCE-6000"));
            assertEquals("Copy", back.name);
            assertTrue(back.isCustom());
            assertSameLook(c, back);
        }
    }

    @Test void theFileReadsLikeTheChips() {
        String text = RecipeFormats.write(mine("Golden Hour", "Kodak Vision 200T (Asteroid City)"), "ILCE-6000");
        assertTrue(text.startsWith("# Recipe Lab custom recipe"), text);
        for (String line : new String[] { "format: 1", "name: \"Golden Hour\"", "made-on: \"ILCE-6000\"", "style: neutral",
                "saturation: -2", "contrast: -3", "effect: off", "white-balance: 5000K", "amber-blue: +2",
                "green-magenta: +3", "exposure: +0.3", "dro: 3" })
            assertTrue(text.contains("\n" + line), line + " in\n" + text);
        assertFalse(text.contains("effect-option"), "only an effect with options writes one");
    }

    @Test void anEffectWritesItsOptionByName() {
        Recipes.Recipe r = mine("Pink", "Sony SH (soft high-key)");
        String text = RecipeFormats.write(r, null);
        assertTrue(text.contains("\neffect: soft-high-key"), text);
        assertTrue(text.contains("\neffect-option: blue"), text);
        assertFalse(text.contains("made-on"), "an unknown camera is left out, not written as null");
    }

    @Test void aMinimalHandWrittenFileTakesTheFactoryLookForWhatItLeavesOut() {
        Recipes.Recipe r = parse("name: Plain\n");
        assertSameLook(CustomRecipes.renamed(Recipes.ALL[Recipes.FACTORY], "Plain"), r);
        assertEquals("Mine", RecipeFormats.parse("style: vivid", "Mine.YML").recipe.name, "no name line: the file's name");
    }

    @Test void handEditingIsForgiving() {
        Recipes.Recipe r = parse("﻿# a comment\r\nFORMAT: 1\r\nname:  Spaced Out  \r\nStyle: VIVID   # trailing comment\r\n"
                + "white-balance: 5600 k\r\nexposure: -1\r\ndro: OFF\r\nsomething-new: 7\r\n");
        assertEquals("Spaced Out", r.name);
        assertEquals(Recipes.VIVID, r.style);
        assertEquals(WB_KELVIN, r.wbMode);
        assertEquals(5600, r.kelvin);
        assertEquals(-3, r.ev);
        assertEquals(0, r.dro);
    }

    @Test void whiteBalanceKeepLeavesTheCamerasAlone() {
        Recipes.Recipe r = parse("name: Keep\nwhite-balance: keep\n");
        assertEquals(0, r.wbMode);
        assertTrue(RecipeFormats.write(r, null).contains("\nwhite-balance: keep"));
    }

    // ---- versions
    @Test void aFileWithoutAFormatIsFormatOne() { assertEquals("Old", parse("name: Old\nstyle: mono").name); }

    @Test void aNewerFormatIsSkippedNotHalfRead() {
        assertEquals("made by a newer Recipe Lab (format 2)", error("format: 2\nname: Future\nstyle: mono"));
    }

    @Test void aFormatThatIsNotANumberIsRefused() {
        assertEquals("format: one is not allowed", error("format: one\nname: x"));
        assertEquals("format: 0 is not allowed", error("format: 0\nname: x"));
    }

    @Test void theWrittenFormatIsTheNewestReadOne() { assertTrue(RecipeFormats.write(mine("x", "Velvia"), null).contains("\nformat: " + RecipeFormats.current().version() + "\n")); }

    // ---- untrusted input
    @Test void aValueTheStoreWouldNotTakeSkipsTheFile() {
        assertEquals("saturation: 4 is not allowed", error("name: x\nsaturation: 4"));
        assertEquals("amber-blue: -8 is not allowed", error("name: x\namber-blue: -8"));
        assertEquals("style: chartreuse is not allowed", error("name: x\nstyle: chartreuse"));
        assertEquals("effect: sparkles is not allowed", error("name: x\neffect: sparkles"));
        assertEquals("effect-option: purple is not allowed", error("name: x\neffect: soft-high-key\neffect-option: purple"));
        assertEquals("white-balance: 12000K is not allowed", error("name: x\nwhite-balance: 12000K"));
        assertEquals("white-balance: 5650K is not allowed", error("name: x\nwhite-balance: 5650K"), "the store keeps hundreds");
        assertEquals("exposure: +0.5 is not allowed", error("name: x\nexposure: +0.5"), "thirds only");
        assertEquals("exposure: +5.3 is not allowed", error("name: x\nexposure: +5.3"));
        assertEquals("dro: 6 is not allowed", error("name: x\ndro: 6"));
        assertEquals("contrast: 1.5 is not allowed", error("name: x\ncontrast: 1.5"));
    }

    @Test void aKeySetTwiceIsAmbiguous() { assertEquals("saturation is set twice", error("name: x\nsaturation: 1\nsaturation: 2")); }

    @Test void aNameMustBeOneTheEditorCouldType() {
        assertEquals("Only letters, digits, spaces and - . ' ( ) & +", error("name: a|b"));
        assertEquals("Only letters, digits, spaces and - . ' ( ) & +", error("name: 晴天"), "no font the app has can draw it on every screen");
        assertEquals("At most 24 characters", error("name: " + x25()));
    }

    @Test void problemNamesTheFirstValueOutOfRange() {
        int[] rows = factoryRows();
        assertNull(CustomRecipes.problem(CustomRecipes.recipe("ok", rows)));
        rows[R_STYLE] = 13;                                       // an enum value the camera uses for a style we have not identified
        assertEquals("style: ?13 is not allowed", CustomRecipes.problem(CustomRecipes.recipe("x", rows)));
        rows = factoryRows(); rows[R_EV] = 16;
        assertEquals("exposure: 16 is not allowed", CustomRecipes.problem(CustomRecipes.recipe("x", rows)));
    }

    @Test void exposureIsReadInThirds() {
        assertEquals(Integer.valueOf(2), CustomRecipes.thirds("+0.7"));
        assertEquals(Integer.valueOf(-4), CustomRecipes.thirds("-1.3"));
        assertEquals(Integer.valueOf(3), CustomRecipes.thirds("1"));
        assertEquals(Integer.valueOf(0), CustomRecipes.thirds("0"));
        assertNull(CustomRecipes.thirds("0.5"));
        assertNull(CustomRecipes.thirds("+"));
        for (int ev = -15; ev <= 15; ev++) assertEquals(Integer.valueOf(ev), CustomRecipes.thirds(Recipes.evLabel(ev)), "as the chip shows " + ev);
    }

    // ---- recipes from rows
    @Test void theStagedRowsBecomeTheRecipe() {
        int[] rows = staged(recipe("Cinestill 800T"), factoryRows(), Q_FINE);
        Recipes.Recipe r = CustomRecipes.recipe("Night", rows);
        assertTrue(r.isCustom());
        assertSameLook(CustomRecipes.renamed(recipe("Cinestill 800T"), "Night"), r);
        assertFalse(Params.differsFromRecipe(r, rows), "staging it again changes nothing");
    }

    @Test void aWhiteBalancePresetIsKeptAsKeep() {
        int[] rows = factoryRows(); rows[R_WBMODE] = 2;           // daylight, set in the camera's own menu
        Recipes.Recipe r = CustomRecipes.recipe("Day", rows);
        assertEquals(0, r.wbMode, "the file has no word for the preset, so the camera's is left alone");
        assertNull(CustomRecipes.problem(r));
    }

    @Test void theSubSettingOnlyCountsForAnEffectWithOne() {
        int[] rows = factoryRows(); rows[R_SUB] = 3;
        assertEquals(0, CustomRecipes.recipe("x", rows).sub);
        rows[R_PE] = Recipes.PE_TOY;
        assertEquals(3, CustomRecipes.recipe("x", rows).sub);
    }

    // ---- an edit worth keeping
    @Test void anEditDiffersFromItsRecipeAndQualityIsNoEdit() {
        Recipes.Recipe velvia = recipe("Velvia");
        int[] e = staged(velvia, factoryRows(), Q_FINE);
        assertFalse(Params.differsFromRecipe(velvia, e));
        e[R_QUAL] = Q_RAW;
        assertFalse(Params.differsFromRecipe(velvia, e), "quality follows the Factory base, not the recipe");
        e[R_SAT] = 2;
        assertTrue(Params.differsFromRecipe(velvia, e));
    }

    @Test void hiddenRowsAreNoEdit() {
        Recipes.Recipe velvia = recipe("Velvia");                // auto white balance, no effect
        int[] e = staged(velvia, factoryRows(), Q_FINE);
        e[R_KELVIN] = 32;
        assertFalse(Params.differsFromRecipe(velvia, e), "the kelvin chip is hidden in auto");
        e[R_SUB] = 2;
        assertFalse(Params.differsFromRecipe(velvia, e), "and so is the sub-setting without an effect");
        e[R_WBMODE] = WB_KELVIN;
        assertTrue(Params.differsFromRecipe(velvia, e));
    }

    // ---- names
    @Test void newRecipesAreUntitledThenNumbered() {
        assertEquals("Untitled", CustomRecipes.defaultName(Collections.<String>emptyList()));
        assertEquals("Untitled 2", CustomRecipes.defaultName(Arrays.asList("untitled")));
        assertEquals("Untitled 3", CustomRecipes.defaultName(Arrays.asList("Untitled", "Untitled 2", "Untitled 4")));
    }

    @Test void aNameIsRefusedWithTheReason() {
        List<String> taken = Arrays.asList("Golden Hour");
        assertNull(CustomRecipes.nameProblem("Blue Hour", taken, null));
        assertEquals("Type a name first", CustomRecipes.nameProblem("   ", taken, null));
        assertEquals("Golden Hour is already a custom recipe", CustomRecipes.nameProblem("golden hour", taken, null), "names are unique ignoring case");
        assertNull(CustomRecipes.nameProblem("GOLDEN HOUR", taken, "Golden Hour"), "a rename may change only the case");
        assertEquals("At most 24 characters", CustomRecipes.nameProblem(x25(), taken, null));
        assertEquals("Only letters, digits, spaces and - . ' ( ) & +", CustomRecipes.nameProblem("a/b", taken, null));
    }

    @Test void everyCharacterTheKeyboardTypesIsAllowedInAName() {
        for (String row : NameEntry.GRID)
            for (char c : row.toCharArray())
                if (c != NameEntry.SHIFT && c != NameEntry.BACK && c != NameEntry.OK) {
                    assertTrue(CustomRecipes.nameChar(c), "'" + c + "'");
                    assertTrue(CustomRecipes.nameChar(Character.toLowerCase(c)), "'" + c + "' in lower case");
                }
    }

    @Test void theFileIsNamedAfterTheRecipe() {
        // the card's file system takes 8.3 names only (ENAMETOOLONG past them), in capitals
        assertEquals("GOLDENHO.YML", CustomRecipes.fileName("Golden Hour", Collections.<String>emptyList()));
        assertEquals("GOLDENH2.YML", CustomRecipes.fileName("Golden Hour", Arrays.asList("goldenho.YML")), "case is ignored, as the card does");
        assertEquals("GOLDEN10.YML", CustomRecipes.fileName("Golden Hour", Arrays.asList("GOLDENHO.YML", "GOLDENH2.YML", "GOLDENH3.YML",
                "GOLDENH4.YML", "GOLDENH5.YML", "GOLDENH6.YML", "GOLDENH7.YML", "GOLDENH8.YML", "GOLDENH9.YML")));
        assertEquals("MRT.YML", CustomRecipes.fileName("Mr. T.", Collections.<String>emptyList()), "only letters and digits");
        assertEquals("RECIPE.YML", CustomRecipes.fileName("...", Collections.<String>emptyList()));
        assertEquals("AB2.YML", CustomRecipes.fileName("a b", Arrays.asList("AB.YML")));
    }

    @Test void everyNameTheAppWritesIsAnEightThreeName() {
        assertTrue(CustomRecipes.DIR.length() <= 8 && CustomRecipes.DIR.equals(CustomRecipes.DIR.toUpperCase()), CustomRecipes.DIR);
        assertTrue(CustomRecipes.TMP.matches("[A-Z0-9]{1,8}\\.[A-Z0-9]{1,3}"), CustomRecipes.TMP);
        for (String n : new String[] { "Golden Hour", "x", "Untitled 12", "(Kodak) & Fuji + 2'" })
            assertTrue(CustomRecipes.fileName(n, Collections.<String>emptyList()).matches("[A-Z0-9]{1,8}\\.YML"), n);
    }

    // ---- the folder
    private File write(String name, String text) throws IOException {
        File f = new File(dir, name);
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test void aMissingFolderHoldsNothing() {
        CustomRecipes.Loaded l = CustomRecipes.load(new File(dir, CustomRecipes.DIR));
        assertTrue(l.entries.isEmpty());
        assertTrue(l.skipped.isEmpty());
        assertTrue(CustomRecipes.load(null).entries.isEmpty(), "no card at all");
    }

    @Test void saveThenLoad() throws IOException {
        File folder = new File(dir, CustomRecipes.DIR);
        CustomRecipes.Entry e = CustomRecipes.save(folder, mine("Zed", "Velvia"), "ILCE-6000", null);
        assertEquals("ZED.YML", e.file);
        CustomRecipes.save(folder, mine("Alpha", "Acros"), null, null);
        CustomRecipes.Loaded l = CustomRecipes.load(folder);
        assertEquals(Arrays.asList("Alpha", "Zed"), Arrays.asList(l.entries.get(0).recipe.name, l.entries.get(1).recipe.name), "A to Z");
        assertSameLook(mine("Zed", "Velvia"), l.entries.get(1).recipe);
        assertEquals(Arrays.asList("ALPHA.YML", "ZED.YML"), sorted(folder.list()), "no temporary file left behind");
    }

    private static List<String> sorted(String[] names) { List<String> l = new ArrayList<String>(Arrays.asList(names)); Collections.sort(l); return l; }

    @Test void anEditReplacesItsFile() throws IOException {
        CustomRecipes.Entry e = CustomRecipes.save(dir, mine("Mine", "Velvia"), null, null);
        int[] rows = staged(e.recipe, factoryRows(), Q_FINE); rows[R_SAT] = -1;
        CustomRecipes.Entry again = CustomRecipes.save(dir, CustomRecipes.recipe("Mine", rows), null, e.file);
        assertEquals("MINE.YML", again.file, "the same file, not MINE2");
        assertEquals(Arrays.asList("MINE.YML"), sorted(dir.list()));
        assertEquals(-1, CustomRecipes.load(dir).entries.get(0).recipe.sat);
    }

    @Test void aRenameMovesTheFile() throws IOException {
        CustomRecipes.Entry e = CustomRecipes.save(dir, mine("Old", "Velvia"), null, null);
        CustomRecipes.Entry r = CustomRecipes.save(dir, CustomRecipes.renamed(e.recipe, "New"), null, e.file);
        assertEquals(Arrays.asList("NEW.YML"), sorted(dir.list()));
        assertEquals("New", CustomRecipes.load(dir).entries.get(0).recipe.name);
        CustomRecipes.save(dir, CustomRecipes.renamed(r.recipe, "NEW"), null, r.file);
        assertEquals(Arrays.asList("NEW.YML"), sorted(dir.list()), "a change of case only keeps the file");
    }

    @Test void aSaveCutShortLeavesNothingInTheWayOfTheNext() throws IOException {
        write(CustomRecipes.TMP, "name: Half");                  // the camera went off between writing and renaming
        CustomRecipes.save(dir, mine("Next", "Velvia"), null, null);
        assertEquals(Arrays.asList("NEXT.YML"), sorted(dir.list()));
    }

    @Test void deleteRemovesTheFile() throws IOException {
        CustomRecipes.Entry e = CustomRecipes.save(dir, mine("Gone", "Velvia"), null, null);
        CustomRecipes.delete(dir, e.file);
        assertEquals(0, dir.list().length);
    }

    @Test void aFileFromElsewhereNeverOverwritesOne() throws IOException {
        CustomRecipes.save(dir, mine("Shared", "Velvia"), null, null);
        write("from a friend.YML", "name: shared\nstyle: mono\n");
        CustomRecipes.Loaded l = CustomRecipes.load(dir);
        assertEquals(1, l.entries.size());
        assertEquals(Arrays.asList("SHARED.YML: Shared is already the name in from a friend.YML"), l.skipped,
                "the first file by name keeps it; the other is skipped and said so");
        CustomRecipes.Entry e = CustomRecipes.save(dir, mine("Shared", "Acros"), null, null);
        assertEquals("SHARED2.YML", e.file, "a new file never takes an existing one's name");
    }

    @Test void badFilesAreSkippedWithTheReasonAndTheRestLoad() throws IOException {
        write("good.YML", "name: Good\n");
        write("bad.YML", "name: Bad\nsaturation: 9\n");
        write("future.YML", "format: 7\nname: Future\n");
        write("notes.md", "not a recipe");
        write(".hidden.YML.tmp", "name: Half\n");
        write(CustomRecipes.TMP, "name: Half\n");
        Files.write(new File(dir, "huge.YML").toPath(), new byte[CustomRecipes.MAX_BYTES + 1]);
        CustomRecipes.Loaded l = CustomRecipes.load(dir);
        assertEquals(1, l.entries.size());
        assertEquals("Good", l.entries.get(0).recipe.name);
        assertEquals(Arrays.asList("bad.YML: saturation: 9 is not allowed", "future.YML: made by a newer Recipe Lab (format 7)",
                "huge.YML: too large to be a recipe"), l.skipped);
        assertEquals("Skipped 3 files — first bad.YML: saturation: 9 is not allowed", CustomRecipes.skippedMessage(l.skipped));
        assertEquals("Skipped bad.YML: x", CustomRecipes.skippedMessage(Arrays.asList("bad.YML: x")));
    }

    // ---- the questions
    private static java.util.List<String> labels(int[] actions) {
        java.util.List<String> l = new java.util.ArrayList<String>();
        for (int x : actions) l.add(CustomRecipes.editLabel(x));
        return l;
    }

    @Test void everyEditedRecipeGetsApplySaveAsNewDiscardAndACustomOneSave() {
        assertEquals(Arrays.asList("Apply", "Save as new", "Discard"), labels(CustomRecipes.editActions(false, false)), "a built-in recipe never changes: no Save");
        assertEquals(Arrays.asList("Save", "Apply", "Save as new", "Discard"), labels(CustomRecipes.editActions(true, false)));
    }

    @Test void applyGoesOnceTheCameraHasTheEdits() {
        assertEquals(Arrays.asList("Save as new", "Discard"), labels(CustomRecipes.editActions(false, true)), "nothing left to write");
        assertEquals(Arrays.asList("Save", "Save as new", "Discard"), labels(CustomRecipes.editActions(true, true)));
    }

    @Test void aCopyIsNumberedAndFitsTheNameLimit() {
        assertEquals("Golden Hour 2", CustomRecipes.copyName("Golden Hour", Arrays.asList("Golden Hour")));
        assertEquals("Golden Hour 3", CustomRecipes.copyName("Golden Hour", Arrays.asList("golden hour 2")));
        String long24 = "abcdefghijklmnopqrstuvwx";
        String c = CustomRecipes.copyName(long24, Arrays.asList(long24));
        assertEquals("abcdefghijklmnopqrstuv 2", c);
        assertNull(CustomRecipes.nameProblem(c, Arrays.asList(long24), null), "a copy's name is always one the editor accepts");
        assertEquals("Polaroid Instax 2", CustomRecipes.copyName("Polaroid / Instax", Arrays.<String>asList()), "what a name may not hold goes");
        for (Recipes.Recipe r : Recipes.ALL) {
            String n = CustomRecipes.copyName(r.name, Arrays.<String>asList());
            assertNull(CustomRecipes.nameProblem(n, Arrays.<String>asList(), null), r.name + " → " + n);
        }
    }

    @Test void theQuestionsHighlightTheHarmlessAnswer() {
        assertEquals(Arrays.asList("Rename", "Delete", "Cancel"), Arrays.asList(CustomRecipes.options()), "favourites are hold centre, as on any recipe");
        assertEquals("Cancel", CustomRecipes.options()[CustomRecipes.OPT_DEFAULT], "a stray centre press after the hold does nothing");
        assertEquals("Cancel", CustomRecipes.deleteOptions()[CustomRecipes.DELETE_DEFAULT]);
        assertEquals("Cancel", CustomRecipes.discardOptions()[CustomRecipes.DISCARD_DEFAULT], "leaving by accident keeps the edits");
        assertEquals("Discard", CustomRecipes.discardOptions()[CustomRecipes.DISCARD]);
        assertEquals("Discard edits to Velvia?", CustomRecipes.discardTitle("Velvia"));
        assertEquals("Removes RECIPES/MINE.YML from the memory card. This cannot be undone.", CustomRecipes.deleteBody("MINE.YML"));
    }

    // ---- what mutation testing found unasserted
    /** the factory look under a name, with one value replaced: {field: value} */
    private static Recipes.Recipe with(String field, int v) {
        Recipes.Recipe f = Recipes.ALL[Recipes.FACTORY];
        int style = f.style, sat = f.sat, con = f.con, sharp = f.sharp, wb = f.wbMode, k = f.kelvin, ab = f.ab, gm = f.gm, pe = f.pe, ev = f.ev, dro = f.dro, sub = f.sub;
        switch (field) {
            case "sat": sat = v; break; case "con": con = v; break; case "sharp": sharp = v; break;
            case "pe": pe = v; break; case "sub": sub = v; break; case "wb": wb = v; break;
            case "kelvin": wb = WB_KELVIN; k = v; break; case "ab": ab = v; break; case "gm": gm = v; break;
            case "ev": ev = v; break; case "dro": dro = v; break; case "style": style = v; break;
            default: fail(field);
        }
        return new Recipes.Recipe(Recipes.CUSTOM, "X", style, sat, con, sharp, wb, k, ab, gm, pe, ev, dro, sub);
    }

    @Test void problemNamesEveryValueOutsideWhatTheStoreTakes() {
        assertEquals("saturation: 4 is not allowed", CustomRecipes.problem(with("sat", 4)));
        assertEquals("saturation: -4 is not allowed", CustomRecipes.problem(with("sat", -4)));
        assertEquals("contrast: 4 is not allowed", CustomRecipes.problem(with("con", 4)));
        assertEquals("sharpness: -4 is not allowed", CustomRecipes.problem(with("sharp", -4)));
        assertEquals("effect: ?14 is not allowed", CustomRecipes.problem(with("pe", 14)));
        assertEquals("effect: ?-1 is not allowed", CustomRecipes.problem(with("pe", -1)));
        assertEquals("effect-option: ?1 is not allowed", CustomRecipes.problem(with("sub", 1)), "no effect, so no option");
        assertEquals("white-balance: ?2 is not allowed", CustomRecipes.problem(with("wb", 2)));
        assertEquals("white-balance: 2400K is not allowed", CustomRecipes.problem(with("kelvin", 2400)));
        assertEquals("white-balance: 5650K is not allowed", CustomRecipes.problem(with("kelvin", 5650)));
        assertEquals("amber-blue: 8 is not allowed", CustomRecipes.problem(with("ab", 8)));
        assertEquals("green-magenta: -8 is not allowed", CustomRecipes.problem(with("gm", -8)));
        assertEquals("exposure: -16 is not allowed", CustomRecipes.problem(with("ev", -16)));
        assertEquals("dro: 7 is not allowed", CustomRecipes.problem(with("dro", 7)));
        assertEquals("dro: -1 is not allowed", CustomRecipes.problem(with("dro", -1)));
        assertEquals("style: ?0 is not allowed", CustomRecipes.problem(with("style", 0)));
    }

    @Test void problemTakesEveryValueOnTheEdgeOfItsRange() {
        for (Object[] c : new Object[][] { { "sat", 3 }, { "sat", -3 }, { "con", -3 }, { "sharp", 3 }, { "pe", 13 }, { "pe", 0 },
                { "kelvin", 2500 }, { "kelvin", 9900 }, { "ab", 7 }, { "ab", -7 }, { "gm", 7 }, { "ev", 15 }, { "ev", -15 },
                { "dro", 0 }, { "dro", 6 }, { "wb", 0 }, { "style", 14 } })
            assertNull(CustomRecipes.problem(with((String) c[0], (Integer) c[1])), c[0] + " " + c[1]);
        int[] rows = factoryRows(); rows[R_PE] = Recipes.PE_HIGHKEY; rows[R_SUB] = 2;
        assertNull(CustomRecipes.problem(CustomRecipes.recipe("x", rows)), "an effect's last option");
        rows[R_SUB] = 3;
        assertEquals("effect-option: ?3 is not allowed", CustomRecipes.problem(CustomRecipes.recipe("x", rows)), "one past it");
    }

    @Test void aFileNameKeepsDigits() {
        assertEquals("PORTRA40.YML", CustomRecipes.fileName("Portra 400", Collections.<String>emptyList()));
        assertEquals("400.YML", CustomRecipes.fileName("400", Collections.<String>emptyList()));
        assertEquals("AZ09.YML", CustomRecipes.fileName("a-z 0.9", Collections.<String>emptyList()), "letters and digits only, in capitals");
    }

    @Test void aCopysNameCollapsesSpacesAndUsesTheWholeLimit() {
        assertEquals("Golden Hour 2", CustomRecipes.copyName("Golden  Hour", Arrays.<String>asList()));
        String n22 = "abcdefghijklmnopqrstuv";
        assertEquals(n22 + " 2", CustomRecipes.copyName(n22, Arrays.<String>asList()), "22 letters and \" 2\" make exactly 24: nothing cut");
        assertEquals("Untitled 2", CustomRecipes.copyName("///", Arrays.<String>asList()), "a name with nothing a name may hold");
    }

    @Test void theFolderSkipsHiddenFilesTakesAFileAtTheLimitAndListsByName() throws IOException {
        write("._GOOD.YML", "name: Resource Fork\n");                      // what macOS leaves on a FAT card
        write("A.YML", "name: Zulu\n");
        write("B.YML", "name: Alpha\n");
        StringBuilder big = new StringBuilder("name: Big\n");
        while (big.length() < CustomRecipes.MAX_BYTES) big.append('#');
        big.setLength(CustomRecipes.MAX_BYTES);
        write("C.YML", big.toString());
        CustomRecipes.Loaded l = CustomRecipes.load(dir);
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (CustomRecipes.Entry e : l.entries) names.add(e.recipe.name);
        assertEquals(Arrays.asList("Alpha", "Big", "Zulu"), names, "A to Z by name, not by file; a file of exactly MAX_BYTES still loads");
        assertTrue(l.skipped.isEmpty(), "the hidden file is ignored, not skipped with a reason: " + l.skipped);
    }

    @Test void aFileWithoutTheExtensionStillNamesANamelessRecipe() {
        assertEquals("Mine", RecipeFormats.parse("style: vivid", "Mine").recipe.name);
        assertEquals("Mine", RecipeFormats.parse("name: \"\"\nstyle: vivid", "Mine.YML").recipe.name, "an empty name line, as no name line");
    }
}
