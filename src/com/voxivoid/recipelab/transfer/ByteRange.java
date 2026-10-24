package com.voxivoid.recipelab.transfer;

import java.util.Locale;

/**
 * A single {@code Range: bytes=...} request resolved against a file length. Supports {@code a-b}, {@code a-} and
 * the suffix form {@code -n}. A header that is absent, malformed, uses another unit or asks for several ranges is
 * ignored (the whole file is served, as RFC 7233 allows); one whose start lies past the end is unsatisfiable.
 */
public final class ByteRange {
    public enum Kind { FULL, PARTIAL, UNSATISFIABLE }

    public final Kind kind;
    /** Inclusive first and last byte; for FULL the whole file, for UNSATISFIABLE unused. */
    public final long first, last;
    public final long total;

    private ByteRange(Kind kind, long first, long last, long total) {
        this.kind = kind;
        this.first = first;
        this.last = last;
        this.total = total;
    }

    public long length() {
        return kind == Kind.UNSATISFIABLE ? 0 : last - first + 1;
    }

    /** {@code bytes a-b/total}, the value of Content-Range for a 206. */
    public String contentRange() {
        return "bytes " + first + "-" + last + "/" + total;
    }

    public static ByteRange parse(String header, long total) {
        ByteRange full = new ByteRange(Kind.FULL, 0, total - 1, total);
        if (header == null) return full;
        String h = header.trim();
        if (!h.toLowerCase(Locale.US).startsWith("bytes=")) return full;
        String spec = h.substring(6).trim();
        if (spec.indexOf(',') >= 0) return full;
        int dash = spec.indexOf('-');
        if (dash < 0) return full;
        String a = spec.substring(0, dash).trim(), b = spec.substring(dash + 1).trim();
        ByteRange unsatisfiable = new ByteRange(Kind.UNSATISFIABLE, 0, -1, total);
        if (a.length() == 0) {                       // suffix: the last n bytes
            long n = number(b);
            if (n < 0) return full;
            if (n == 0 || total == 0) return unsatisfiable;
            return new ByteRange(Kind.PARTIAL, n >= total ? 0 : total - n, total - 1, total);
        }
        long first = number(a);
        if (first < 0) return full;
        long last;
        if (b.length() == 0) {
            last = total - 1;
        } else {
            last = number(b);
            if (last < 0 || last < first) return full;
        }
        if (first >= total) return unsatisfiable;
        if (last >= total) last = total - 1;
        return new ByteRange(Kind.PARTIAL, first, last, total);
    }

    /** A non-negative decimal, Long.MAX_VALUE if it overflows, -1 if it is not one. */
    private static long number(String s) {
        if (s.length() == 0) return -1;
        long v = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return -1;
            if (v > (Long.MAX_VALUE - 9) / 10) return Long.MAX_VALUE;
            v = v * 10 + (c - '0');
        }
        return v;
    }
}
