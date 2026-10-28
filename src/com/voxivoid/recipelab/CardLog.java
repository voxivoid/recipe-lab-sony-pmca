package com.voxivoid.recipelab;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The logs a user can share: {@link #LOG} (what the app did and what went wrong), and the developer tools' output —
 * {@link #DIFF} (what the snapshot tool saw change), {@link #KEYS} (the key logger), {@link #LOCKS} (the read-only
 * check) — and {@link #PARAMS}, every camera parameter as of the last opening, in {@link #DIR}/{@link #SUB} on the
 * memory card, so a bug report can attach them after mounting the camera over USB — files in the app's own storage
 * cannot be reached without a shell.
 *
 * Every line is written and closed on its own: the camera can reboot under the app (issue #100), and the last line
 * before that is the one that matters. Any file past {@link #MAX_BYTES} rolls over to one older file ({@link #older}),
 * and {@link #PARAMS} is replaced, never appended, so the folder holds at most nine fixed names and about 600 KB. Names are 8.3, as the card's file system takes no longer ones (see {@link CustomRecipes}). English always,
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
    static final String LOG = "LOG.TXT", DIFF = "DIFF.TXT", KEYS = "KEYS.TXT", LOCKS = "LOCKS.TXT", PARAMS = "PARAMS.TXT";
    /** about a thousand lines; the rolled-over file keeps as many again */
    static final int MAX_BYTES = 64 * 1024;

    /** the kinds of line, padded so the messages line up */
    static final String OPEN = "OPEN", STATE = "STATE", WRITE = "WRITE", FAIL = "FAIL", PREVIEW = "PREVIEW", SHOOT = "SHOOT", CRASH = "CRASH", INFO = "INFO",
            TRANSFER = "TRANSFER";

    /** the logs folder under a card root; null without a card */
    static File dir(File cardRoot) { return cardRoot == null ? null : new File(new File(cardRoot, DIR), SUB); }

    /** where a log file sits on the card, as a user looks for it over USB */
    static String path(String name) { return DIR + "/" + SUB + "/" + name; }

    /** {@code 2026-10-15 14:03:27  WRITE    msg}, at the camera's time; a message over several lines keeps its indent */
    static String line(String kind, String msg) { return line(now(), kind, msg); }

    static String line(String when, String kind, String msg) {
        String head = when + "  " + pad(kind, 8) + " ";
        return head + String.valueOf(msg).replace("\n", "\n" + spaces(head.length())) + "\n";
    }

    /**
     * The time the camera shows. Its Android clock is never set — an A6000 log read 1970-01-01 — so the time comes
     * from Sony's TimeUtil, by reflection as in {@link KeyProbe}: a UTC calendar plus the zone and summer-time offsets
     * set in the camera's menu (as OpenMemories-Framework's DateTime reads it). Off the camera, the system clock.
     */
    static String now() {
        try {
            Class<?> tu = Class.forName("com.sony.scalar.sysutil.TimeUtil");
            Object cal = tu.getMethod("getCurrentCalendar").invoke(null), tz = tu.getMethod("getCurrentTimeZone").invoke(null);
            return stamp(field(cal, "year"), field(cal, "month"), field(cal, "day"), field(cal, "hour"), field(cal, "minute"),
                    field(cal, "second"), field(tz, "gmtDiff") + field(tz, "summerTimeDiff"));
        } catch (Throwable t) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        }
    }

    /** a UTC date and time (month 1–12) shifted by the camera's offset in minutes */
    static String stamp(int year, int month, int day, int hour, int minute, int second, int offsetMinutes) {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month - 1, day, hour, minute, second);
        c.add(Calendar.MINUTE, offsetMinutes);
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(c.getTime());
    }

    private static int field(Object o, String name) throws Exception { return o.getClass().getField(name).getInt(o); }

    /** what each opening of the app writes first: the body, the build and the mode dial, so a log needs no other context */
    static String header(String model, String firmware, String platform, String version, String keys, String mode) {
        return "model " + orUnknown(model) + "  ·  firmware " + orUnknown(firmware) + "  ·  platform " + orUnknown(platform)
                + "  ·  app " + orUnknown(version)
                + "  ·  mode " + modeLabel(mode) + "  ·  keys " + orUnknown(keys);
    }

    /** the mode dial as a reader should take it: an A6000 on the Movie dial reports {@code auto}, the same as Auto */
    static String modeLabel(String mode) { return "auto".equals(mode) ? "auto (Auto or Movie)" : orUnknown(mode); }

    /**
     * What the app shows when the report is saved — so a bug report need not ask: the recipe on screen, whether the
     * camera holds it (ACTIVE / PREVIEW) and whether its values were changed (EDITED), the display language, and the
     * user's own recipes and marks.
     */
    static String app(String recipe, boolean active, boolean edited, String language, int customs, List<String> skipped, int favourites) {
        return "recipe " + recipe + (active ? "  ACTIVE" : "  PREVIEW") + (edited ? " EDITED" : "") + "  ·  language " + language
                + "  ·  custom recipes " + customs + (skipped.isEmpty() ? "" : " (skipped: " + join(skipped) + ")")
                + "  ·  favourites " + favourites;
    }

    private static String join(List<String> l) { StringBuilder b = new StringBuilder(); for (String s : l) b.append(b.length() == 0 ? "" : "; ").append(s); return b.toString(); }

    /** the camera parameter the mode dial shows in (P / A / S / M / the auto and scene modes) */
    static final String MODE_KEY = "scene-mode";

    /** one value out of {@code Camera.Parameters.flatten()} ({@code key=value;key=value}); null when it is not there */
    static String param(String flat, String key) {
        if (flat == null) return null;
        String k = key + "=";
        int at = flat.startsWith(k) ? 0 : flat.indexOf(";" + k);
        if (at < 0) return null;
        int from = at == 0 ? k.length() : at + 1 + k.length(), to = flat.indexOf(';', from);
        return flat.substring(from, to < 0 ? flat.length() : to);
    }

    /** {@link #PARAMS}: the flattened parameters one per line, sorted, so two reports compare with any diff tool */
    static String paramsFile(String header, String flat) {
        String[] kv = flat == null ? new String[0] : flat.split(";");
        java.util.Arrays.sort(kv);
        StringBuilder sb = new StringBuilder("# ").append(header).append('\n');
        for (String s : kv) if (s.length() > 0) sb.append(s).append('\n');
        return sb.toString();
    }

    /** what the store holds for every recipe row, by the rows' English names: {@code STYLE=1 SAT=0 …} */
    static String state(int[] rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < rows.length && i < Params.ROW_NAME.length; i++) sb.append(i > 1 ? " " : "").append(Params.ROW_NAME[i]).append('=').append(rows[i]);
        return sb.toString();
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

    // ------------------------------------------------------------ the report: one file for a user to attach
    /** the one file a bug report needs, beside LOGS rather than in it, so it is the first thing seen in the folder */
    static final String REPORT = "REPORT.TXT";
    /** how much of each file the report keeps, from the end: the newest lines are the ones that explain a bug */
    static final int REPORT_LOG = 48 * 1024, REPORT_TOOL = 8 * 1024;

    /** where the report goes under a card root; null without a card */
    static File reportDir(File cardRoot) { return cardRoot == null ? null : new File(cardRoot, DIR); }

    /**
     * Everything a bug report needs in one file, so a user who is not a developer attaches one thing: the camera now
     * (its header, what the store holds, every parameter), then the end of the log and of each developer tool's file.
     * Cheap: it only runs when the user asks for it, and reads at most about 100 KB.
     */
    static String report(File logs, String now, String header, String app, String state, String flat) {
        StringBuilder sb = new StringBuilder("Recipe Lab report: attach this file to your GitHub issue.\n")
                .append("saved ").append(now).append('\n').append(header).append('\n').append(app).append('\n')
                .append("STATE ").append(state).append('\n');
        section(sb, "LOG", tail(new File(logs, older(LOG)), new File(logs, LOG), REPORT_LOG));
        section(sb, "LOCKS", tail(null, new File(logs, LOCKS), REPORT_TOOL));
        section(sb, "DIFF", tail(null, new File(logs, DIFF), REPORT_TOOL));
        section(sb, "KEYS", tail(null, new File(logs, KEYS), REPORT_TOOL));
        section(sb, "PARAMS", flat != null ? paramsFile(now, flat) : tail(null, new File(logs, PARAMS), REPORT_LOG));
        return sb.toString();
    }

    private static void section(StringBuilder sb, String name, String body) {
        sb.append("\n===== ").append(name).append(" =====\n").append(body.length() == 0 ? "(none)\n" : body);
        if (body.length() > 0 && !body.endsWith("\n")) sb.append('\n');
    }

    /** the last {@code max} bytes of {@code older} then {@code file} together, from a whole line on; "" when neither is there */
    static String tail(File older, File file, int max) {
        String all = read(older) + read(file);
        if (all.length() <= max) return all;
        String cut = all.substring(all.length() - max);
        int nl = cut.indexOf('\n');
        return nl < 0 ? cut : cut.substring(nl + 1);
    }

    private static String read(File f) {
        if (f == null || !f.isFile()) return "";
        try {
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try { int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } } finally { in.close(); }
            return new String(b, "UTF-8");
        } catch (IOException e) { return ""; }
    }

    /** the one older file a full one rolls over to: LOG.TXT → LOG1.TXT, still 8.3 */
    static String older(String name) { int dot = name.lastIndexOf('.'); return name.substring(0, dot) + "1" + name.substring(dot); }

    /** Appends text to {@code name} in the logs folder, creating the folders; a file that would pass the cap rolls over first */
    static void append(File dir, String name, String text) throws IOException {
        File f = file(dir, name);
        if (rolls(f.length(), text.length())) {
            File old = new File(dir, older(name));
            old.delete();
            if (!f.renameTo(old)) f.delete();
        }
        write(f, text, true);
    }

    /** Replaces {@code name} in the logs folder with text: a snapshot such as {@link #PARAMS}, never more than one */
    static void replace(File dir, String name, String text) throws IOException { write(file(dir, name), text, false); }

    private static File file(File dir, String name) throws IOException {
        if (dir == null) throw new IOException("no memory card");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        return new File(dir, name);
    }

    private static void write(File f, String text, boolean append) throws IOException {
        FileWriter w = new FileWriter(f, append);
        try { w.write(text); } finally { w.close(); }
    }

    private static String orUnknown(String s) { return s == null || s.length() == 0 ? "unknown" : s; }
    private static String pad(String s, int n) { return s.length() >= n ? s : s + spaces(n - s.length()); }
    private static String spaces(int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(' '); return b.toString(); }
}
