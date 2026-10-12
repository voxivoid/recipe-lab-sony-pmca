package com.voxivoid.recipelab.transfer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** One parsed HTTP/1.x request head. Bodies are never read: the protocol only has GET and HEAD. */
public final class HttpRequest {
    /** Longest request head accepted (request line plus headers). */
    public static final int MAX_HEAD = 16 * 1024;
    private static final int MAX_HEADERS = 100;

    public final String method;
    /** The request target as sent, e.g. {@code /api/preview?path=DCIM%2Fa.ARW}. */
    public final String target;
    /** The percent-decoded path part of the target ('+' stays '+'). */
    public final String path;
    /** The raw query string after '?', or null. */
    public final String query;
    public final String version;
    private final Map<String, String> headers;

    private HttpRequest(String method, String target, String path, String query, String version,
                        Map<String, String> headers) {
        this.method = method;
        this.target = target;
        this.path = path;
        this.query = query;
        this.version = version;
        this.headers = headers;
    }

    /** A header value by case-insensitive name, or null. Repeated headers are joined with ", ". */
    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.US));
    }

    /** The first query parameter called {@code name}, decoded ('+' is a space), or null. */
    public String queryParam(String name) throws HttpException {
        if (query == null) return null;
        int start = 0;
        while (start <= query.length()) {
            int amp = query.indexOf('&', start);
            if (amp < 0) amp = query.length();
            String pair = query.substring(start, amp);
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            if (percentDecode(k, true).equals(name)) return eq < 0 ? "" : percentDecode(pair.substring(eq + 1), true);
            start = amp + 1;
        }
        return null;
    }

    /**
     * Reads one request head from {@code in}. Returns null if the stream ends before any byte arrives (a client
     * that connected and went away). Throws a 400 {@link HttpException} for anything malformed.
     */
    public static HttpRequest read(InputStream in) throws IOException, HttpException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(512);
        int newlines = 0;       // consecutive line ends, ignoring CR
        boolean started = false;
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (!started) return null;
                throw new HttpException(400, "incomplete request");
            }
            if (!started) {
                if (b == '\r' || b == '\n') continue;   // tolerate empty lines before the request line
                started = true;
            }
            buf.write(b);
            if (buf.size() > MAX_HEAD) throw new HttpException(400, "request head too large");
            if (b == '\n') {
                if (++newlines == 2) break;
            } else if (b != '\r') {
                newlines = 0;
            }
        }
        String head;
        try {
            head = buf.toString("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new IOException(e.toString());
        }
        return parse(head);
    }

    /** Parses a complete request head (request line and header lines, CRLF or LF separated). */
    public static HttpRequest parse(String head) throws HttpException {
        String[] lines = head.split("\r?\n");
        if (lines.length == 0 || lines[0].length() == 0) throw new HttpException(400, "empty request");
        String[] parts = lines[0].split(" ", -1);
        if (parts.length != 3) throw new HttpException(400, "malformed request line");
        String method = parts[0], target = parts[1], version = parts[2];
        if (method.length() == 0 || method.length() > 16) throw new HttpException(400, "malformed method");
        for (int i = 0; i < method.length(); i++) {
            char c = method.charAt(i);
            if (c < 'A' || c > 'Z') throw new HttpException(400, "malformed method");
        }
        if (!version.equals("HTTP/1.1") && !version.equals("HTTP/1.0")) throw new HttpException(400, "unsupported version");
        if (target.length() == 0 || target.charAt(0) != '/') throw new HttpException(400, "malformed target");
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            if (c <= 0x20 || c >= 0x7f) throw new HttpException(400, "malformed target");
        }

        Map<String, String> headers = new HashMap<String, String>();
        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() == 0) break;
            if (++count > MAX_HEADERS) throw new HttpException(400, "too many headers");
            int colon = line.indexOf(':');
            if (colon <= 0) throw new HttpException(400, "malformed header");
            String name = line.substring(0, colon);
            for (int j = 0; j < name.length(); j++) {
                char c = name.charAt(j);
                if (c <= 0x20 || c >= 0x7f) throw new HttpException(400, "malformed header");
            }
            name = name.toLowerCase(Locale.US);
            String value = line.substring(colon + 1).trim();
            String prev = headers.get(name);
            headers.put(name, prev == null ? value : prev + ", " + value);
        }

        int hash = target.indexOf('#');
        String t = hash < 0 ? target : target.substring(0, hash);
        int q = t.indexOf('?');
        String rawPath = q < 0 ? t : t.substring(0, q);
        String query = q < 0 ? null : t.substring(q + 1);
        return new HttpRequest(method, target, percentDecode(rawPath, false), query, version, headers);
    }

    /** Decodes %XX escapes as UTF-8. {@code plusIsSpace} is for query strings. Bad escapes are a 400. */
    public static String percentDecode(String s, boolean plusIsSpace) throws HttpException {
        if (s.indexOf('%') < 0 && !(plusIsSpace && s.indexOf('+') >= 0)) return s;
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%') {
                if (i + 2 >= s.length()) throw new HttpException(400, "bad percent escape");
                int hi = Character.digit(s.charAt(i + 1), 16), lo = Character.digit(s.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) throw new HttpException(400, "bad percent escape");
                out.write((hi << 4) | lo);
                i += 2;
            } else if (c == '+' && plusIsSpace) {
                out.write(' ');
            } else if (c < 0x80) {
                out.write(c);
            } else {
                byte[] b = Utf8.bytes(String.valueOf(c));
                out.write(b, 0, b.length);
            }
        }
        return Utf8.string(out.toByteArray());
    }
}
