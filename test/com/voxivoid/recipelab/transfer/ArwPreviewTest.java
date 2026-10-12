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
}
