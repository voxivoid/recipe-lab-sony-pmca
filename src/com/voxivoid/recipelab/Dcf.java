package com.voxivoid.recipelab;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The camera's DCF folders (DCIM/100MSDCF/DSC01234.JPG): which photos are there, and the numbers each is filed under. */
final class Dcf {
    private Dcf() {}

    /** every JPEG in the folders under DCIM, by path: one level down, as the camera files them */
    static List<String> jpegs(File dcim) {
        List<String> out = new ArrayList<String>();
        File[] folders = dcim == null ? null : dcim.listFiles();
        if (folders == null) return out;
        for (File f : folders) {
            if (!f.isDirectory()) continue;
            String[] names = f.list();
            if (names == null) continue;
            for (String n : names) {
                String u = n.toUpperCase(Locale.US);
                if (u.endsWith(".JPG") || u.endsWith(".JPEG")) out.add(new File(f, n).getPath());
            }
        }
        return out;
    }

    /**
     * A DCF photo's numbers, as the camera's picture database keys it: DCIM/100MSDCF/DSC01234.JPG → { 100, 1234 }, the
     * folder's first three digits and the name's last four; null for a path that is not named so.
     */
    static int[] numbers(String path) {
        if (path == null) return null;
        File f = new File(path), dir = f.getParentFile();
        String name = f.getName(), folder = dir == null ? "" : dir.getName();
        int dot = name.lastIndexOf('.');
        if (dot != 8 || folder.length() != 8) return null;
        String fd = folder.substring(0, 3), nd = name.substring(4, 8);
        if (!fd.matches("[1-9][0-9]{2}") || !nd.matches("[0-9]{4}")) return null;
        return new int[] { Integer.parseInt(fd), Integer.parseInt(nd) };
    }
}
