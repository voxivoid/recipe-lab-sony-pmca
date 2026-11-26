package com.voxivoid.recipelab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link #NAME} on the card: which picture profile the camera held from when. The profile lives in the image processor,
 * not in the camera's settings, so a photo's maker notes never record it; Recipe Lab Studio reads this file on import
 * and finds the line in force when each photo was taken (its Exif time and these times come from the same camera
 * clock, {@link CardLog#now}).
 *
 * <pre>
 * # Recipe Lab: the picture profile on from each time ...
 * format: 1
 * 2026-11-26 14:03:27  gamma=s-log3 color-mode=s-gamut3-cine black-level=0 ... s-curve=0
 * 2026-11-26 15:10:00  gamma=off
 * </pre>
 *
 * A line has the keys and values of {@link RecipeFormatV3} (format 3 recipe files), so a newer curve or colour mode
 * needs no change here. A line is added only when the profile changes: on leaving the app (the stored one, or off
 * where this body cannot run one) and at power-on ({@link BootReceiver}). Each is appended and closed on its own; past
 * {@link #MAX_BYTES} the file is rewritten with its last {@link #KEEP} lines. A file in a newer format is left alone.
 *
 * <p>No android.* import may appear here (tools/test.sh).
 */
final class ProfileLog {
    private ProfileLog() {}

    static final String NAME = "PROFILES.TXT";
    static final int FORMAT = 1;
    static final int MAX_BYTES = 16 * 1024, KEEP = 100;
    static final String HEAD = "# Recipe Lab: the picture profile on from each time (camera clock). Recipe Lab Studio reads it\n"
            + "# to know which profile a photo was taken with. Written by the camera app; do not edit.\n"
            + "format: " + FORMAT + "\n";

    /** where the file is, under a card root; null without a card */
    static File file(File cardRoot) { return cardRoot == null ? null : new File(new File(cardRoot, CardLog.DIR), NAME); }

    /** the look as a line's keys: {@code gamma=off} for none */
    static String entry(IspProfiles.Look l) {
        String[][] kv = RecipeFormatV3.profileValues(l);
        StringBuilder s = new StringBuilder();
        for (String[] p : kv) s.append(s.length() == 0 ? "" : " ").append(p[0]).append('=').append(p[1]);
        return s.toString();
    }

    /** {@code when}, two spaces, the entry */
    static String line(String when, IspProfiles.Look l) { return when + "  " + entry(l) + "\n"; }

    /** the file's format, 0 when it has no format line */
    static int format(String text) {
        for (String l : text.split("\n")) {
            if (!l.startsWith("format:")) continue;
            try { return Integer.parseInt(l.substring("format:".length()).trim()); } catch (NumberFormatException e) { return -1; }
        }
        return 0;
    }

    /** the time-stamped lines, in order */
    static List<String> entries(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        for (String l : text.split("\n")) if (l.length() > 21 && Character.isDigit(l.charAt(0))) out.add(l);
        return out;
    }

    /** the entry of the last line, without its time; null for none */
    static String last(String text) {
        List<String> e = entries(text);
        return e.isEmpty() ? null : e.get(e.size() - 1).substring(21);
    }

    /**
     * What to do to a file holding {@code old} (null: none yet) so its last line is {@code l} from {@code when}: null
     * when nothing needs writing (already the last line, or a file from a newer app); else {@code [mode, text]}, mode
     * "a" to append text, "w" to replace the file with it.
     */
    static String[] update(String old, String when, IspProfiles.Look l) {
        String line = line(when, l);
        if (old == null || old.isEmpty()) return new String[] { "w", HEAD + line };
        int f = format(old);
        if (f != FORMAT) return f > FORMAT || f < 0 ? null : new String[] { "w", HEAD + line };   // no format line: not ours
        if (entry(l).equals(last(old))) return null;
        if (old.length() + line.length() <= MAX_BYTES) return new String[] { "a", old.endsWith("\n") ? line : "\n" + line };
        List<String> e = entries(old);
        StringBuilder s = new StringBuilder(HEAD);
        for (String k : e.subList(Math.max(0, e.size() - (KEEP - 1)), e.size())) s.append(k).append('\n');
        return new String[] { "w", s.append(line).toString() };
    }

    /** brings the card's file to {@code l} from now; best-effort, never throws */
    static void record(File cardRoot, IspProfiles.Look l) { record(cardRoot, CardLog.now(), l); }

    static void record(File cardRoot, String when, IspProfiles.Look l) {
        try {
            File f = file(cardRoot);
            if (f == null) return;
            String[] u = update(read(f), when, l);
            if (u == null) return;
            f.getParentFile().mkdirs();
            FileOutputStream o = new FileOutputStream(f, u[0].equals("a"));
            try { o.write(u[1].getBytes("UTF-8")); } finally { o.close(); }
        } catch (Throwable ignored) {}
    }

    private static String read(File f) throws IOException {
        if (!f.exists()) return null;
        byte[] b = new byte[(int) Math.min(f.length(), 4L * MAX_BYTES)];
        FileInputStream in = new FileInputStream(f);
        try {
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new String(b, 0, n, "UTF-8");
        } finally { in.close(); }
    }
}
