package com.voxivoid.recipelab.transfer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every photo on the card, ARW and JPEG, for {@code GET /api/files}, and the custom recipe files in RECIPES for
 * {@code GET /api/recipes}. The card is scanned afresh on each call.
 */
public final class FileIndex {
    /** Deeper than any DCF layout; stops a runaway walk. */
    private static final int MAX_DEPTH = 8;
    /** Recipe Lab's custom recipe folder at the card root (CustomRecipes.DIR); never listed as photos. */
    public static final String RECIPES = "RECIPES";
    /** The two kinds of photo, as {@code type} says them. */
    public static final String TYPE_ARW = "arw", TYPE_JPG = "jpg";

    public static final class Entry {
        /** Relative to the storage root, '/' separated. */
        public final String path;
        public final long size;
        /** Epoch milliseconds. */
        public final long modified;
        /** {@link #TYPE_ARW} or {@link #TYPE_JPG}; null for a recipe file. */
        public final String type;

        public Entry(String path, long size, long modified, String type) {
            this.path = path;
            this.size = size;
            this.modified = modified;
            this.type = type;
        }
    }

    /** Newest first; equal times fall back to the path, descending (DSC01543 before DSC01542). */
    public static final Comparator<Entry> NEWEST_FIRST = new Comparator<Entry>() {
        @Override public int compare(Entry a, Entry b) {
            if (a.modified != b.modified) return a.modified > b.modified ? -1 : 1;
            return b.path.compareTo(a.path);
        }
    };

    private FileIndex() {}

    public static boolean isArw(String name) {
        return hasExtension(name, ".arw");
    }

    /** {@code .JPG} or {@code .JPEG}, any case. */
    public static boolean isJpeg(String name) {
        return hasExtension(name, ".jpg") || hasExtension(name, ".jpeg");
    }

    /** A custom recipe file: {@code .YML}, any case. */
    public static boolean isRecipe(String name) {
        return hasExtension(name, ".yml");
    }

    /** {@code name} ends in {@code ext} (lower case, with the dot), has a name before it, and is not a dot-file. */
    private static boolean hasExtension(String name, String ext) {
        return name.length() > ext.length() && name.toLowerCase(Locale.US).endsWith(ext) && !name.startsWith(".");
    }

    /** {@link #TYPE_ARW}, {@link #TYPE_JPG}, or null for anything that is not a photo. */
    public static String type(String name) {
        return isArw(name) ? TYPE_ARW : isJpeg(name) ? TYPE_JPG : null;
    }

    /**
     * Sony's database and movie trees, dot-directories (.Trashes, .Spotlight-V100 ...), and Recipe Lab's RECIPES
     * folder, whose files are not photos and have their own listing.
     */
    static boolean skipDir(String name) {
        String n = name.toUpperCase(Locale.US);
        return n.equals("PRIVATE") || n.equals("AVF_INFO") || n.equals(RECIPES) || name.startsWith(".");
    }

    /** Every ARW and JPEG on the card, newest first. */
    public static List<Entry> scan(StorageRoot root) {
        List<Entry> out = new ArrayList<Entry>();
        Set<String> seen = new HashSet<String>();
        seen.add(root.dir().getPath());
        walk(root, root.dir(), "", 0, out, seen);
        Collections.sort(out, NEWEST_FIRST);
        return out;
    }

    private static void walk(StorageRoot root, File dir, String rel, int depth, List<Entry> out, Set<String> seen) {
        String[] names = dir.list();
        if (names == null) return;
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            File f = new File(dir, name);
            String childRel = rel.length() == 0 ? name : rel + "/" + name;
            if (f.isDirectory()) {
                if (depth + 1 >= MAX_DEPTH || skipDir(name)) continue;
                File canonical = canonical(f);
                if (canonical == null || !root.contains(canonical) || !seen.add(canonical.getPath())) continue;
                walk(root, f, childRel, depth + 1, out, seen);
            } else if (type(name) != null && f.isFile()) {
                File canonical = canonical(f);
                if (canonical == null || !root.contains(canonical)) continue;
                out.add(new Entry(childRel, f.length(), f.lastModified(), type(name)));
            }
        }
    }

    private static File canonical(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The custom recipe files: every {@code *.YML} (any case) directly inside the RECIPES folder (any case) at the
     * card root, by path. Each goes through {@link StorageRoot#resolve}, so a folder or file that links out of the
     * card is left out. No folder is an empty list.
     */
    public static List<Entry> recipes(StorageRoot root) {
        List<Entry> out = new ArrayList<Entry>();
        String[] top = root.dir().list();
        if (top == null) return out;
        for (int i = 0; i < top.length; i++) {
            if (!top[i].equalsIgnoreCase(RECIPES)) continue;
            String[] names = new File(root.dir(), top[i]).list();
            if (names == null) continue;
            for (int k = 0; k < names.length; k++) {
                if (!isRecipe(names[k])) continue;
                String rel = top[i] + "/" + names[k];
                File f;
                try {
                    f = root.resolve(rel);
                } catch (HttpException e) {
                    continue;                                   // escapes the card, or a name the paths refuse
                }
                if (f.isFile()) out.add(new Entry(rel, f.length(), f.lastModified(), null));
            }
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.path.compareTo(b.path); }
        });
        return out;
    }

    /** {@code {"files":[{"path":…,"size":…,"modified":…,"type":…}, …]}}; an entry without a type has no type key. */
    public static String toJson(List<Entry> entries) {
        StringBuilder sb = new StringBuilder(32 + entries.size() * 100);
        sb.append("{\"files\":[");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"path\":");
            Json.quote(sb, e.path);
            sb.append(",\"size\":").append(e.size).append(",\"modified\":").append(e.modified);
            if (e.type != null) {
                sb.append(",\"type\":");
                Json.quote(sb, e.type);
            }
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }
}
