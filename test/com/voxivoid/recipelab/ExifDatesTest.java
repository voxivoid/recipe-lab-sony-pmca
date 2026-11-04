package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Save image's picture as the A6000 writes it (DSC07681, 2026-11-03): DateTime the camera's clock, DateTimeOriginal
 * and DateTimeDigitized the photo it was encoded from. Both are set to DateTime.
 */
class ExifDatesTest {
    static final String NOW = "2026:11:03 10:38:02", SOURCE = "2026:10:16 14:00:00";

    /** a JPEG head: SOI, APP1 Exif with IFD0 (Model, DateTime, ExifIFD) and the Exif IFD (Original, Digitized) */
    static byte[] jpeg(boolean le, String now, String original, String digitized) {
        ByteBuffer t = ByteBuffer.allocate(256).order(le ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        t.put(le ? (byte) 'I' : (byte) 'M').put(le ? (byte) 'I' : (byte) 'M').putShort((short) 42).putInt(8);
        int ifd0 = 8, exif = ifd0 + 2 + 3 * 12 + 4, strings = exif + 2 + 2 * 12 + 4;
        t.position(ifd0); t.putShort((short) 3);
        entry(t, 0x0110, 2, 4, 0x00303030);                    // Model "000", inline
        entry(t, 0x0132, 2, 20, strings);
        entry(t, 0x8769, 4, 1, exif);
        t.putInt(0);
        t.position(exif); t.putShort((short) 2);
        entry(t, 0x9003, 2, 20, strings + 20);
        entry(t, 0x9004, 2, 20, strings + 40);
        t.putInt(0);
        t.position(strings);
        for (String s : new String[] { now, original, digitized }) { t.put(s.getBytes()); t.put((byte) 0); }
        byte[] tiff = Arrays.copyOf(t.array(), t.position());
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0xFF); o.write(0xD8);
        o.write(0xFF); o.write(0xE0); o.write(0); o.write(4); o.write(1); o.write(2);       // an APP0 first, skipped
        int len = 2 + 6 + tiff.length;
        o.write(0xFF); o.write(0xE1); o.write(len >> 8); o.write(len & 0xFF);
        o.write('E'); o.write('x'); o.write('i'); o.write('f'); o.write(0); o.write(0);
        o.write(tiff, 0, tiff.length);
        o.write(0xFF); o.write(0xDA); o.write(0); o.write(2);
        return o.toByteArray();
    }

    private static void entry(ByteBuffer t, int tag, int type, int count, int value) {
        t.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
    }

    private static boolean has(byte[] b, String s) { return new String(b, java.nio.charset.StandardCharsets.ISO_8859_1).contains(s); }

    private static int count(byte[] b, String s) {
        String all = new String(b, java.nio.charset.StandardCharsets.ISO_8859_1);
        int n = 0;
        for (int i = all.indexOf(s); i >= 0; i = all.indexOf(s, i + 1)) n++;
        return n;
    }

    @Test void theSourcePhotosDatesBecomeTheSaveTime() {
        for (boolean le : new boolean[] { true, false }) {
            byte[] b = jpeg(le, NOW, SOURCE, SOURCE);
            int size = b.length;
            assertEquals("dates: was " + SOURCE + ", now " + NOW, ExifDates.align(b), le ? "II" : "MM");
            assertEquals(3, count(b, NOW), "DateTime, Original and Digitized");
            assertFalse(has(b, SOURCE));
            assertEquals(size, b.length);
            assertEquals("dates: already " + NOW, ExifDates.align(b), "a second time changes nothing");
        }
    }

    @Test void onlyTheOneThatDiffersIsWritten() {
        byte[] b = jpeg(true, NOW, NOW, SOURCE);
        assertEquals("dates: was " + SOURCE + ", now " + NOW, ExifDates.align(b));
        assertEquals(3, count(b, NOW));
        b = jpeg(true, NOW, SOURCE, NOW);
        assertEquals("dates: was " + SOURCE + ", now " + NOW, ExifDates.align(b));
        assertEquals(3, count(b, NOW));
    }

    @Test void nothingElseMoves() {
        byte[] b = jpeg(false, NOW, SOURCE, SOURCE), want = jpeg(false, NOW, NOW, NOW);
        ExifDates.align(b);
        assertArrayEquals(want, b);
    }

    @Test void whatIsNotACameraJpegIsLeftAlone() {
        assertEquals("dates: no EXIF", ExifDates.align(new byte[0]));
        assertEquals("dates: no EXIF", ExifDates.align(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA, 0, 2 }));
        byte[] png = jpeg(true, NOW, SOURCE, SOURCE);
        png[1] = 0x50;
        assertEquals("dates: no EXIF", ExifDates.align(png), "no SOI");
        byte[] badOrder = jpeg(true, NOW, SOURCE, SOURCE);
        badOrder[ExifDates.tiffStart(badOrder)] = 'X';
        assertEquals("dates: no EXIF", ExifDates.align(badOrder));
        byte[] noExifTag = jpeg(true, NOW, SOURCE, SOURCE);
        byte[] copy = noExifTag.clone();
        int t = ExifDates.tiffStart(noExifTag);
        noExifTag[t + 8 + 2 + 24] = 0x01;                     // the third IFD0 entry's tag is no longer 0x8769
        assertEquals("dates: no DateTime", ExifDates.align(noExifTag));
        assertNotEquals(Arrays.toString(copy), Arrays.toString(noExifTag));
        assertTrue(has(noExifTag, SOURCE), "untouched");
    }

    @Test void aDateThatIsNotTwentyCharactersIsNotWrittenOver() {
        byte[] b = jpeg(true, NOW, SOURCE, SOURCE);
        int t = ExifDates.tiffStart(b);
        b[t + 50 + 4] = 19;                                   // the Exif IFD's first entry (Original): count 19
        assertEquals("dates: was " + SOURCE + ", now " + NOW, ExifDates.align(b), "Digitized still is");
        assertEquals(1, count(b, SOURCE), "Original kept");
    }

    @Test void theFileIsWrittenOnlyWhenItChanged(@TempDir File dir) throws Exception {
        File f = new File(dir, "DSC07681.JPG");
        byte[] tail = new byte[ExifDates.HEAD + 1000];
        Arrays.fill(tail, (byte) 7);
        byte[] head = jpeg(true, NOW, SOURCE, SOURCE);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(head, 0, head.length); o.write(tail, 0, tail.length);
        Files.write(f.toPath(), o.toByteArray());
        assertEquals("dates: was " + SOURCE + ", now " + NOW, ExifDates.align(f));
        byte[] after = Files.readAllBytes(f.toPath());
        assertEquals(head.length + tail.length, after.length);
        assertEquals(3, count(after, NOW));
        assertEquals(7, after[after.length - 1], "past the head: kept");
        f.setLastModified(1000);
        assertEquals("dates: already " + NOW, ExifDates.align(f));
        assertEquals(1000, f.lastModified(), "not written again");
        assertEquals("dates: no file missing.JPG", ExifDates.align(new File(dir, "missing.JPG")));
        assertFalse(new File(dir, "missing.JPG").exists(), "not made");
    }
}
