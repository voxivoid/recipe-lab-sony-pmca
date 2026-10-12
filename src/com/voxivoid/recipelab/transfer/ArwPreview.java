package com.voxivoid.recipelab.transfer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the JPEGs embedded in an ARW by walking its TIFF structure; nothing is decoded and the file is never read
 * whole. Every IFD in the main chain and their SubIFDs is searched for either JPEGInterchangeFormat/Length
 * (0x0201/0x0202) or a single strip (0x0111/0x0117) with Compression 6/7. A candidate counts only if it lies inside
 * the file, starts with SOI and has a baseline/progressive SOF (which rules out lossless-JPEG raw data); the
 * largest by pixel count is the "large" preview and the smallest the "small" thumbnail.
 *
 * <p>On the A6000, IFD0 holds the 1616x1080 preview (1,120,820 bytes in the test sample; LibRaw reports 1,386
 * bytes more because it splices an Exif header in) and IFD1 the 160x120 thumbnail.
 *
 * <p>A camera JPEG carries the same structure inside its APP1 "Exif" segment: a TIFF whose offsets count from the
 * TIFF header, not from the start of the file, with the orientation in IFD0 and the 160x120 thumbnail in IFD1.
 * {@link #locateExif} finds that segment and runs the same walk with the TIFF's base offset; every offset found is
 * turned into a file offset, so a caller streams it the same way.
 */
public final class ArwPreview {
    private static final int MAX_IFDS = 16, MAX_ENTRIES = 1024, MAX_SUBIFDS = 8, MAX_DEPTH = 3;
    /** How far into a JPEG to look for its SOF marker. */
    private static final int SOF_SCAN_LIMIT = 256 * 1024;

    private static final int TAG_COMPRESSION = 0x0103, TAG_STRIP_OFFSETS = 0x0111, TAG_ORIENTATION = 0x0112,
            TAG_STRIP_BYTE_COUNTS = 0x0117, TAG_SUBIFDS = 0x014a, TAG_JPEG_OFFSET = 0x0201, TAG_JPEG_LENGTH = 0x0202;

    /** One embedded JPEG: where it is and how big its image is. */
    public static final class Jpeg {
        public final long offset, length;
        public final int width, height;

        Jpeg(long offset, long length, int width, int height) {
            this.offset = offset;
            this.length = length;
            this.width = width;
            this.height = height;
        }

        long pixels() {
            return (long) width * height;
        }
    }

    /** What an ARW offers. {@code large} and {@code small} are the same object when there is only one JPEG. */
    public static final class Previews {
        public final Jpeg large, small;
        /** TIFF Orientation from IFD0, 1-8 (1 when absent or out of range). */
        public final int orientation;

        Previews(Jpeg large, Jpeg small, int orientation) {
            this.large = large;
            this.small = small;
            this.orientation = orientation;
        }

        /** The requested size, falling back to the other one; null if the file has no usable JPEG. */
        public Jpeg pick(boolean wantSmall) {
            Jpeg first = wantSmall ? small : large, second = wantSmall ? large : small;
            return first != null ? first : second;
        }
    }

    private ArwPreview() {}

    /** How many JPEG segments {@link #locateExif} looks through for the Exif one before giving up. */
    private static final int MAX_SEGMENTS = 32;

    /** Locates the previews in {@code file}. A file that is not a TIFF yields empty previews, not an exception. */
    public static Previews locate(File file) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            return new Walker(raf, 0, raf.length()).run();
        } finally {
            raf.close();
        }
    }

    /**
     * Locates the Exif thumbnail and orientation of a JPEG: the TIFF inside its APP1 "Exif\0\0" segment, walked
     * with that TIFF's base offset and kept inside the segment. A JPEG without Exif (or not a JPEG at all) yields
     * empty previews with orientation 1.
     */
    public static Previews locateExif(File file) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            long[] tiff = exifTiff(raf);
            if (tiff == null) return new Previews(null, null, 1);
            return new Walker(raf, tiff[0], tiff[1]).run();
        } finally {
            raf.close();
        }
    }

    /** {start, end} of the TIFF in the first APP1 Exif segment before SOS, or null. */
    private static long[] exifTiff(RandomAccessFile raf) throws IOException {
        long length = raf.length();
        byte[] m = new byte[10];
        if (length < 4) return null;
        raf.seek(0);
        raf.readFully(m, 0, 2);
        if ((m[0] & 0xff) != 0xff || (m[1] & 0xff) != 0xd8) return null;
        long p = 2;
        for (int i = 0; i < MAX_SEGMENTS && p + 4 <= length; i++) {
            raf.seek(p);
            raf.readFully(m, 0, 4);
            if ((m[0] & 0xff) != 0xff) return null;
            int marker = m[1] & 0xff;
            if (marker == 0xff) { p++; continue; }                // fill byte
            if (marker == 0xd8 || (marker >= 0xd0 && marker <= 0xd7) || marker == 0x01) { p += 2; continue; }
            if (marker == 0xda || marker == 0xd9) return null;    // image data: no Exif before it
            int segLen = ((m[2] & 0xff) << 8) | (m[3] & 0xff);
            if (segLen < 2) return null;
            long end = Math.min(p + 2 + segLen, length);
            if (marker == 0xe1 && segLen >= 2 + 6 + 8 && p + 10 <= length) {
                raf.readFully(m, 4, 6);
                if (m[4] == 'E' && m[5] == 'x' && m[6] == 'i' && m[7] == 'f' && m[8] == 0 && m[9] == 0) return new long[] { p + 10, end };
            }
            p = p + 2 + segLen;
        }
        return null;
    }

    /**
     * One walk of one TIFF. Offsets inside the TIFF count from {@code base} (0 for an ARW, the Exif TIFF header in a
     * JPEG); {@link #readAt} takes them that way, and nothing is read at or past {@code end}.
     */
    private static final class Walker {
        private final RandomAccessFile raf;
        private final long base, end;
        private boolean little;
        private final Set<Long> visited = new HashSet<Long>();
        private final List<Jpeg> found = new ArrayList<Jpeg>();
        private final Set<Long> foundOffsets = new HashSet<Long>();
        private int orientation = 1;

        Walker(RandomAccessFile raf, long base, long end) {
            this.raf = raf;
            this.base = base;
            this.end = end;
        }

        Previews run() throws IOException {
            byte[] h = new byte[8];
            if (!readAt(0, h)) return new Previews(null, null, 1);
            if (h[0] == 'I' && h[1] == 'I') little = true;
            else if (h[0] == 'M' && h[1] == 'M') little = false;
            else return new Previews(null, null, 1);
            if (u16(h, 2) != 42) return new Previews(null, null, 1);
            long ifd = u32(h, 4);
            for (int i = 0; i < MAX_IFDS && ifd != 0; i++) ifd = readIfd(ifd, 0, i == 0);

            Jpeg large = null, small = null;
            for (int i = 0; i < found.size(); i++) {
                Jpeg j = found.get(i);
                if (large == null || j.pixels() > large.pixels() || (j.pixels() == large.pixels() && j.length > large.length)) large = j;
                if (small == null || j.pixels() < small.pixels() || (j.pixels() == small.pixels() && j.length < small.length)) small = j;
            }
            return new Previews(large, small, orientation);
        }

        /** Reads the IFD at {@code off}, records candidates, recurses into SubIFDs; returns the next-IFD offset. */
        private long readIfd(long off, int depth, boolean isIfd0) throws IOException {
            if (off < 8 || base + off + 2 > end || !visited.add(off)) return 0;
            byte[] nb = new byte[2];
            if (!readAt(off, nb)) return 0;
            int n = u16(nb, 0);
            if (n == 0 || n > MAX_ENTRIES) return 0;
            byte[] e = new byte[n * 12 + 4];
            if (!readAt(off + 2, e)) return 0;

            long compression = -1, stripOffset = -1, stripLength = -1, jpegOffset = -1, jpegLength = -1;
            long[] subIfds = null;
            for (int i = 0; i < n; i++) {
                int p = i * 12;
                int tag = u16(e, p), type = u16(e, p + 2);
                long count = u32(e, p + 4);
                switch (tag) {
                    case TAG_COMPRESSION: compression = scalar(e, p, type, count); break;
                    case TAG_STRIP_OFFSETS: stripOffset = count == 1 ? scalar(e, p, type, count) : -1; break;
                    case TAG_STRIP_BYTE_COUNTS: stripLength = count == 1 ? scalar(e, p, type, count) : -1; break;
                    case TAG_JPEG_OFFSET: jpegOffset = scalar(e, p, type, count); break;
                    case TAG_JPEG_LENGTH: jpegLength = scalar(e, p, type, count); break;
                    case TAG_ORIENTATION:
                        if (isIfd0) {
                            long o = scalar(e, p, type, count);
                            if (o >= 1 && o <= 8) orientation = (int) o;
                        }
                        break;
                    case TAG_SUBIFDS: subIfds = offsets(e, p, type, count); break;
                    default: break;
                }
            }
            if (jpegOffset > 0 && jpegLength > 0) candidate(jpegOffset, jpegLength);
            if ((compression == 6 || compression == 7) && stripOffset > 0 && stripLength > 0) candidate(stripOffset, stripLength);
            if (subIfds != null && depth < MAX_DEPTH) {
                for (int i = 0; i < subIfds.length; i++) {
                    // a SubIFD's own chain is followed too, bounded like the main one
                    long sub = subIfds[i];
                    for (int k = 0; k < MAX_IFDS && sub != 0; k++) sub = readIfd(sub, depth + 1, false);
                }
            }
            return u32(e, n * 12);
        }

        /** The first value of a SHORT or LONG entry, or -1. */
        private long scalar(byte[] e, int p, int type, long count) {
            if (count < 1) return -1;
            boolean inline;
            if (type == 3) inline = count <= 2;                     // SHORT
            else if (type == 4 || type == 13) inline = count == 1;  // LONG / IFD
            else return -1;
            if (inline) return type == 3 ? u16(e, p + 8) : u32(e, p + 8);   // left-justified in the value field
            byte[] b = new byte[4];
            try {
                if (!readAt(u32(e, p + 8), b)) return -1;
            } catch (IOException ex) {
                return -1;
            }
            return type == 3 ? u16(b, 0) : u32(b, 0);
        }

        private long[] offsets(byte[] e, int p, int type, long count) throws IOException {
            if ((type != 4 && type != 13) || count < 1) return null;
            int n = (int) Math.min(count, MAX_SUBIFDS);
            if (count == 1) return new long[] { u32(e, p + 8) };
            byte[] b = new byte[n * 4];
            if (!readAt(u32(e, p + 8), b)) return null;
            long[] out = new long[n];
            for (int i = 0; i < n; i++) out[i] = u32(b, i * 4);
            return out;
        }

        /** A JPEG at TIFF offset {@code offset}; recorded with its file offset. */
        private void candidate(long offset, long length) throws IOException {
            if (length < 4 || base + offset + length > end || foundOffsets.contains(offset)) return;
            byte[] soi = new byte[2];
            if (!readAt(offset, soi) || (soi[0] & 0xff) != 0xff || (soi[1] & 0xff) != 0xd8) return;
            int[] dims = jpegSize(offset, length);
            if (dims == null) return;
            foundOffsets.add(offset);
            found.add(new Jpeg(base + offset, length, dims[0], dims[1]));
        }

        /** {width, height} from the first SOF0/1/2 marker, or null if there is none before SOS. TIFF offsets. */
        private int[] jpegSize(long offset, long length) throws IOException {
            long stop = offset + Math.min(length, SOF_SCAN_LIMIT);
            long p = offset + 2;
            byte[] m = new byte[9];
            while (p + 4 <= stop) {
                if (!readAt(p, m, 4)) return null;
                if ((m[0] & 0xff) != 0xff) return null;
                int marker = m[1] & 0xff;
                if (marker == 0xff) { p++; continue; }          // fill byte
                if (marker == 0xd8 || (marker >= 0xd0 && marker <= 0xd7) || marker == 0x01) { p += 2; continue; }
                if (marker == 0xda || marker == 0xd9) return null;
                int segLen = ((m[2] & 0xff) << 8) | (m[3] & 0xff);
                if (segLen < 2) return null;
                if (marker == 0xc0 || marker == 0xc1 || marker == 0xc2) {
                    if (p + 9 > stop || !readAt(p, m, 9)) return null;
                    int h = ((m[5] & 0xff) << 8) | (m[6] & 0xff), w = ((m[7] & 0xff) << 8) | (m[8] & 0xff);
                    return w > 0 && h > 0 ? new int[] { w, h } : null;
                }
                if (marker >= 0xc3 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc) return null;
                p += 2 + segLen;
            }
            return null;
        }

        private boolean readAt(long off, byte[] b) throws IOException {
            return readAt(off, b, b.length);
        }

        /** Reads {@code len} bytes at TIFF offset {@code off}; false if they are not all inside the TIFF's bounds. */
        private boolean readAt(long off, byte[] b, int len) throws IOException {
            if (off < 0 || base + off + len > end) return false;
            raf.seek(base + off);
            raf.readFully(b, 0, len);
            return true;
        }

        private int u16(byte[] b, int i) {
            int x = b[i] & 0xff, y = b[i + 1] & 0xff;
            return little ? x | (y << 8) : (x << 8) | y;
        }

        private long u32(byte[] b, int i) {
            long a = b[i] & 0xff, c = b[i + 1] & 0xff, d = b[i + 2] & 0xff, f = b[i + 3] & 0xff;
            return little ? a | (c << 8) | (d << 16) | (f << 24) : (a << 24) | (d << 8) | (c << 16) | f;
        }
    }
}
