package com.voxivoid.recipelab;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The logs a user can share: {@link #LOG} (what the app did and what went wrong), and the developer tools' output —
 * {@link #DIFF} (what the snapshot tool saw change), {@link #KEYS} (the key logger), {@link #LOCKS} (the read-only
 * check) — in {@link #DIR}/{@link #SUB} on the memory card, so a bug report can attach them after mounting the
 * camera over USB — files in the app's own storage cannot be reached without a shell.
 *
 * Every line is written and closed on its own: the camera can reboot under the app (issue #100), and the last line
 * before that is the one that matters. A log past {@link #MAX_BYTES} rolls over to {@link #OLD}, so the card never
 * fills. Names are 8.3, as the card's file system takes no longer ones (see {@link CustomRecipes}). English always,
 * like every file the app writes.
 *
 * MainActivity finds the card and calls in; this class formats and writes. No android.* import may appear here
 * (tools/test.sh).
 */
final class CardLog {
    private CardLog() {}

    /** the app's folder at the root of the card: RECIPE-LAB would be ten characters, past what the card takes */
    static final String DIR = "RECIPLAB";
    static final String SUB = "LOGS";
    static final String LOG = "LOG.TXT", OLD = "LOG1.TXT", DIFF = "DIFF.TXT", KEYS = "KEYS.TXT", LOCKS = "LOCKS.TXT";
    /** about a thousand lines; the rolled-over file keeps as many again */
    static final int MAX_BYTES = 64 * 1024;

    /** the kinds of line, padded so the messages line up */
    static final String OPEN = "OPEN", WRITE = "WRITE", FAIL = "FAIL", PREVIEW = "PREVIEW", SHOOT = "SHOOT", CRASH = "CRASH", INFO = "INFO";

    /** the logs folder under a card root; null without a card */
    static File dir(File cardRoot) { return cardRoot == null ? null : new File(new File(cardRoot, DIR), SUB); }

    /** where a log file sits on the card, as a user looks for it over USB */
    static String path(String name) { return DIR + "/" + SUB + "/" + name; }

    /** {@code 2026-10-15 14:03:27  WRITE    msg}; a message over several lines keeps its indent on the next ones */
    static String line(long millis, String kind, String msg) {
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
        String head = when + "  " + pad(kind, 8) + " ";
        return head + String.valueOf(msg).replace("\n", "\n" + spaces(head.length())) + "\n";
    }

    /** what each opening of the app writes first: the body and the build, so a log needs no other context */
    static String header(String model, String platform, String version, String keys) {
        return "model " + orUnknown(model) + "  ·  platform " + orUnknown(platform) + "  ·  app " + orUnknown(version)
                + "  ·  keys " + orUnknown(keys);
    }

    /** a write the store refused, in the words a report needs: slot, value, error and how far it got */
    static String writeFailed(int id, int value, String error, int written, int total) {
        return String.format(Locale.US, "%08x = %d refused: %s  (%d of %d written before it)", id, value, error, written, total);
    }

    /** a successful write: the recipe, then every slot and value, as id=value pairs */
    static String written(String recipe, List<Params.Write> ws) {
        StringBuilder sb = new StringBuilder(recipe).append(": ").append(ws.size()).append(ws.size() == 1 ? " slot" : " slots");
        for (Params.Write w : ws) sb.append("  ").append(w);
        return sb.toString();
    }

    /**
     * The preview parameters the camera did not keep: what was set, read straight back. Empty when it kept them all.
     * A body that silently drops one — Movie mode on the A7 shows a recipe in colour that is stored as B&W (issue #104)
     * — throws nothing, so this is the only trace it leaves.
     */
    static String previewDropped(Map<String, String> wanted, Map<String, String> got) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : wanted.entrySet()) {
            String kept = got.get(e.getKey());
            if (!e.getValue().equals(kept)) sb.append(sb.length() == 0 ? "" : "  ").append(e.getKey()).append(' ').append(e.getValue()).append('>').append(kept);
        }
        return sb.toString();
    }

    /** slot ids the way every other line shows them: eight hex digits */
    static String ids(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) sb.append(sb.length() == 0 ? "" : " ").append(String.format(Locale.US, "%08x", id));
        return sb.toString();
    }

    /** a throwable with its whole stack, for the crash handler */
    static String stack(Throwable t) {
        StringWriter s = new StringWriter();
        t.printStackTrace(new PrintWriter(s));
        return s.toString().trim();
    }

    /** whether appending {@code add} bytes to a file of {@code size} would take it past the cap */
    static boolean rolls(long size, int add) { return size > 0 && size + add > MAX_BYTES; }

    /**
     * Appends text to {@code name} in the logs folder, creating the folders. {@link #LOG} rolls over to {@link #OLD}
     * first when it would pass the cap; the others never do, as they only grow by a developer's hand.
     */
    static void append(File dir, String name, String text) throws IOException {
        if (dir == null) throw new IOException("no memory card");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File f = new File(dir, name);
        if (LOG.equals(name) && rolls(f.length(), text.length())) {
            File old = new File(dir, OLD);
            old.delete();
            if (!f.renameTo(old)) f.delete();
        }
        FileWriter w = new FileWriter(f, true);
        try { w.write(text); } finally { w.close(); }
    }

    private static String orUnknown(String s) { return s == null || s.length() == 0 ? "unknown" : s; }
    private static String pad(String s, int n) { return s.length() >= n ? s : s + spaces(n - s.length()); }
    private static String spaces(int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(' '); return b.toString(); }
}
