package com.voxivoid.recipelab.transfer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Synthetic ARWs (a TIFF skeleton around fake JPEGs), camera-like JPEGs with Exif, and the real-sample lookup. */
final class Fixtures {
    private Fixtures() {}

    /** The real A6000 sample named by RECIPELAB_TEST_ARW, or null when unset / missing (tests then skip). */
    static File realArw() {
        String p = System.getenv("RECIPELAB_TEST_ARW");
        if (p == null || p.isEmpty()) return null;
        File f = new File(p);
        return f.isFile() ? f : null;
    }

    /** A minimal JPEG-shaped blob: SOI, APP0, SOF0 with the given size, filler, EOI. */
    static byte[] fakeJpeg(int width, int height, int filler) {
        ByteBuffer b = ByteBuffer.allocate(2 + 18 + 19 + filler + 2).order(ByteOrder.BIG_ENDIAN);
        b.put((byte) 0xff).put((byte) 0xd8);
        b.put((byte) 0xff).put((byte) 0xe0).putShort((short) 16).put("JFIF\0".getBytes()).put(new byte[9]);
        b.put((byte) 0xff).put((byte) 0xc0).putShort((short) 17).put((byte) 8)
         .putShort((short) height).putShort((short) width).put((byte) 3).put(new byte[9]);
        for (int i = 0; i < filler; i++) b.put((byte) (i * 7 + 1));   // neighbours differ by 7, so never an 0xff 0xd9 pair
        b.put((byte) 0xff).put((byte) 0xd9);
        return b.array();
    }

    /** One IFD entry: tag, TIFF type (3 SHORT, 4 LONG), single value. */
    static int[] e(int tag, int type, long value) {
        return new int[] { tag, type, (int) value };
    }

    /**
     * Lays out a TIFF: header, the IFD chain {@code chain}, the SubIFDs {@code subs} (referenced from the chain by
     * the placeholder value {@code -1-k} in a 0x014a entry), then the blobs. Values {@code -100-k} are replaced by
     * blob k's offset. Returns the file bytes.
     */
    static byte[] tiff(boolean little, List<int[][]> chain, List<int[][]> subs, List<byte[]> blobs) {
        List<int[][]> all = new ArrayList<int[][]>(chain);
        all.addAll(subs);
        int[] ifdOff = new int[all.size()];
        int pos = 8;
        for (int i = 0; i < all.size(); i++) { ifdOff[i] = pos; pos += 2 + 12 * all.get(i).length + 4; }
        int[] blobOff = new int[blobs.size()];
        for (int i = 0; i < blobs.size(); i++) { blobOff[i] = pos; pos += blobs.get(i).length; }
        pos += 64;                                                      // trailing "raw data"
        ByteBuffer b = ByteBuffer.allocate(pos).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        b.put(little ? (byte) 'I' : (byte) 'M').put(little ? (byte) 'I' : (byte) 'M').putShort((short) 42).putInt(8);
        for (int i = 0; i < all.size(); i++) {
            b.position(ifdOff[i]);
            int[][] entries = all.get(i);
            b.putShort((short) entries.length);
            for (int[] en : entries) {
                int v = en[2];
                if (v <= -100) v = blobOff[-100 - v];
                else if (v < 0) v = ifdOff[chain.size() + (-1 - v)];
                b.putShort((short) en[0]).putShort((short) en[1]).putInt(1);
                if (en[1] == 3) b.putShort((short) v).putShort((short) 0); else b.putInt(v);
            }
            boolean inChain = i < chain.size() - 1;
            b.putInt(inChain ? ifdOff[i + 1] : 0);
        }
        for (int i = 0; i < blobs.size(); i++) { b.position(blobOff[i]); b.put(blobs.get(i)); }
        return b.array();
    }

    /** The A6000 layout: IFD0 -> large preview (0x0201/0x0202), IFD1 -> small thumbnail, plus a raw SubIFD. */
    static byte[] sonyLikeArw(boolean little, int orientation, byte[] large, byte[] small) {
        List<int[][]> chain = new ArrayList<int[][]>();
        chain.add(new int[][] {
            e(0x0103, 3, 6), e(0x0112, 3, orientation), e(0x014a, 4, -1),
            e(0x0201, 4, -100), e(0x0202, 4, large.length) });
        chain.add(new int[][] { e(0x0103, 3, 6), e(0x0201, 4, -101), e(0x0202, 4, small.length) });
        List<int[][]> subs = new ArrayList<int[][]>();
        subs.add(new int[][] { e(0x0103, 3, 32767), e(0x0111, 4, -102), e(0x0117, 4, 64) });
        List<byte[]> blobs = new ArrayList<byte[]>();
        blobs.add(large);
        blobs.add(small);
        blobs.add(new byte[64]);
        return tiff(little, chain, subs, blobs);
    }

    /**
     * A camera-like JPEG: SOI, then an APP1 "Exif\0\0" segment holding a TIFF (built by {@link #tiff}, so its offsets
     * count from the TIFF header, not the file) with the orientation in IFD0 ({@code orientation <= 0} leaves the tag
     * out) and {@code thumb} as IFD1's 0x0201/0x0202 thumbnail ({@code null}: no IFD1), then the image itself — a
     * {@link #fakeJpeg} of {@code width} x {@code height} without its SOI. {@code thumbLength} overrides the length
     * IFD1 declares (-1: the thumbnail's own).
     */
    static byte[] exifJpeg(boolean little, int orientation, byte[] thumb, long thumbLength, int width, int height) {
        List<int[][]> chain = new ArrayList<int[][]>();
        chain.add(orientation > 0 ? new int[][] { e(0x0112, 3, orientation) } : new int[][] { e(0x0100, 4, width) });
        List<byte[]> blobs = new ArrayList<byte[]>();
        if (thumb != null) {
            chain.add(new int[][] { e(0x0103, 3, 6), e(0x0201, 4, -100), e(0x0202, 4, thumbLength >= 0 ? thumbLength : thumb.length) });
            blobs.add(thumb);
        }
        byte[] tiff = tiff(little, chain, new ArrayList<int[][]>(), blobs);
        byte[] image = fakeJpeg(width, height, 3000);
        ByteBuffer b = ByteBuffer.allocate(2 + 4 + 6 + tiff.length + image.length - 2).order(ByteOrder.BIG_ENDIAN);
        b.put((byte) 0xff).put((byte) 0xd8);
        b.put((byte) 0xff).put((byte) 0xe1).putShort((short) (2 + 6 + tiff.length)).put("Exif\0\0".getBytes()).put(tiff);
        b.put(Arrays.copyOfRange(image, 2, image.length));
        return b.array();
    }

    /** {@link #exifJpeg} with the thumbnail's own length. */
    static byte[] exifJpeg(boolean little, int orientation, byte[] thumb) {
        return exifJpeg(little, orientation, thumb, -1, 6000, 4000);
    }

    static File write(File f, byte[] data) throws IOException {
        f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        try { out.write(data); } finally { out.close(); }
        return f;
    }

    static File tempDir() throws IOException {
        return Files.createTempDirectory("recipelab-transfer").toFile();
    }

    static void deleteTree(File f) {
        File[] kids = Files.isSymbolicLink(f.toPath()) ? null : f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
