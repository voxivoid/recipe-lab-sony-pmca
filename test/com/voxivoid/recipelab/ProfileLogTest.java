package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileLogTest {

    /**
     * test/fixtures/profile-log-vectors.tsv: each line of PROFILES.TXT this app writes, after the share code of a recipe
     * with that profile ("off" for none), so Recipe Lab Studio reads every line back to the same profile. Studio keeps
     * the same file. Written from here: RECIPELAB_WRITE_GOLDEN=1 tools/test.sh ProfileLog, then copy it to Studio.
     */
    static final Path VECTORS = Paths.get("test/fixtures/profile-log-vectors.tsv");

    static final IspProfiles.Look SLOG3 = new IspProfiles.Look(2, 2);   // s-log3, s-gamut3-cine

    static String vectors() {
        StringBuilder s = new StringBuilder(
                "# Recipe Lab (camera) PROFILES.TXT lines: share code of a recipe with the profile (or off), a tab, the line.\n"
              + "# Written by ProfileLogTest (RECIPELAB_WRITE_GOLDEN=1 tools/test.sh ProfileLog), never by hand; the same file is in Recipe Lab Studio.\n"
              + "# The file starts with:\n");
        for (String h : ProfileLog.HEAD.split("\n")) s.append("#   ").append(h).append('\n');
        List<IspProfiles.Look> looks = new ArrayList<IspProfiles.Look>();
        looks.add(null);
        for (String[] l : IspGoldenTest.LOOKS) looks.add(IspGoldenTest.look(l[1]));
        looks.add(IspGoldenTest.look("cine4,bt2020,-15,7,wide,95,-5,7,-7,3"));
        looks.add(IspGoldenTest.look("hlg,s-gamut3,15,-7,narrow,75,5,-7,7,-3"));
        int second = 0;
        for (IspProfiles.Look l : looks) {
            String code = "off";
            if (l != null) {
                String link = RecipeShare.encode(new Recipes.Recipe(Recipes.CUSTOM, "Log", 1, 0, 0, 0, Params.WB_AUTO, 0, 0, 0, 0, 0, Recipes.DRO_OFF, 0, l));
                code = link.substring(link.indexOf('#') + 1);
            }
            s.append(code).append('\t').append(ProfileLog.line(String.format("2026-11-26 14:%02d:%02d", second / 60, second % 60), l));
            second += 7;
        }
        return s.toString();
    }

    @Test void theVectorsAreWhatThisAppWrites() throws Exception {
        String want = vectors();
        if ("1".equals(System.getenv("RECIPELAB_WRITE_GOLDEN"))) Files.write(VECTORS, want.getBytes(StandardCharsets.UTF_8));
        assertEquals(want, new String(Files.readAllBytes(VECTORS), StandardCharsets.UTF_8),
                "run RECIPELAB_WRITE_GOLDEN=1 tools/test.sh ProfileLog, then copy the file to Studio");
    }

    @Test void aLineIsTheTimeThenFormatThreesKeys() {
        assertEquals("2026-11-26 14:03:27  gamma=off\n", ProfileLog.line("2026-11-26 14:03:27", null));
        assertEquals("2026-11-26 14:03:27  gamma=s-log3 color-mode=s-gamut3-cine black-level=0 black-gamma=0 black-gamma-range=middle"
                + " knee=off knee-slope=0 color-saturation=0 color-phase=0 s-curve=0\n", ProfileLog.line("2026-11-26 14:03:27", SLOG3));
        // signed as a recipe file signs them; what does nothing is written at its default
        IspProfiles.Look l = IspGoldenTest.look("cine4,bt2020,-15,7,wide,95,-5,7,-7,3");
        assertEquals("gamma=cine4 color-mode=bt2020 black-level=-15 black-gamma=+7 black-gamma-range=wide knee=95 knee-slope=-5"
                + " color-saturation=+7 color-phase=-7 s-curve=+3", ProfileLog.entry(l));
        assertEquals(ProfileLog.entry(new IspProfiles.Look(2, 2, 0, 0, 0, IspProfiles.KNEE_OFF, 3, 0, 0, 0)), ProfileLog.entry(SLOG3));
    }

    @Test void aNewFileGetsTheHeadThenTheLine() {
        String[] u = ProfileLog.update(null, "2026-11-26 14:03:27", SLOG3);
        assertArrayEquals(new String[] { "w", ProfileLog.HEAD + ProfileLog.line("2026-11-26 14:03:27", SLOG3) }, u);
        assertArrayEquals(u, ProfileLog.update("", "2026-11-26 14:03:27", SLOG3));
        assertTrue(ProfileLog.HEAD.endsWith("\nformat: 1\n"));
        assertEquals(1, ProfileLog.format(u[1]));
    }

    @Test void aLineIsAddedOnlyWhenTheProfileChanges() {
        String f = ProfileLog.update(null, "2026-11-26 14:00:00", SLOG3)[1];
        assertNull(ProfileLog.update(f, "2026-11-26 15:00:00", SLOG3));
        assertArrayEquals(new String[] { "a", "2026-11-26 15:00:00  gamma=off\n" }, ProfileLog.update(f, "2026-11-26 15:00:00", null));
        String off = f + "2026-11-26 15:00:00  gamma=off\n";
        assertNull(ProfileLog.update(off, "2026-11-26 16:00:00", null));
        assertArrayEquals(new String[] { "a", ProfileLog.line("2026-11-26 16:00:00", SLOG3) }, ProfileLog.update(off, "2026-11-26 16:00:00", SLOG3));
        // a file cut short mid-line still gets its new line on a line of its own
        assertArrayEquals(new String[] { "a", "\n2026-11-26 16:00:00  gamma=off\n" }, ProfileLog.update(f + "2026-11-2", "2026-11-26 16:00:00", null));
    }

    @Test void theLastLineIsTheEntryAfterItsTime() {
        assertNull(ProfileLog.last(null));
        assertNull(ProfileLog.last(ProfileLog.HEAD));
        assertEquals("gamma=off", ProfileLog.last(ProfileLog.HEAD + ProfileLog.line("2026-11-26 14:00:00", SLOG3) + "2026-11-26 15:00:00  gamma=off\n"));
        assertEquals(2, ProfileLog.entries(ProfileLog.HEAD + "2026-11-26 14:00:00  gamma=off\n2026-11-26 15:00:00  gamma=off\n# 2026\n").size());
    }

    @Test void aFileFromANewerAppIsLeftAloneAndOneThatIsNotOursIsReplaced() {
        String newer = "# from a newer app\nformat: 2\n2026-11-26 14:00:00  something new\n";
        assertNull(ProfileLog.update(newer, "2026-11-26 15:00:00", SLOG3));
        assertNull(ProfileLog.update("format: x\n", "2026-11-26 15:00:00", SLOG3));
        assertEquals(0, ProfileLog.format("no format here\n"));
        assertEquals(-1, ProfileLog.format("format: x\n"));
        assertArrayEquals(new String[] { "w", ProfileLog.HEAD + "2026-11-26 15:00:00  gamma=off\n" }, ProfileLog.update("junk\n", "2026-11-26 15:00:00", null));
    }

    @Test void aFullFileKeepsItsLastLines() {
        StringBuilder f = new StringBuilder(ProfileLog.HEAD);
        int n = 0;
        while (f.length() <= ProfileLog.MAX_BYTES - 200) f.append(ProfileLog.line(String.format("2026-01-01 %02d:%02d:%02d", n / 3600, n / 60 % 60, n % 60), n++ % 2 == 0 ? SLOG3 : null));
        IspProfiles.Look next = new IspProfiles.Look(1, 0);       // s-log2: never one of the lines above
        String[] small = ProfileLog.update(f.toString(), "2026-11-26 15:00:00", next);
        assertEquals("a", small[0], "still room: appended");
        while (f.length() <= ProfileLog.MAX_BYTES) f.append(ProfileLog.line(String.format("2026-01-01 %02d:%02d:%02d", n / 3600, n / 60 % 60, n % 60), n++ % 2 == 0 ? SLOG3 : null));
        String[] u = ProfileLog.update(f.toString(), "2026-11-26 15:00:00", next);
        assertEquals("w", u[0]);
        assertTrue(u[1].startsWith(ProfileLog.HEAD));
        List<String> kept = ProfileLog.entries(u[1]), had = ProfileLog.entries(f.toString());
        assertEquals(ProfileLog.KEEP, kept.size());
        assertEquals(had.subList(had.size() - (ProfileLog.KEEP - 1), had.size()), kept.subList(0, ProfileLog.KEEP - 1));
        assertEquals(ProfileLog.line("2026-11-26 15:00:00", next).trim(), kept.get(ProfileLog.KEEP - 1));
        assertTrue(u[1].length() < ProfileLog.MAX_BYTES);
    }

    @Test void recordWritesTheCardsFileAndAppendsToIt(@TempDir File card) throws Exception {
        ProfileLog.record(card, "2026-11-26 14:00:00", SLOG3);
        ProfileLog.record(card, "2026-11-26 14:30:00", SLOG3);
        ProfileLog.record(card, "2026-11-26 15:00:00", null);
        File f = new File(new File(card, "RECIPLAB"), "PROFILES.TXT");
        assertEquals(f, ProfileLog.file(card));
        assertNull(ProfileLog.file(null));
        assertEquals(ProfileLog.HEAD + ProfileLog.line("2026-11-26 14:00:00", SLOG3) + "2026-11-26 15:00:00  gamma=off\n",
                new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        ProfileLog.record(null, "2026-11-26 16:00:00", SLOG3);   // no card: nothing, and no exception
    }
}
