package com.voxivoid.recipelab;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * Save image's picture is encoded by the camera from one of its photos, and the camera keeps that photo's
 * DateTimeOriginal and DateTimeDigitized: playback then files the card under the day that photo was taken. Its
 * DateTime is the camera's clock when it saved, so both are set to it, in place (the same 19 characters).
 */
final class ExifDates {
    private ExifDates() {}

    /** how far into the file the EXIF block is looked for */
    static final int HEAD = 64 * 1024;

    private static final int DATE_TIME = 0x0132, EXIF_IFD = 0x8769, ORIGINAL = 0x9003, DIGITIZED = 0x9004, ASCII = 2, LENGTH = 20;

    /**
     * Sets {@code jpeg}'s DateTimeOriginal and DateTimeDigitized to its DateTime. Returns what it did, for the log:
     * "dates: was …" when it changed them, "dates: already …" when they matched, or why it could not.
     */
    static String align(byte[] jpeg) {
        int tiff = tiffStart(jpeg);
        if (tiff < 0) return "dates: no EXIF";
        boolean le = jpeg[tiff] == 'I';
        int ifd0 = tiff + u32(jpeg, tiff + 4, le);
        int now = stringAt(jpeg, tiff, ifd0, DATE_TIME, le);
        int exifTag = entry(jpeg, ifd0, EXIF_IFD, le);
        if (now < 0 || exifTag < 0) return "dates: no DateTime";
        int exif = tiff + u32(jpeg, exifTag + 8, le);
        int[] targets = { stringAt(jpeg, tiff, exif, ORIGINAL, le), stringAt(jpeg, tiff, exif, DIGITIZED, le) };
        String was = null;
        for (int at : targets) {
            if (at < 0) continue;
            String old = new String(jpeg, at, LENGTH - 1);
            if (!old.equals(new String(jpeg, now, LENGTH - 1))) {
                if (was == null) was = old;
                System.arraycopy(jpeg, now, jpeg, at, LENGTH - 1);
            }
        }
        String date = new String(jpeg, now, LENGTH - 1);
        return was == null ? "dates: already " + date : "dates: was " + was + ", now " + date;
    }

    /** {@link #align} on a file's first {@link #HEAD} bytes, written back only when they changed */
    static String align(File f) {
        if (!f.isFile()) return "dates: no file " + f.getName();
        RandomAccessFile io = null;
        try {
            io = new RandomAccessFile(f, "rw");
            byte[] head = new byte[(int) Math.min(HEAD, io.length())];
            io.readFully(head);
            String said = align(head);
            if (said.startsWith("dates: was ")) { io.seek(0); io.write(head); }
            return said;
        } catch (Exception e) {
            return "dates: " + e;
        } finally {
            if (io != null) try { io.close(); } catch (Exception ignored) {}
        }
    }

    /** where the TIFF header of the first APP1 Exif segment starts, or -1 */
    static int tiffStart(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) return -1;
        int p = 2;
        while (p + 4 <= b.length && (b[p] & 0xFF) == 0xFF) {
            int marker = b[p + 1] & 0xFF, len = (b[p + 2] & 0xFF) << 8 | (b[p + 3] & 0xFF);
            if (marker == 0xDA || len < 2) return -1;
            if (marker == 0xE1 && p + 10 + 8 <= b.length && b[p + 4] == 'E' && b[p + 5] == 'x' && b[p + 6] == 'i'
                    && b[p + 7] == 'f' && b[p + 8] == 0 && b[p + 9] == 0) {
                int t = p + 10;
                boolean ok = (b[t] == 'I' && b[t + 1] == 'I') || (b[t] == 'M' && b[t + 1] == 'M');
                return ok ? t : -1;
            }
            p += 2 + len;
        }
        return -1;
    }

    /** the offset of {@code tag}'s 12-byte entry in the IFD at {@code ifd}, or -1 */
    private static int entry(byte[] b, int ifd, int tag, boolean le) {
        if (ifd < 0 || ifd + 2 > b.length) return -1;
        int n = u16(b, ifd, le);
        for (int k = 0; k < n; k++) {
            int e = ifd + 2 + 12 * k;
            if (e + 12 > b.length) return -1;
            if (u16(b, e, le) == tag) return e;
        }
        return -1;
    }

    /** where {@code tag}'s 20-byte date string is, or -1 when it is missing or not one */
    private static int stringAt(byte[] b, int tiff, int ifd, int tag, boolean le) {
        int e = entry(b, ifd, tag, le);
        if (e < 0 || u16(b, e + 2, le) != ASCII || u32(b, e + 4, le) != LENGTH) return -1;
        int at = tiff + u32(b, e + 8, le);
        return at >= 0 && at + LENGTH <= b.length ? at : -1;
    }

    private static int u16(byte[] b, int p, boolean le) {
        int x = b[p] & 0xFF, y = b[p + 1] & 0xFF;
        return le ? x | y << 8 : x << 8 | y;
    }

    private static int u32(byte[] b, int p, boolean le) {
        return le ? u16(b, p, true) | u16(b, p + 2, true) << 16 : u16(b, p, false) << 16 | u16(b, p + 2, false);
    }
}
