package com.voxivoid.recipelab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@link #NAME} on the card: which picture profile the camera held from which photo on. The profile lives in the image
 * processor, not in the camera's settings, so a photo's maker notes never record it; Recipe Lab Studio reads this file
 * on import and gives each photo the last line whose {@code after} photo it was taken after.
 *
 * <pre>
 * # Recipe Lab: the picture profile on after each photo ...
 * format: 1
 * 2026-11-26 14:03:27  after=100-8117 gamma=s-log3 color-mode=s-gamut3-cine black-level=0 ... s-curve=0
 * 2026-11-26 15:10:00  after=100-8125 gamma=off
 * </pre>
 *
 * {@code after} is the newest photo on the card when the line was written ({@link Dcf#newest}: folder, then file number;
 * {@code none} for an empty card): the camera numbers each later shot above it. It is what places a photo, not the time:
 * the time {@link CardLog#now} gives is not always the one the camera writes into a photo (an A6000 set to summer time
 * logged an hour ahead of its Exif), so it is only for a person reading the file. The rest of a line is the keys and
 * values of {@link RecipeFormatV3} (format 3 recipe files), so a newer curve or colour mode needs no change here. A line is added only when the profile changes, or the card's numbers start over (then the file starts again): on leaving the app (the stored one, or off
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
    static final String HEAD = "# Recipe Lab: the picture profile on after each photo (after=folder-file). Recipe Lab Studio reads it\n"
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

    /** a photo's {@link Dcf#numbers} as a line's {@code after}: {@code 100-8117}; {@code none} for no photo */
    static String after(int[] n) { return n == null ? "none" : String.format(Locale.US, "%03d-%04d", n[0], n[1]); }

    /** {@code when}, two spaces, {@code after=}, the entry */
    static String line(String when, String after, IspProfiles.Look l) { return when + "  after=" + after + " " + entry(l) + "\n"; }

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

    /**
     * the profile of the last line, without its time and {@code after}; null for none, or a last line without
     * {@code after} (from a build before it: Studio cannot place it, so it covers no photo and a new line is due)
     */
    static String last(String text) {
        List<String> e = entries(text);
        if (e.isEmpty()) return null;
        String rest = e.get(e.size() - 1).substring(21);
        if (!rest.startsWith("after=")) return null;
        int sp = rest.indexOf(' ');
        return sp < 0 ? "" : rest.substring(sp + 1);
    }

    /** the last line's {@code after} ({@code 100-8117}, {@code none}); null without a last line that has one */
    static String lastAfter(String text) {
        List<String> e = entries(text);
        if (e.isEmpty()) return null;
        java.util.regex.Matcher m = AFTER.matcher(e.get(e.size() - 1).substring(21));
        return m.lookingAt() ? m.group(1) : null;
    }

    /** {@link #after}'s form: fixed width, so two compare as strings in the camera's order */
    private static final java.util.regex.Pattern AFTER = java.util.regex.Pattern.compile("after=(none|[0-9]{3}-[0-9]{4})(?: |$)");

    /**
     * What to do to a file holding {@code old} (null: none yet) so its last line is {@code l} after photo {@code after}
     * (at {@code when}): null when nothing needs writing (the last line already has this profile, so it covers what
     * comes next, or a file from a newer app); else {@code [mode, text]}, mode "a" to append text, "w" to replace the
     * file with it. A photo numbered below the last line's (the card's numbers started over: File Number Reset, its
     * photos deleted) starts the file again, even for the same profile: the old lines would claim the new photos.
     */
    static String[] update(String old, String when, String after, IspProfiles.Look l) {
        String line = line(when, after, l);
        if (old == null || old.isEmpty()) return new String[] { "w", HEAD + line };
        int f = format(old);
        if (f != FORMAT) return f > FORMAT || f < 0 ? null : new String[] { "w", HEAD + line };   // no format line: not ours
        String was = lastAfter(old);
        if (was != null && !was.equals("none") && (after.equals("none") || after.compareTo(was) < 0)) {
            return new String[] { "w", HEAD + line };   // the numbers started over: the lines before would misplace photos
        }
        if (entry(l).equals(last(old))) return null;
        if (old.length() + line.length() <= MAX_BYTES) return new String[] { "a", old.endsWith("\n") ? line : "\n" + line };
        List<String> e = entries(old);
        StringBuilder s = new StringBuilder(HEAD);
        for (String k : e.subList(Math.max(0, e.size() - (KEEP - 1)), e.size())) s.append(k).append('\n');
        return new String[] { "w", s.append(line).toString() };
    }

    /** brings the card's file to {@code l} for the photos after the newest one now; best-effort, never throws */
    static void record(File cardRoot, IspProfiles.Look l) {
        try {
            if (cardRoot == null) return;
            record(cardRoot, CardLog.now(), after(Dcf.newest(new File(cardRoot, "DCIM"))), l);
        } catch (Throwable ignored) {}
    }

    static void record(File cardRoot, String when, String after, IspProfiles.Look l) {
        try {
            File f = file(cardRoot);
            if (f == null) return;
            String[] u = update(read(f), when, after, l);
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
