package com.voxivoid.recipelab.transfer;

import java.io.File;
import java.io.IOException;

/**
 * The memory card root and the one place request paths become files. A path must be relative, use '/'
 * separators, contain no empty, "." or ".." segments, and resolve canonically (following symlinks) to somewhere
 * strictly inside the root; anything else is a 400.
 */
public final class StorageRoot {
    private static final int MAX_PATH = 1024;

    private final File dir;
    private final String prefix;   // canonical root path ending in a separator

    public StorageRoot(File dir) throws IOException {
        this.dir = dir.getCanonicalFile();
        String p = this.dir.getPath();
        this.prefix = p.endsWith(File.separator) ? p : p + File.separator;
    }

    public File dir() {
        return dir;
    }

    /** True if {@code f} (already canonical) lies strictly inside the root. */
    public boolean contains(File canonical) {
        return canonical.getPath().startsWith(prefix);
    }

    /** Resolves a relative request path to a canonical file inside the root. The file need not exist. */
    public File resolve(String rel) throws HttpException {
        if (rel == null || rel.length() == 0) throw new HttpException(400, "empty path");
        if (rel.length() > MAX_PATH) throw new HttpException(400, "path too long");
        if (rel.charAt(0) == '/') throw new HttpException(400, "absolute path");
        if (rel.indexOf('\\') >= 0 || rel.indexOf('\0') >= 0) throw new HttpException(400, "bad character in path");
        String[] segments = rel.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String s = segments[i];
            if (s.length() == 0 || s.equals(".") || s.equals("..")) throw new HttpException(400, "bad path segment");
        }
        File canonical;
        try {
            canonical = new File(dir, rel.replace('/', File.separatorChar)).getCanonicalFile();
        } catch (IOException e) {
            throw new HttpException(400, "unresolvable path");
        }
        if (!contains(canonical)) throw new HttpException(400, "path escapes storage");
        return canonical;
    }
}
