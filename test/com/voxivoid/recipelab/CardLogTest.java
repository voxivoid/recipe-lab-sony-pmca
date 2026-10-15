package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shareable logs on the card: where they go, what a line looks like, and the roll-over that keeps them small.
 * Whether the camera's card layer takes the folder, and whether a line survives a reboot, needs a camera.
 */
class CardLogTest {
    @TempDir File root;

    private static String read(File f) throws IOException { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }

    @Test void folderAndFileNamesAreDos83() {
        for (String n : new String[] { CardLog.DIR, CardLog.SUB }) assertTrue(n.length() <= 8 && n.equals(n.toUpperCase()), n);
        for (String n : new String[] { CardLog.LOG, CardLog.OLD, CardLog.DIFF, CardLog.KEYS, CardLog.LOCKS }) assertTrue(n.matches("[A-Z0-9]{1,8}\\.[A-Z]{1,3}"), n);
        assertEquals("RECIPLAB/LOGS/LOG.TXT", CardLog.path(CardLog.LOG));
    }

    @Test void noCardNoFolder() {
        assertNull(CardLog.dir(null));
        assertThrows(IOException.class, () -> CardLog.append(null, CardLog.LOG, "x\n"));
    }

    @Test void appendCreatesTheFoldersAndAppends() throws IOException {
        File dir = CardLog.dir(root);
        CardLog.append(dir, CardLog.LOG, "one\n");
        CardLog.append(dir, CardLog.LOG, "two\n");
        assertEquals(new File(new File(root, "RECIPLAB"), "LOGS"), dir);
        assertEquals("one\ntwo\n", read(new File(dir, CardLog.LOG)));
    }

    @Test void aFullLogRollsOverAndKeepsOneOldFile() throws IOException {
        File dir = CardLog.dir(root);
        char[] big = new char[CardLog.MAX_BYTES - 10]; Arrays.fill(big, 'a');
        CardLog.append(dir, CardLog.LOG, new String(big));
        CardLog.append(dir, CardLog.LOG, "first after the cap\n");
        assertEquals("first after the cap\n", read(new File(dir, CardLog.LOG)));
        assertEquals(CardLog.MAX_BYTES - 10, new File(dir, CardLog.OLD).length());

        CardLog.append(dir, CardLog.LOG, new String(big));       // a second roll replaces the old file, never a third
        assertEquals(new String(big), read(new File(dir, CardLog.LOG)));
        assertEquals(2, dir.list().length);
    }

    @Test void theDiffFileNeverRolls() throws IOException {
        File dir = CardLog.dir(root);
        char[] big = new char[CardLog.MAX_BYTES]; Arrays.fill(big, 'a');
        CardLog.append(dir, CardLog.DIFF, new String(big));
        CardLog.append(dir, CardLog.DIFF, "more\n");
        assertEquals(CardLog.MAX_BYTES + 5, new File(dir, CardLog.DIFF).length());
        assertFalse(new File(dir, CardLog.OLD).exists());
    }

    @Test void rollsOnlyAFileThatHasSomethingInIt() {
        assertFalse(CardLog.rolls(0, CardLog.MAX_BYTES * 2));
        assertFalse(CardLog.rolls(10, CardLog.MAX_BYTES - 10));
        assertTrue(CardLog.rolls(10, CardLog.MAX_BYTES - 9));
    }

    @Test void aLineHasTheTimeTheKindAndTheMessage() {
        String l = CardLog.line(CardLog.WRITE, "Velvia: 2 slots");
        assertTrue(l.matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d  WRITE    Velvia: 2 slots\n"), l);
    }

    @Test void theCameraClockIsUtcPlusItsOffset() {
        assertEquals("2026-10-15 14:03:27", CardLog.stamp(2026, 10, 15, 13, 3, 27, 60));    // Lisbon summer time
        assertEquals("2026-01-01 00:30:00", CardLog.stamp(2025, 12, 31, 23, 30, 0, 60));    // across midnight and the year
        assertEquals("2026-10-15 08:03:27", CardLog.stamp(2026, 10, 15, 13, 3, 27, -300));
    }

    @Test void offTheCameraTheSystemClock() { assertTrue(CardLog.now().startsWith(String.valueOf(java.time.Year.now().getValue())), CardLog.now()); }

    @Test void aMultiLineMessageKeepsItsIndent() {
        String[] l = CardLog.line(CardLog.CRASH, "boom\nat x").split("\n");
        assertEquals(2, l.length);
        assertEquals(l[0].indexOf("boom"), l[1].indexOf("at x"));
    }

    @Test void theHeaderNamesTheBodyAndTheBuild() {
        assertEquals("model ILCE-6000  ·  platform 2.0  ·  app 1.7.0  ·  keys Fn",
                CardLog.header("ILCE-6000", "2.0", "1.7.0", "Fn"));
        assertEquals("model unknown  ·  platform unknown  ·  app unknown  ·  keys unknown", CardLog.header(null, "", null, null));
    }

    @Test void aRefusedWriteSaysWhereItStopped() {
        assertEquals("01070c7f = 3 refused: Backup_get_datasize failed  (2 of 9 written before it)",
                CardLog.writeFailed(0x01070c7f, 3, "Backup_get_datasize failed", 2, 9));
    }

    @Test void aWriteListsEverySlot() {
        assertEquals("Velvia: 2 slots  01070175=1  01070187=3",
                CardLog.written("Velvia", Arrays.asList(new Params.Write(0x01070175, 1), new Params.Write(0x01070187, 3))));
        assertEquals("Factory: 1 slot  01070175=0", CardLog.written("Factory", Arrays.asList(new Params.Write(0x01070175, 0))));
    }

    @Test void idsAreEightHexDigits() {
        assertEquals("01070175 0107031c", CardLog.ids(Arrays.asList(0x01070175, 0x0107031c)));
        assertEquals("", CardLog.ids(Arrays.<Integer>asList()));
    }

    @Test void previewNamesOnlyTheValuesTheCameraDidNotKeep() {
        Map<String, String> wanted = new LinkedHashMap<String, String>(), got = new LinkedHashMap<String, String>();
        wanted.put("color-mode", "mono"); wanted.put("saturation", "0"); wanted.put("contrast", "1");
        got.put("color-mode", "standard"); got.put("saturation", "0");
        assertEquals("color-mode mono>standard  contrast 1>null", CardLog.previewDropped(wanted, got));
        got.put("color-mode", "mono"); got.put("contrast", "1");
        assertEquals("", CardLog.previewDropped(wanted, got));
    }

    @Test void aStackHasTheExceptionAndWhereItCameFrom() {
        String s = CardLog.stack(new IllegalStateException("boom"));
        assertTrue(s.startsWith("java.lang.IllegalStateException: boom"), s);
        assertTrue(s.contains("CardLogTest"), s);
    }
}
