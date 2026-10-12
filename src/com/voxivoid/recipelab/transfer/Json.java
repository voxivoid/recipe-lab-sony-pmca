package com.voxivoid.recipelab.transfer;

/** Just enough JSON writing for the protocol: string quoting. Output is pure ASCII. */
public final class Json {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Json() {}

    public static String quote(String s) {
        StringBuilder sb = new StringBuilder(s == null ? 4 : s.length() + 2);
        quote(sb, s);
        return sb.toString();
    }

    /** Appends {@code s} as a JSON string literal, or {@code null} for a null reference. */
    public static void quote(StringBuilder sb, String s) {
        if (s == null) { sb.append("null"); return; }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c >= 0x7f) {
                        sb.append("\\u").append(HEX[(c >> 12) & 15]).append(HEX[(c >> 8) & 15])
                          .append(HEX[(c >> 4) & 15]).append(HEX[c & 15]);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
