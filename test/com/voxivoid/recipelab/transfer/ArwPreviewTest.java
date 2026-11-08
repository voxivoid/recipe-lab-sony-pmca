package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArwPreviewTest {
    private File dir;

    @BeforeEach void setUp() throws IOException { dir = Fixtures.tempDir(); }
    @AfterEach void tearDown() { Fixtures.deleteTree(dir); }

    private File arw(byte[] data) throws IOException {
        return Fixtures.write(new File(dir, "x.ARW"), data);
    }

    @Test void sonyLayoutGivesIfd0AsLargeAndIfd1AsSmall() throws IOException {
        byte[] large = Fixtures.fakeJpeg(1616, 1080, 5000), small = Fixtures.fakeJpeg(160, 120, 300);
        ArwPreview.Previews p = ArwPreview.locate(arw(Fixtures.sonyLikeArw(true, 6, large, small)));
        assertEquals(1616, p.large.width);
        assertEquals(1080, p.large.height);
        assertEquals(large.length, p.large.length);
        assertEquals(160, p.small.width);
        assertEquals(small.length, p.small.length);
        assertEquals(6, p.orientation);
        assertSame(p.large, p.pick(false));
        assertSame(p.small, p.pick(true));
    }

    @Test void bigEndianWorksToo() throws IOException {
        byte[] large = Fixtures.fakeJpeg(1616, 1080, 100), small = Fixtures.fakeJpeg(160, 120, 10);
        ArwPreview.Previews p = ArwPreview.locate(arw(Fixtures.sonyLikeArw(false, 8, large, small)));
        assertEquals(1616, p.large.width);
        assertEquals(160, p.small.width);
        assertEquals(8, p.orientation);
    }

    @Test void sizeIsDecidedByPixelsNotByWhichIfd() throws IOException {
        // swapped: IFD0 points at the thumbnail, IFD1 at the preview
        byte[] large = Fixtures.fakeJpeg(1616, 1080, 100), small = Fixtures.fakeJpeg(160, 120, 10);
        ArwPreview.Previews p = ArwPreview.locate(arw(Fixtures.sonyLikeArw(true, 1, small, large)));
        assertEquals(1616, p.large.width);
        assertEquals(160, p.small.width);
    }

    @Test void previewInSubIfdStripWithCompression6IsFound() throws IOException {
        byte[] large = Fixtures.fakeJpeg(1616, 1080, 100), small = Fixtures.fakeJpeg(160, 120, 10);
        List<int[][]> chain = new ArrayList<int[][]>();
        chain.add(new int[][] { Fixtures.e(0x0112, 3, 3), Fixtures.e(0x014a, 4, -1),
            Fixtures.e(0x0201, 4, -101), Fixtures.e(0x0202, 4, small.length) });
        List<int[][]> subs = new ArrayList<int[][]>();
        subs.add(new int[][] { Fixtures.e(0x0103, 3, 6), Fixtures.e(0x0111, 4, -100), Fixtures.e(0x0117, 4, large.length) });
        ArwPreview.Previews p = ArwPreview.locate(arw(Fixtures.tiff(true, chain, subs, Arrays.asList(large, small))));
        assertEquals(1616, p.large.width);
        assertEquals(large.length, p.large.length);
        assertEquals(160, p.small.width);
        assertEquals(3, p.orientation);
    }

    @Test void stripsWithRawCompressionAreIgnored() throws IOException {
        byte[] jpeg = Fixtures.fakeJpeg(1616, 1080, 100);
        List<int[][]> chain = new ArrayList<int[][]>();
        chain.add(new int[][] { Fixtures.e(0x0103, 3, 32767), Fixtures.e(0x0111, 4, -100), Fixtures.e(0x0117, 4, jpeg.length) });
        ArwPreview.Previews p = ArwPreview.locate(arw(Fixtures.tiff(true, chain, new ArrayList<int[][]>(), Arrays.asList(jpeg))));
        assertNull(p.pick(false));
    }

    @Test void aSingleJpegServesBothSizes() throws IOException {
        byte[] large = Fixtures.fakeJpeg(1616, 1080, 100);
        List<int[][]> chain = new ArrayList<int[][]>();
        chain.add(new int[][] { Fixtures.e(0x0201, 4, -100), Fixtures.e(0x0202, 4, large.length) });
        ArwPreview.Previews p = ArwPreview.locate(arw(Fixtures.tiff(true, chain, new ArrayList<int[][]>(), Arrays.asList(large))));
        assertSame(p.large, p.pick(true));
        assertEquals(1, p.orientation);
    }

    @Test void pointersOutsideTheFileOrNotAtAJpegAreRejected() throws IOException {
        byte[] notJpeg = new byte[200];
        List<int[][]> chain = new ArrayList<int[][]>();
        chain.add(new int[][] { Fixtures.e(0x0201, 4, -100), Fixtures.e(0x0202, 4, notJpeg.length) });
        chain.add(new int[][] { Fixtures.e(0x0201, 4, 100), Fixtures.e(0x0202, 4, 1 << 30) });
        ArwPreview.Previews p = ArwPreview.locate(arw(Fixtures.tiff(true, chain, new ArrayList<int[][]>(), Arrays.asList(notJpeg))));
        assertNull(p.pick(true));
        assertNull(p.pick(false));
    }

    @Test void orientationOutOfRangeFallsBackToOne() throws IOException {
        byte[] large = Fixtures.fakeJpeg(1616, 1080, 100), small = Fixtures.fakeJpeg(160, 120, 10);
        assertEquals(1, ArwPreview.locate(arw(Fixtures.sonyLikeArw(true, 42, large, small))).orientation);
    }

    @Test void garbageAndTruncatedFilesYieldNothingRatherThanThrowing() throws IOException {
        assertNull(ArwPreview.locate(arw("not a tiff at all".getBytes())).pick(false));
        assertNull(ArwPreview.locate(arw(new byte[0])).pick(false));
        byte[] full = Fixtures.sonyLikeArw(true, 1, Fixtures.fakeJpeg(1616, 1080, 100), Fixtures.fakeJpeg(160, 120, 10));
        assertNull(ArwPreview.locate(arw(Arrays.copyOf(full, 40))).pick(false));
    }

    @Test void selfReferencingIfdChainTerminates() throws IOException {
        byte[] b = new byte[64];
        b[0] = 'I'; b[1] = 'I'; b[2] = 42; b[4] = 8;
        b[8] = 1;                                   // one entry
        b[10] = 0x03; b[11] = 0x01; b[12] = 3; b[14] = 1; b[18] = 6;   // compression 6
        b[22] = 8;                                   // next IFD = itself
        assertNull(ArwPreview.locate(arw(b)).pick(false));
    }

    // ---- a camera JPEG: the same walk inside its APP1 Exif segment, offsets counted from the TIFF header

    private File jpg(byte[] data) throws IOException {
        return Fixtures.write(new File(dir, "x.JPG"), data);
    }

    private static byte[] at(File f, ArwPreview.Jpeg j) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            byte[] b = new byte[(int) j.length];
            raf.seek(j.offset);
            raf.readFully(b);
            return b;
        } finally {
            raf.close();
        }
    }

    @Test void jpegExifGivesIfd1ThumbnailAtItsFileOffsetAndIfd0Orientation() throws IOException {
        for (boolean little : new boolean[] { true, false }) {
            byte[] thumb = Fixtures.fakeJpeg(160, 120, 300);
            File f = jpg(Fixtures.exifJpeg(little, 6, thumb));
            ArwPreview.Previews p = ArwPreview.locateExif(f);
            assertNotNull(p.small, "little endian " + little);
            assertEquals(160, p.small.width);
            assertEquals(120, p.small.height);
            assertEquals(thumb.length, p.small.length);
            assertTrue(p.small.offset > 12 + 8, "a file offset, past SOI, the APP1 head and the TIFF header");
            assertArrayEquals(thumb, at(f, p.small), "the offset is the thumbnail's place in the file, not in the TIFF");
            assertEquals(6, p.orientation);
        }
    }

    @Test void jpegWithoutOrientationIsOne() throws IOException {
        ArwPreview.Previews p = ArwPreview.locateExif(jpg(Fixtures.exifJpeg(true, 0, Fixtures.fakeJpeg(160, 120, 10))));
        assertNotNull(p.small);
        assertEquals(1, p.orientation);
    }

    @Test void jpegWithoutAThumbnailHasOnlyItsOrientation() throws IOException {
        ArwPreview.Previews p = ArwPreview.locateExif(jpg(Fixtures.exifJpeg(true, 8, null)));
        assertNull(p.small);
        assertNull(p.large);
        assertEquals(8, p.orientation);
    }

    @Test void jpegWithoutExifOrNotAJpegYieldsNothing() throws IOException {
        ArwPreview.Previews plain = ArwPreview.locateExif(jpg(Fixtures.fakeJpeg(6000, 4000, 100)));
        assertNull(plain.small);
        assertEquals(1, plain.orientation);
        assertNull(ArwPreview.locateExif(jpg("not a jpeg".getBytes())).small);
        assertNull(ArwPreview.locateExif(jpg(new byte[0])).small);
        byte[] full = Fixtures.exifJpeg(true, 6, Fixtures.fakeJpeg(160, 120, 10));
        assertNull(ArwPreview.locateExif(jpg(Arrays.copyOf(full, 30))).small, "cut off inside the Exif segment");
    }

    @Test void aJpegThumbnailMustStayInsideTheExifSegment() throws IOException {
        byte[] thumb = Fixtures.fakeJpeg(160, 120, 100);
        // the declared length runs past the APP1 segment into the image data, though not past the file
        ArwPreview.Previews p = ArwPreview.locateExif(jpg(Fixtures.exifJpeg(true, 1, thumb, thumb.length + 1000, 6000, 4000)));
        assertNull(p.small);
    }

    @Test void anArwIsNotReadAsAJpegNorAJpegAsAnArw() throws IOException {
        byte[] arw = Fixtures.sonyLikeArw(true, 6, Fixtures.fakeJpeg(1616, 1080, 100), Fixtures.fakeJpeg(160, 120, 10));
        assertNull(ArwPreview.locateExif(arw(arw)).small, "an ARW starts with a TIFF header, not SOI");
        assertNull(ArwPreview.locate(jpg(Fixtures.exifJpeg(true, 6, Fixtures.fakeJpeg(160, 120, 10)))).pick(true), "a JPEG starts with SOI, not a TIFF header");
    }

    /** The real A6000 file: IFD0's JPEG is the 1616x1080 preview LibRaw extracts (it adds a 1,386-byte Exif header). */
    @Test void realA6000Sample() throws IOException {
        File f = Fixtures.realArw();
        assumeTrue(f != null, "set RECIPELAB_TEST_ARW to a real A6000 ARW to run this");
        ArwPreview.Previews p = ArwPreview.locate(f);
        assertNotNull(p.large);
        assertNotNull(p.small);
        assertEquals(1616, p.large.width);
        assertEquals(1080, p.large.height);
        assertEquals(1122206 - 1386, p.large.length);
        assertEquals(160, p.small.width);
        assertEquals(120, p.small.height);
        assertTrue(p.small.length < p.large.length);
        assertEquals(1, p.orientation);
        assertSoiAndEoi(f, p.large);
        assertSoiAndEoi(f, p.small);
    }

    private static void assertSoiAndEoi(File f, ArwPreview.Jpeg j) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            byte[] b = new byte[2];
            raf.seek(j.offset);
            raf.readFully(b);
            assertEquals(0xffd8, ((b[0] & 0xff) << 8) | (b[1] & 0xff), "SOI");
            raf.seek(j.offset + j.length - 2);
            raf.readFully(b);
            assertEquals(0xffd9, ((b[0] & 0xff) << 8) | (b[1] & 0xff), "EOI");
        } finally {
            raf.close();
        }
    }

    // ---- damaged files: whatever the bytes, nothing throws and nothing points outside the file

    private static void assertInside(File f, ArwPreview.Previews p, String what) {
        for (ArwPreview.Jpeg j : new ArwPreview.Jpeg[] { p.large, p.small }) {
            if (j == null) continue;
            assertTrue(j.offset >= 0 && j.length > 0 && j.offset + j.length <= f.length(), what + ": " + j.offset + "+" + j.length);
        }
        assertTrue(p.orientation >= 1 && p.orientation <= 8, what);
    }

    @Test void anArwCutShortAnywhereIsNeverAProblem() throws IOException {
        byte[] full = Fixtures.sonyLikeArw(true, 6, Fixtures.fakeJpeg(1616, 1080, 40), Fixtures.fakeJpeg(160, 120, 10));
        for (int n = 0; n <= full.length; n++) {
            File f = arw(Arrays.copyOf(full, n));
            assertInside(f, ArwPreview.locate(f), "cut at " + n);
        }
    }

    @Test void aDamagedArwIsNeverAProblem() throws IOException {
        byte[] full = Fixtures.sonyLikeArw(false, 6, Fixtures.fakeJpeg(1616, 1080, 10), Fixtures.fakeJpeg(160, 120, 10));
        for (int i = 0; i < full.length; i++) {
            for (int v : new int[] { 0x00, 0xFF, 0x01 }) {
                byte[] b = full.clone();
                b[i] = (byte) v;
                File f = arw(b);
                assertInside(f, ArwPreview.locate(f), "byte " + i + " = " + v);
            }
        }
    }

    @Test void aJpegCutShortAnywhereIsNeverAProblem() throws IOException {
        byte[] full = Fixtures.exifJpeg(true, 6, Fixtures.fakeJpeg(160, 120, 10), -1, 600, 400);
        for (int n = 0; n <= Math.min(full.length, 400); n++) {
            File f = jpg(Arrays.copyOf(full, n));
            assertInside(f, ArwPreview.locateExif(f), "cut at " + n);
        }
    }

    // ---- what is in front of a preview's size

    /** a preview: SOI, {@code before}, a frame header {@code sof} (0: none) of 1616 x 1080, some data, EOI */
    private static byte[] preview(int sof, int... before) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(0xff); o.write(0xd8);
        for (int x : before) o.write(x);
        if (sof != 0) {
            int[] h = { 0xff, sof, 0, 17, 8, 1080 >> 8, 1080 & 0xff, 1616 >> 8, 1616 & 0xff, 3 };
            for (int x : h) o.write(x);
            for (int i = 0; i < 9; i++) o.write(0);
        }
        for (int i = 0; i < 40; i++) o.write(i * 7 + 1);
        o.write(0xff); o.write(0xd9);
        return o.toByteArray();
    }

    private ArwPreview.Jpeg found(byte[] jpeg) throws IOException {
        List<int[][]> chain = new ArrayList<int[][]>();
        chain.add(new int[][] { Fixtures.e(0x0201, 4, -100), Fixtures.e(0x0202, 4, jpeg.length) });
        return ArwPreview.locate(arw(Fixtures.tiff(true, chain, new ArrayList<int[][]>(), Arrays.asList(jpeg)))).pick(false);
    }

    @Test void theSizeIsReadFromBaselineExtendedAndProgressiveFrames() throws IOException {
        for (int sof : new int[] { 0xc0, 0xc1, 0xc2 }) {
            ArwPreview.Jpeg j = found(preview(sof));
            assertNotNull(j, Integer.toHexString(sof));
            assertEquals(1616, j.width);
            assertEquals(1080, j.height);
        }
    }

    @Test void fillBytesRestartsAndTablesBeforeTheFrameAreSteppedOver() throws IOException {
        assertEquals(1616, found(preview(0xc0, 0xff)).width, "a fill byte");
        assertEquals(1616, found(preview(0xc0, 0xff, 0xd0)).width, "a restart marker");
        assertEquals(1616, found(preview(0xc0, 0xff, 0xc4, 0, 4, 0, 0)).width, "a Huffman table");
    }

    @Test void aPreviewThePhoneCouldNotShowIsNotOffered() throws IOException {
        assertNull(found(preview(0xc3)), "lossless");
        assertNull(found(preview(0xc9)), "arithmetic");
        assertNull(found(preview(0)), "no frame header at all");
        assertNull(found(preview(0xc0, 0xff, 0xda, 0, 2)), "image data before the frame header");
        assertNull(found(preview(0xc0, 0xff, 0xe0, 0, 1)), "a segment length under two");
        byte[] noWidth = preview(0xc0);
        noWidth[2 + 7] = 0; noWidth[2 + 8] = 0;
        assertNull(found(noWidth), "zero wide");
    }

    @Test void theExifSegmentIsFoundBehindFillBytesAndAnotherApp1() throws IOException {
        byte[] plain = Fixtures.exifJpeg(true, 6, Fixtures.fakeJpeg(160, 120, 10));
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(plain, 0, 2);
        o.write(0xff);                                                   // a fill byte
        byte[] xmp = { (byte) 0xff, (byte) 0xe1, 0, 8, 'h', 't', 't', 'p', ':', '/' };
        o.write(xmp, 0, xmp.length);
        o.write(plain, 2, plain.length - 2);
        File f = jpg(o.toByteArray());
        ArwPreview.Previews p = ArwPreview.locateExif(f);
        assertNotNull(p.small);
        assertEquals(160, p.small.width);
        assertEquals(6, p.orientation);
        assertSoiAndEoi(f, p.small);
    }

    @Test void aJpegSegmentLengthUnderTwoEndsTheSearch() throws IOException {
        byte[] b = Fixtures.exifJpeg(true, 6, Fixtures.fakeJpeg(160, 120, 10));
        b[4] = 0; b[5] = 1;                                              // the APP1's length
        assertNull(ArwPreview.locateExif(jpg(b)).small);
    }
}
