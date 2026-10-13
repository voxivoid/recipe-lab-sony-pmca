package com.voxivoid.recipelab.transfer;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * The protocol 3 HTTP server: blocking sockets, one thread per connection (at most {@link #MAX_CONNECTIONS}), one
 * response per connection, files streamed in {@link Protocol#BUFFER_SIZE} chunks and never held in memory.
 *
 * <pre>
 * GET  /api/info                               camera identity (JSON)
 * GET  /api/files                              every ARW and JPEG on the card, newest first (JSON)
 * GET  /api/preview?path=REL&amp;size=small|large  ARW: the embedded JPEG; JPEG: its Exif thumbnail (small) or
 *                                              the file itself (large); X-Orientation header
 * GET  /api/recipes                            the custom recipe files in RECIPES (JSON)
 * GET  /files/REL  (and HEAD, Range)           the file itself
 * PUT  /files/RECIPES/NAME.YML                 writes a custom recipe file (new or replaced), nothing else
 * </pre>
 */
public final class LinkServer {
    public static final int MAX_CONNECTIONS = 6;
    /** How long a client may take to send its request head. */
    private static final int READ_TIMEOUT_MS = 20000;

    private final StorageRoot root;
    private final String model, name;
    private final Stats stats;
    private final Logger log;
    private final Semaphore slots = new Semaphore(MAX_CONNECTIONS);
    private final Object writing = new Object();                  // one recipe file written at a time: they share SAVING.TMP
    private final Set<Socket> open = new HashSet<Socket>();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    public LinkServer(StorageRoot root, String model, String name, Stats stats, Logger log) {
        this.root = root;
        this.model = model;
        this.name = name;
        this.stats = stats;
        this.log = log;
    }

    /** Binds all interfaces on {@code port} (0 = any free port) and starts accepting; returns the bound port. */
    public synchronized int start(int port) throws IOException {
        if (running) return serverSocket.getLocalPort();
        final ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(port), 16);
        serverSocket = ss;
        running = true;
        acceptThread = new Thread(new Runnable() {
            @Override public void run() { acceptLoop(ss); }
        }, "link-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log.info("serving " + root.dir() + " on port " + ss.getLocalPort());
        return ss.getLocalPort();
    }

    public synchronized boolean isRunning() {
        return running;
    }

    /** Stops accepting and drops every open connection. Safe to call twice. */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        closeQuietly(serverSocket);
        if (acceptThread != null) acceptThread.interrupt();
        synchronized (open) {
            for (Iterator<Socket> it = open.iterator(); it.hasNext(); ) closeQuietly(it.next());
            open.clear();
        }
        serverSocket = null;
        acceptThread = null;
        log.info("server stopped");
    }

    private void acceptLoop(ServerSocket ss) {
        while (running) {
            try {
                slots.acquire();
            } catch (InterruptedException e) {
                return;
            }
            final Socket s;
            try {
                s = ss.accept();
            } catch (IOException e) {
                slots.release();
                if (running) log.error("accept failed", e);
                if (ss.isClosed()) return;
                continue;
            }
            synchronized (open) { open.add(s); }
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        serve(s);
                    } finally {
                        synchronized (open) { open.remove(s); }
                        closeQuietly(s);
                        slots.release();
                    }
                }
            }, "link-conn");
            t.setDaemon(true);
            t.start();
        }
    }

    private void serve(Socket s) {
        CountingOutputStream out = null;
        String summary = "?";
        boolean started = false;
        try {
            s.setSoTimeout(READ_TIMEOUT_MS);
            InputStream in = new BufferedInputStream(s.getInputStream(), 4096);
            out = new CountingOutputStream(s.getOutputStream(), stats);
            HttpRequest req;
            try {
                req = HttpRequest.read(in);
            } catch (HttpException e) {
                stats.requestStarted("bad request");
                started = true;
                summary = "bad request";
                writeError(out, e, false);
                summary = "bad request -> " + e.status;
                return;
            }
            if (req == null) return;
            summary = req.method + " " + req.target;
            stats.requestStarted(summary);
            started = true;
            int status = handleOrError(req, in, out);
            summary = summary + " -> " + status;
        } catch (SocketException e) {
            summary = summary + " -> dropped";     // client went away, or stop() closed the socket
        } catch (IOException e) {
            summary = summary + " -> dropped";
            log.info(summary + ": " + e);
        } finally {
            if (started) stats.requestFinished(summary);
            if (out != null) {
                try { out.flush(); } catch (IOException ignored) {}
                try { s.shutdownOutput(); } catch (IOException ignored) {}   // FIN, not RST, before close
            }
        }
    }

    /** Handles one request, turning failures into error responses; returns the status sent. */
    public int handleOrError(HttpRequest req, InputStream in, CountingOutputStream out) throws IOException {
        boolean head = "HEAD".equals(req.method);
        try {
            return handle(req, in, out);
        } catch (HttpException e) {
            if (out.count() > 0) throw new IOException("failed after response started: " + e.getMessage());
            writeError(out, e, head);
            return e.status;
        } catch (RuntimeException e) {
            log.error("internal error on " + req.target, e);
            if (out.count() > 0) throw new IOException("failed after response started: " + e);
            writeError(out, new HttpException(500, "internal error"), head);
            return 500;
        }
    }

    /** A request without a body. */
    int handle(HttpRequest req, OutputStream out) throws IOException, HttpException {
        return handle(req, new ByteArrayInputStream(new byte[0]), out);
    }

    /**
     * Routes one request and writes the whole response; returns the status. Errors are thrown, not written.
     * {@code in} is the connection past the request head, where a PUT's body is.
     */
    int handle(HttpRequest req, InputStream in, OutputStream out) throws IOException, HttpException {
        boolean head = "HEAD".equals(req.method);
        String p = req.path;
        if ("PUT".equals(req.method)) return putRecipe(req, p, in, out);
        if (!head && !"GET".equals(req.method)) throw new HttpException(405, "method not allowed", "Allow", "GET, HEAD, PUT");
        if (p.equals("/api/info")) return json(out, Protocol.infoJson(model, name), head);
        if (p.equals("/api/files")) return json(out, FileIndex.toJson(FileIndex.scan(root)), head);
        if (p.equals("/api/recipes")) return json(out, FileIndex.toJson(FileIndex.recipes(root)), head);
        if (p.equals("/api/preview")) return preview(req, out, head);
        if (p.startsWith("/files/")) return file(req, p.substring("/files/".length()), out, head);
        throw new HttpException(404, "not found");
    }

    private int json(OutputStream out, String body, boolean head) throws IOException {
        byte[] b = Utf8.bytes(body);
        writeHead(out, 200, "application/json", b.length);
        if (!head) out.write(b);
        return 200;
    }

    private int preview(HttpRequest req, OutputStream out, boolean head) throws IOException, HttpException {
        String rel = req.queryParam("path");
        if (rel == null || rel.length() == 0) throw new HttpException(400, "missing path");
        String size = req.queryParam("size");
        if (size == null) size = "large";
        if (!size.equals("small") && !size.equals("large")) throw new HttpException(400, "size must be small or large");
        File f = root.resolve(rel);
        String leaf = rel.substring(rel.lastIndexOf('/') + 1);
        String type = FileIndex.type(leaf);
        if (type == null || !f.isFile()) throw new HttpException(404, "no such ARW or JPEG");
        boolean small = size.equals("small");
        if (type.equals(FileIndex.TYPE_JPG)) return jpegPreview(f, small, out, head);
        ArwPreview.Previews previews = ArwPreview.locate(f);
        ArwPreview.Jpeg jpeg = previews.pick(small);
        if (jpeg == null) throw new HttpException(404, "no embedded preview");
        writeHead(out, 200, "image/jpeg", jpeg.length, "X-Orientation", String.valueOf(previews.orientation));
        if (!head) copy(f, jpeg.offset, jpeg.length, out);
        return 200;
    }

    /** A JPEG's preview: small is its Exif thumbnail (none is a 404), large the file itself. */
    private int jpegPreview(File f, boolean small, OutputStream out, boolean head) throws IOException, HttpException {
        ArwPreview.Previews exif = ArwPreview.locateExif(f);
        String orientation = String.valueOf(exif.orientation);
        if (small) {
            ArwPreview.Jpeg thumb = exif.small;
            if (thumb == null) throw new HttpException(404, "no Exif thumbnail");
            writeHead(out, 200, "image/jpeg", thumb.length, "X-Orientation", orientation);
            if (!head) copy(f, thumb.offset, thumb.length, out);
            return 200;
        }
        long total = f.length();
        writeHead(out, 200, "image/jpeg", total, "X-Orientation", orientation);
        if (!head) copy(f, 0, total, out);
        return 200;
    }

    private int file(HttpRequest req, String rel, OutputStream out, boolean head) throws IOException, HttpException {
        File f = root.resolve(rel);
        if (!f.isFile()) throw new HttpException(404, "not found");
        long total = f.length();
        ByteRange range = ByteRange.parse(req.header("Range"), total);
        switch (range.kind) {
            case UNSATISFIABLE:
                throw new HttpException(416, "range not satisfiable", "Content-Range", "bytes */" + total,
                        "Accept-Ranges", "bytes");
            case PARTIAL:
                writeHead(out, 206, "application/octet-stream", range.length(),
                        "Accept-Ranges", "bytes", "Content-Range", range.contentRange());
                if (!head) copy(f, range.first, range.length(), out);
                return 206;
            default:
                writeHead(out, 200, "application/octet-stream", total, "Accept-Ranges", "bytes");
                if (!head) copy(f, 0, total, out);
                return 200;
        }
    }

    /**
     * {@code PUT /files/RECIPES/NAME.YML}: the body becomes that custom recipe file, new (201) or replacing the one
     * there (200). Nothing else on the card can be written (403): the folder must be RECIPES and the name one the
     * card's file system takes, up to eight letters and digits plus .YML, stored in capitals. What the file says is
     * not looked at; Recipe Lab reads it as it reads any file the card brings. The bytes go to SAVING.TMP first and
     * only then take the name, as Recipe Lab's own saves do.
     */
    private int putRecipe(HttpRequest req, String p, InputStream in, OutputStream out) throws IOException, HttpException {
        String rel = p.startsWith("/files/") ? p.substring("/files/".length()) : "";
        int slash = rel.indexOf('/');
        String leaf = slash < 0 ? "" : rel.substring(slash + 1);
        if (slash < 0 || !rel.substring(0, slash).equalsIgnoreCase(FileIndex.RECIPES) || !recipeFileName(leaf))
            throw new HttpException(403, "only RECIPES/NAME.YML can be written");
        byte[] body = req.body(in, Protocol.MAX_RECIPE_BYTES);
        leaf = leaf.toUpperCase(Locale.US);
        String folder = FileIndex.RECIPES;                           // the card's own spelling of the folder, when it has one
        String[] top = root.dir().list();
        if (top != null) for (int i = 0; i < top.length; i++) if (top[i].equalsIgnoreCase(FileIndex.RECIPES)) folder = top[i];
        File dir = root.resolve(folder);
        boolean replaced;
        synchronized (writing) {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new HttpException(500, "cannot create " + folder);
            String[] names = dir.list();
            if (names != null) for (int i = 0; i < names.length; i++) if (names[i].equalsIgnoreCase(leaf)) leaf = names[i];
            File target = root.resolve(folder + "/" + leaf), tmp = root.resolve(folder + "/" + TMP);
            replaced = target.exists();
            try {
                FileOutputStream f = new FileOutputStream(tmp);
                try {
                    f.write(body);
                    f.flush();
                    try { f.getFD().sync(); } catch (IOException noSync) {}   // Sony's FUSE layer has no fsync
                } finally {
                    f.close();
                }
            } catch (IOException e) {
                tmp.delete();
                throw new HttpException(500, "cannot write " + leaf);
            }
            if (replaced && !target.delete()) { tmp.delete(); throw new HttpException(500, "cannot replace " + leaf); }
            if (!tmp.renameTo(target)) { tmp.delete(); throw new HttpException(500, "cannot write " + leaf); }
        }
        byte[] b = Utf8.bytes("{\"path\":" + Json.quote(folder + "/" + leaf) + "}");
        int status = replaced ? 200 : 201;
        writeHead(out, status, "application/json", b.length);
        out.write(b);
        return status;
    }

    /** The file a save writes before it takes the recipe's name, as in Recipe Lab's own saves; never read as a recipe. */
    static final String TMP = "SAVING.TMP";

    /** A recipe file name the card takes: one to eight ASCII letters and digits, then .YML (any case). */
    static boolean recipeFileName(String name) {
        if (!FileIndex.isRecipe(name)) return false;
        int n = name.length() - 4;
        if (n < 1 || n > 8) return false;
        for (int i = 0; i < n; i++) {
            char c = name.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    /** Streams {@code length} bytes of {@code f} from {@code offset} in small chunks. */
    static void copy(File f, long offset, long length, OutputStream out) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            raf.seek(offset);
            byte[] buf = new byte[Protocol.BUFFER_SIZE];
            long left = length;
            while (left > 0) {
                int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new IOException("file shrank while sending");
                out.write(buf, 0, n);
                left -= n;
            }
            out.flush();
        } finally {
            raf.close();
        }
    }

    static void writeHead(OutputStream out, int status, String contentType, long length, String... headers)
            throws IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Length: ").append(length).append("\r\n");
        for (int i = 0; i + 1 < headers.length; i += 2) sb.append(headers[i]).append(": ").append(headers[i + 1]).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(Utf8.bytes(sb.toString()));
    }

    static void writeError(OutputStream out, HttpException e, boolean head) throws IOException {
        byte[] body = Utf8.bytes("{\"error\":" + Json.quote(e.getMessage()) + "}");
        writeHead(out, e.status, "application/json", body.length, e.headers);
        if (!head) out.write(body);
        out.flush();
    }

    static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 201: return "Created";
            case 206: return "Partial Content";
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 411: return "Length Required";
            case 413: return "Payload Too Large";
            case 416: return "Range Not Satisfiable";
            case 500: return "Internal Server Error";
            default: return "Status";
        }
    }

    private static void closeQuietly(Socket s) {
        try { if (s != null) s.close(); } catch (IOException ignored) {}
    }

    private static void closeQuietly(ServerSocket s) {
        try { if (s != null) s.close(); } catch (IOException ignored) {}
    }

    /** Counts what goes to the client, for the on-screen counters and to know whether a response has started. */
    public static final class CountingOutputStream extends FilterOutputStream {
        private final Stats stats;
        private long count;

        public CountingOutputStream(OutputStream out, Stats stats) {
            super(out);
            this.stats = stats;
        }

        public long count() {
            return count;
        }

        @Override public void write(int b) throws IOException {
            out.write(b);
            count++;
            stats.addBytes(1);
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
            stats.addBytes(len);
        }
    }
}
