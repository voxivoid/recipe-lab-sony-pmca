package com.voxivoid.recipelab;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Every new file on the memory card is created here (issue #130). A NEX-5R restarts when an app creates a file in some
 * folders, while making a folder is safe; the files that crashed were the first in their folder, so an empty folder gets
 * a {@link #KEEP} subfolder first.
 *
 * If a create still takes the camera down, the card itself remembers it. The marks are folders, because a folder is what
 * this camera can make safely and because the app's own storage is a RAM disk there, which a crash can roll back:
 * {@link #TRYING} stands while a file is being created, so finding it on opening means the last create never finished.
 * The first time leaves {@link #STRIKE} (a camera switched off at that instant must not lose card writes for good); a
 * second time in a row leaves {@link #BLOCKED}, and no file is created on that card again. Deleting RECIPLAB/NOFILES on a
 * computer undoes it. Files that already exist are still written, which that camera does safely.
 *
 * No android.* import may appear here (tools/test.sh).
 */
public final class CardGuard {
    private CardGuard() {}

    static final String TRYING = "TRYING", STRIKE = "STRIKE", BLOCKED = "NOFILES", KEEP = "KEEP";
    /** custom recipes kept in the camera on a blocked card: its app storage is a 1 MB RAM disk the settings live on too */
    static final int MAX_IN_CAMERA = 40;

    private static File root;
    private static boolean blocked;

    /** each opening, before anything is written: reads the card's marks, and counts a create that never finished */
    public static synchronized void open(File cardRoot) {
        root = cardRoot;
        blocked = false;
        if (root == null) return;
        File app = new File(root, CardLog.DIR), trying = new File(app, TRYING), strike = new File(app, STRIKE), mark = new File(app, BLOCKED);
        if (trying.isDirectory()) {
            trying.delete();
            if (strike.isDirectory()) { strike.delete(); mark.mkdir(); }
            else strike.mkdir();
        }
        blocked = mark.isDirectory();
    }

    /** true when this card must get no new file */
    public static synchronized boolean blocked() { return blocked; }

    /**
     * Makes sure {@code f} exists before it is opened for writing, so the writer only ever opens a file that is there. A
     * file outside the card, or one already there, is left alone.
     *
     * @throws IOException on a blocked card, with the message the screen shows
     */
    public static synchronized void ensure(File f) throws IOException {
        if (f.exists() || !onCard(f)) return;
        if (blocked) throw new IOException(Lang.t("card_no_files"));
        File app = new File(root, CardLog.DIR), trying = new File(app, TRYING);
        app.mkdirs();
        trying.mkdir();
        try {
            File dir = f.getParentFile();
            String[] held = dir == null ? null : dir.list();
            if (held != null && held.length == 0) new File(dir, KEEP).mkdir();
            new FileOutputStream(f).close();
            new File(app, STRIKE).delete();
        } finally {
            trying.delete();
        }
    }

    private static boolean onCard(File f) throws IOException {
        return root != null && f.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator);
    }
}
