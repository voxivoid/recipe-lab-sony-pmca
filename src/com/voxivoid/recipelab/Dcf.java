package com.voxivoid.recipelab;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    /**
     * The numbers of the newest photo under DCIM, any kind (RAW, JPEG …), as {@link #numbers} gives them: the highest
     * folder, then the highest file in it; null when there is none. The camera numbers each new shot after it. Folders
     * are looked in from the highest down, stopping at the first that holds one, so leaving the app lists one folder.
     */
    static int[] newest(File dcim) {
        File[] all = dcim == null ? null : dcim.listFiles();
        if (all == null) return null;
        List<File> folders = new ArrayList<File>();
        for (File f : all) if (f.isDirectory() && folder(f.getName()) > 0) folders.add(f);
        Collections.sort(folders, new Comparator<File>() {
            @Override public int compare(File a, File b) { return folder(b.getName()) - folder(a.getName()); }
        });
        int[] best = null;
        for (File f : folders) {
            if (best != null && folder(f.getName()) < best[0]) break;
            String[] names = f.list();
            if (names == null) continue;
            for (String n : names) {
                int[] k = numbers(new File(f, n).getPath());
                if (k != null && (best == null || compare(k, best) > 0)) best = k;
            }
        }
        return best;
    }

    /** a DCF folder name's number (100MSDCF → 100); 0 for a name that is not one */
    private static int folder(String name) {
        if (name.length() != 8 || !name.substring(0, 3).matches("[1-9][0-9]{2}")) return 0;
        return Integer.parseInt(name.substring(0, 3));
    }

    /** orders two {@link #numbers}: by folder, then by file */
    static int compare(int[] a, int[] b) { return a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]; }
}
