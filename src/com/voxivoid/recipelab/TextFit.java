package com.voxivoid.recipelab;

/**
 * Text fitted to a width: one line cut with an ellipsis, or a paragraph wrapped onto a few lines. How wide a run of
 * text is comes from the caller (a Paint on the camera), so none of this needs Android.
 */
final class TextFit {
    private TextFit() {}

    /** how wide {@code s} is from {@code start} to {@code end} */
    interface Width {
        float of(String s, int start, int end);
    }

    static final String ELLIPSIS = "…";

    /** {@code s} cut to {@code max} wide with an ellipsis, or as it is when it fits; "" for null */
    static String ellipsize(String s, Width w, float max) {
        if (s == null) return "";
        if (w.of(s, 0, s.length()) <= max) return s;
        int n = s.length();
        float ew = w.of(ELLIPSIS, 0, ELLIPSIS.length());
        while (n > 0 && w.of(s, 0, n) + ew > max) n--;
        return s.substring(0, n).trim() + ELLIPSIS;
    }

    /**
     * {@code s} broken into at most {@code maxLines} lines of {@code max} wide in {@code lines}: at spaces where there
     * are any, between characters where there are none (Chinese); the last line ellipsized if text is left. Returns the
     * count.
     */
    static int wrap(String s, Width w, float max, String[] lines, int maxLines) {
        int count = 0, start = 0, len = s.length();
        while (start < len && count < maxLines) {
            while (start < len && s.charAt(start) == ' ') start++;
            if (start >= len) break;
            if (count == maxLines - 1) { lines[count++] = ellipsize(s.substring(start), w, max); break; }
            int end = start + 1;
            while (end < len && w.of(s, start, end + 1) <= max) end++;
            if (end < len) {
                int sp = s.lastIndexOf(' ', end);
                if (sp > start) end = sp;
            }
            lines[count++] = s.substring(start, end);
            start = end;
        }
        return count;
    }
}
