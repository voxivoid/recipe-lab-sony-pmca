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
            s.append(code).append('\t').append(ProfileLog.line(String.format("2026-11-26 14:%02d:%02d", second / 60, second % 60),
                    second == 0 ? "none" : ProfileLog.after(new int[] { 100 + second / 70, 8000 + second }), l));
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

    @Test void aLineIsTheTimeTheNewestPhotoThenFormatThreesKeys() {
        assertEquals("2026-11-26 14:03:27  after=100-8117 gamma=off\n", ProfileLog.line("2026-11-26 14:03:27", "100-8117", null));
        assertEquals("2026-11-26 14:03:27  after=none gamma=s-log3 color-mode=s-gamut3-cine black-level=0 black-gamma=0 black-gamma-range=middle"
                + " knee=off knee-slope=0 color-saturation=0 color-phase=0 s-curve=0\n", ProfileLog.line("2026-11-26 14:03:27", "none", SLOG3));
        assertEquals("100-8117", ProfileLog.after(new int[] { 100, 8117 }));
        assertEquals("999-0001", ProfileLog.after(new int[] { 999, 1 }));
        assertEquals("none", ProfileLog.after(null));
        // signed as a recipe file signs them; what does nothing is written at its default
        IspProfiles.Look l = IspGoldenTest.look("cine4,bt2020,-15,7,wide,95,-5,7,-7,3");
        assertEquals("gamma=cine4 color-mode=bt2020 black-level=-15 black-gamma=+7 black-gamma-range=wide knee=95 knee-slope=-5"
                + " color-saturation=+7 color-phase=-7 s-curve=+3", ProfileLog.entry(l));
        assertEquals(ProfileLog.entry(new IspProfiles.Look(2, 2, 0, 0, 0, IspProfiles.KNEE_OFF, 3, 0, 0, 0)), ProfileLog.entry(SLOG3));
    }

    @Test void aNewFileGetsTheHeadThenTheLine() {
        String[] u = ProfileLog.update(null, "2026-11-26 14:03:27", "100-8117", SLOG3);
        assertArrayEquals(new String[] { "w", ProfileLog.HEAD + ProfileLog.line("2026-11-26 14:03:27", "100-8117", SLOG3) }, u);
        assertArrayEquals(u, ProfileLog.update("", "2026-11-26 14:03:27", "100-8117", SLOG3));
        assertTrue(ProfileLog.HEAD.endsWith("\nformat: 1\n"));
        assertEquals(1, ProfileLog.format(u[1]));
    }

    @Test void aLineIsAddedOnlyWhenTheProfileChangesWhateverPhotosCameSince() {
        String f = ProfileLog.update(null, "2026-11-26 14:00:00", "100-8117", SLOG3)[1];
        assertNull(ProfileLog.update(f, "2026-11-26 15:00:00", "100-8130", SLOG3), "the line in force covers the new photos");
        assertArrayEquals(new String[] { "a", "2026-11-26 15:00:00  after=100-8130 gamma=off\n" }, ProfileLog.update(f, "2026-11-26 15:00:00", "100-8130", null));
        String off = f + "2026-11-26 15:00:00  after=100-8130 gamma=off\n";
        assertNull(ProfileLog.update(off, "2026-11-26 16:00:00", "100-8131", null));
        assertArrayEquals(new String[] { "a", ProfileLog.line("2026-11-26 16:00:00", "100-8131", SLOG3) }, ProfileLog.update(off, "2026-11-26 16:00:00", "100-8131", SLOG3));
        // a file cut short mid-line still gets its new line on a line of its own
        assertArrayEquals(new String[] { "a", "\n2026-11-26 16:00:00  after=100-8131 gamma=off\n" }, ProfileLog.update(f + "2026-11-2", "2026-11-26 16:00:00", "100-8131", null));
    }

    @Test void theLastLineIsTheProfileAfterItsTimeAndPhoto() {
        assertNull(ProfileLog.last(null));
        assertNull(ProfileLog.last(ProfileLog.HEAD));
        assertEquals("gamma=off", ProfileLog.last(ProfileLog.HEAD + ProfileLog.line("2026-11-26 14:00:00", "none", SLOG3) + "2026-11-26 15:00:00  after=100-0001 gamma=off\n"));
        assertEquals(ProfileLog.entry(SLOG3), ProfileLog.last(ProfileLog.HEAD + ProfileLog.line("2026-11-26 14:00:00", "999-9999", SLOG3)));
        assertNull(ProfileLog.last(ProfileLog.HEAD + "2026-11-26 15:00:00  gamma=off\n"), "a line without after places no photo");
        String early = ProfileLog.HEAD + "2014-01-07 23:48:36  " + ProfileLog.entry(SLOG3) + "\n";
        assertArrayEquals(new String[] { "a", ProfileLog.line("2026-11-26 15:00:00", "100-8120", SLOG3) },
                ProfileLog.update(early, "2026-11-26 15:00:00", "100-8120", SLOG3), "the same profile, now placed");
        assertEquals("", ProfileLog.last(ProfileLog.HEAD + "2026-11-26 15:00:00  after=none\n"));
        assertEquals(2, ProfileLog.entries(ProfileLog.HEAD + "2026-11-26 14:00:00  after=none gamma=off\n2026-11-26 15:00:00  after=none gamma=off\n# 2026\n").size());
    }

    @Test void numbersThatStartOverStartTheFileAgainEvenForTheSameProfile() {
        String f = ProfileLog.HEAD + ProfileLog.line("2026-11-26 14:00:00", "none", null)
                + ProfileLog.line("2026-11-26 15:00:00", "100-8120", SLOG3);
        String[] restart = { "w", ProfileLog.HEAD + ProfileLog.line("2026-11-26 16:00:00", "100-0005", SLOG3) };
        assertArrayEquals(restart, ProfileLog.update(f, "2026-11-26 16:00:00", "100-0005", SLOG3), "File Number Reset");
        assertArrayEquals(new String[] { "w", ProfileLog.HEAD + ProfileLog.line("2026-11-26 16:00:00", "099-9999", null) },
                ProfileLog.update(f, "2026-11-26 16:00:00", "099-9999", null), "a lower folder");
        assertArrayEquals(new String[] { "w", ProfileLog.HEAD + ProfileLog.line("2026-11-26 16:00:00", "none", SLOG3) },
                ProfileLog.update(f, "2026-11-26 16:00:00", "none", SLOG3), "every photo deleted");
        assertNull(ProfileLog.update(f, "2026-11-26 16:00:00", "100-8120", SLOG3), "no photo since: the same place");
        assertNull(ProfileLog.update(f, "2026-11-26 16:00:00", "101-0001", SLOG3), "the next folder is later");
        assertArrayEquals(new String[] { "a", ProfileLog.line("2026-11-26 16:00:00", "100-8121", null) },
                ProfileLog.update(f, "2026-11-26 16:00:00", "100-8121", null));
        String fromNone = ProfileLog.HEAD + ProfileLog.line("2026-11-26 14:00:00", "none", SLOG3);
        assertNull(ProfileLog.update(fromNone, "2026-11-26 16:00:00", "none", SLOG3), "still empty");
        assertNull(ProfileLog.update(fromNone, "2026-11-26 16:00:00", "100-0001", SLOG3), "none is before every photo");
        String unplaced = ProfileLog.HEAD + "2014-01-07 23:48:36  " + ProfileLog.entry(SLOG3) + "\n";
        assertEquals("a", ProfileLog.update(unplaced, "2026-11-26 16:00:00", "100-0001", SLOG3)[0], "nothing to start over from");
        assertEquals("none", ProfileLog.lastAfter(fromNone));
        assertEquals("100-8120", ProfileLog.lastAfter(f));
        assertNull(ProfileLog.lastAfter(unplaced));
        assertNull(ProfileLog.lastAfter(ProfileLog.HEAD));
        assertNull(ProfileLog.lastAfter(ProfileLog.HEAD + "2026-11-26 15:00:00  after=100-81200 gamma=off\n"));
        assertEquals("100-8120", ProfileLog.lastAfter(ProfileLog.HEAD + "2026-11-26 15:00:00  after=100-8120\n"));
    }

    @Test void aFileFromANewerAppIsLeftAloneAndOneThatIsNotOursIsReplaced() {
        String newer = "# from a newer app\nformat: 2\n2026-11-26 14:00:00  something new\n";
        assertNull(ProfileLog.update(newer, "2026-11-26 15:00:00", "none", SLOG3));
        assertNull(ProfileLog.update("format: x\n", "2026-11-26 15:00:00", "none", SLOG3));
        assertEquals(0, ProfileLog.format("no format here\n"));
        assertEquals(-1, ProfileLog.format("format: x\n"));
        assertArrayEquals(new String[] { "w", ProfileLog.HEAD + "2026-11-26 15:00:00  after=none gamma=off\n" }, ProfileLog.update("junk\n", "2026-11-26 15:00:00", "none", null));
    }

    @Test void aFullFileKeepsItsLastLines() {
        StringBuilder f = new StringBuilder(ProfileLog.HEAD);
        int n = 0;
        while (f.length() <= ProfileLog.MAX_BYTES - 400) f.append(line(n++));
        IspProfiles.Look next = new IspProfiles.Look(1, 0);       // s-log2: never one of the lines above
        String[] small = ProfileLog.update(f.toString(), "2026-11-26 15:00:00", "999-0001", next);
        assertEquals("a", small[0], "still room: appended");
        while (f.length() <= ProfileLog.MAX_BYTES) f.append(line(n++));
        String[] u = ProfileLog.update(f.toString(), "2026-11-26 15:00:00", "999-0001", next);
        assertEquals("w", u[0]);
        assertTrue(u[1].startsWith(ProfileLog.HEAD));
        List<String> kept = ProfileLog.entries(u[1]), had = ProfileLog.entries(f.toString());
        assertEquals(ProfileLog.KEEP, kept.size());
        assertEquals(had.subList(had.size() - (ProfileLog.KEEP - 1), had.size()), kept.subList(0, ProfileLog.KEEP - 1));
        assertEquals(ProfileLog.line("2026-11-26 15:00:00", "999-0001", next).trim(), kept.get(ProfileLog.KEEP - 1));
        assertTrue(u[1].length() < ProfileLog.MAX_BYTES);
    }

    private static String line(int n) {
        return ProfileLog.line(String.format("2026-01-01 %02d:%02d:%02d", n / 3600, n / 60 % 60, n % 60),
                ProfileLog.after(new int[] { 100 + n / 9999, n % 9999 + 1 }), n % 2 == 0 ? SLOG3 : null);
    }

    private static void touch(File dir, String name) throws Exception {
        dir.mkdirs();
        assertTrue(new File(dir, name).createNewFile());
    }

    @Test void theNewestPhotoIsTheHighestFolderThenFile(@TempDir File card) throws Exception {
        File dcim = new File(card, "DCIM");
        assertNull(Dcf.newest(dcim), "no DCIM");
        assertNull(Dcf.newest(null));
        dcim.mkdirs();
        assertNull(Dcf.newest(dcim), "no photos");
        touch(new File(dcim, "100MSDCF"), "DSC09998.JPG");
        touch(new File(dcim, "100MSDCF"), "DSC09999.ARW");
        assertArrayEquals(new int[] { 100, 9999 }, Dcf.newest(dcim), "a RAW counts, not only JPEGs");
        touch(new File(dcim, "101MSDCF"), "DSC00001.ARW");
        touch(new File(dcim, "101MSDCF"), "DSC00002.JPG");
        touch(new File(dcim, "101MSDCF"), "NOTES.TXTX");
        touch(new File(dcim, "102_NOTDCF"), "DSC00005.JPG");
        touch(dcim, "DSC00009.JPG");
        assertArrayEquals(new int[] { 101, 2 }, Dcf.newest(dcim));
        touch(new File(dcim, "102MSDCF"), "NOTES.TXT");
        touch(new File(dcim, "101ABCDE"), "DSC00003.JPG");
        assertArrayEquals(new int[] { 101, 3 }, Dcf.newest(dcim), "an empty higher folder is passed; two folders of one number both count");
        touch(new File(dcim, "099MSDCF"), "DSC09999.JPG");
        touch(new File(dcim, "1000MSDCF"), "DSC09999.JPG");
        assertArrayEquals(new int[] { 101, 3 }, Dcf.newest(dcim), "names the camera does not give");
        assertTrue(Dcf.compare(new int[] { 101, 1 }, new int[] { 100, 9999 }) > 0);
        assertTrue(Dcf.compare(new int[] { 100, 2 }, new int[] { 100, 3 }) < 0);
        assertEquals(0, Dcf.compare(new int[] { 100, 2 }, new int[] { 100, 2 }));
    }

    @Test void recordStartsTheFileAgainWhenTheCardsNumbersStartOver(@TempDir File card) throws Exception {
        File folder = new File(new File(card, "DCIM"), "100MSDCF");
        touch(folder, "DSC08120.ARW");
        ProfileLog.record(card, SLOG3);
        assertTrue(new File(folder, "DSC08120.ARW").delete());
        touch(folder, "DSC00002.ARW");
        ProfileLog.record(card, SLOG3);
        List<String> e = ProfileLog.entries(new String(Files.readAllBytes(ProfileLog.file(card).toPath()), StandardCharsets.UTF_8));
        assertEquals(1, e.size(), e.toString());
        assertTrue(e.get(0).endsWith("  after=100-0002 " + ProfileLog.entry(SLOG3)), e.get(0));
    }

    @Test void recordWritesTheCardsFileAfterItsNewestPhoto(@TempDir File card) throws Exception {
        File dcim = new File(card, "DCIM"), folder = new File(dcim, "100MSDCF");
        ProfileLog.record(card, SLOG3);                           // an empty card: every photo to come
        touch(folder, "DSC08117.ARW");
        ProfileLog.record(card, SLOG3);                           // no change: no line
        ProfileLog.record(card, null);
        touch(folder, "DSC08118.ARW");
        ProfileLog.record(card, "2026-11-26 16:00:00", "100-8118", SLOG3);
        File f = new File(new File(card, "RECIPLAB"), "PROFILES.TXT");
        assertEquals(f, ProfileLog.file(card));
        assertNull(ProfileLog.file(null));
        List<String> e = ProfileLog.entries(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        assertEquals(3, e.size());
        assertTrue(e.get(0).endsWith("  after=none " + ProfileLog.entry(SLOG3)), e.get(0));
        assertTrue(e.get(1).endsWith("  after=100-8117 gamma=off"), e.get(1));
        assertEquals("2026-11-26 16:00:00  after=100-8118 " + ProfileLog.entry(SLOG3), e.get(2));
        ProfileLog.record(null, SLOG3);                            // no card: nothing, and no exception
        ProfileLog.record(null, "2026-11-26 16:00:00", "none", SLOG3);
    }
}
