package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The camera's DCF names: the numbers the picture database files a photo under, and which photos are on the card. */
class DcfTest {
    @Test void aPhotoIsFiledUnderItsFolderAndFileNumbers() {
        assertArrayEquals(new int[] { 100, 1234 }, Dcf.numbers("/mnt/sdcard/DCIM/100MSDCF/DSC01234.JPG"));
        assertArrayEquals(new int[] { 999, 9999 }, Dcf.numbers("DCIM/999MSDCF/DSC09999.JPG"));
        assertArrayEquals(new int[] { 101, 7 }, Dcf.numbers("DCIM/101MSDCF/DSC00007.JPG"));
        assertArrayEquals(new int[] { 102, 1542 }, Dcf.numbers("DCIM/102_PANA/_DSC1542.ARW"), "only the digit places count");
    }

    @Test void aNameTheCameraWouldNotGiveHasNoNumbers() {
        assertNull(Dcf.numbers(null));
        assertNull(Dcf.numbers("DSC01234.JPG"), "no folder");
        assertNull(Dcf.numbers("DCIM/099MSDCF/DSC01234.JPG"), "folders start at 100");
        assertNull(Dcf.numbers("DCIM/1000MSDCF/DSC01234.JPG"), "the folder name is eight long");
        assertNull(Dcf.numbers("DCIM/10MSDCF/DSC01234.JPG"));
        assertNull(Dcf.numbers("DCIM/ABCMSDCF/DSC01234.JPG"));
        assertNull(Dcf.numbers("DCIM/100MSDCF/DSC0123.JPG"), "the file name is eight long");
        assertNull(Dcf.numbers("DCIM/100MSDCF/DSC012345.JPG"));
        assertNull(Dcf.numbers("DCIM/100MSDCF/DSC0A234.JPG"), "the last four are digits");
        assertNull(Dcf.numbers("DCIM/100MSDCF/DSC01234"), "no extension");
    }

    @Test void theJpegsAreThoseInTheFoldersUnderDcim(@TempDir File dcim) throws IOException {
        File a = touch(dcim, "100MSDCF/DSC00001.JPG"), b = touch(dcim, "100MSDCF/dsc00002.jpg"), c = touch(dcim, "101MSDCF/DSC00003.JPEG");
        touch(dcim, "100MSDCF/DSC00004.ARW");
        touch(dcim, "100MSDCF/DSC00005.MP4");
        touch(dcim, "LOOSE.JPG");                                          // DCIM itself holds no photos
        touch(dcim, "100MSDCF/SUB/DSC00006.JPG");                          // nor does a folder in a folder
        assertEquals(set(a.getPath(), b.getPath(), c.getPath()), new HashSet<String>(Dcf.jpegs(dcim)));
    }

    @Test void noDcimNoJpegs(@TempDir File dir) {
        assertTrue(Dcf.jpegs(null).isEmpty());
        assertTrue(Dcf.jpegs(new File(dir, "missing")).isEmpty());
        assertTrue(Dcf.jpegs(dir).isEmpty());
    }

    private static File touch(File dir, String rel) throws IOException {
        File f = new File(dir, rel);
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
        return f;
    }

    private static Set<String> set(String... s) { return new HashSet<String>(Arrays.asList(s)); }
}
