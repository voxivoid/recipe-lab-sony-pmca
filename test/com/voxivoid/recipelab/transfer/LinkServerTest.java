package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The real server on an ephemeral port against a temp "card", driven over real sockets. */
class LinkServerTest {
    private static final String ARW = "DCIM/100MSDCF/DSC01542.ARW", JPG = "DCIM/100MSDCF/DSC01543.JPG";

    private File base, card;
    private byte[] large, small, arwBytes, thumb, jpgBytes;
    private LinkServer server;
    private Stats stats;
    private int port;

    @BeforeEach void setUp() throws IOException {
        base = Fixtures.tempDir();
        card = new File(base, "card");
        large = Fixtures.fakeJpeg(1616, 1080, 70_000);    // bigger than one 32 KB buffer
        small = Fixtures.fakeJpeg(160, 120, 500);
        arwBytes = Fixtures.sonyLikeArw(true, 6, large, small);
        Fixtures.write(new File(card, ARW), arwBytes).setLastModified(1507981649000L);
        thumb = Fixtures.fakeJpeg(160, 120, 400);
        jpgBytes = Fixtures.exifJpeg(true, 8, thumb);
        Fixtures.write(new File(card, JPG), jpgBytes).setLastModified(1507981650000L);
        Fixtures.write(new File(card, "PRIVATE/M4ROOT/X.ARW"), new byte[] { 1 });
        Fixtures.write(new File(base, "secret.txt"), "secret".getBytes("UTF-8"));
        stats = new Stats();
        server = new LinkServer(new StorageRoot(card), "ILCE-6000", "Sony \u03b16000", stats, Logger.NONE);
        port = server.start(0);
    }

    @AfterEach void tearDown() {
        server.stop();
        Fixtures.deleteTree(base);
    }

    static final class Response {
        int status;
        final Map<String, String> headers = new HashMap<String, String>();
        byte[] body;

        String header(String name) { return headers.get(name.toLowerCase(Locale.US)); }
        String text() throws IOException { return new String(body, "UTF-8"); }
    }

    /** Sends {@code raw} as-is and reads the response until the server closes the connection. */
    private Response raw(String raw) throws IOException {
        Socket s = new Socket(InetAddress.getLoopbackAddress(), port);
        try {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            out.write(raw.getBytes("ISO-8859-1"));
            out.flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) all.write(buf, 0, n);
            byte[] b = all.toByteArray();
            int split = -1;
            for (int i = 0; i + 3 < b.length; i++) {
                if (b[i] == '\r' && b[i + 1] == '\n' && b[i + 2] == '\r' && b[i + 3] == '\n') { split = i; break; }
            }
            assertTrue(split > 0, "no header terminator in response");
            String[] lines = new String(b, 0, split, "ISO-8859-1").split("\r\n");
            Response r = new Response();
            String[] statusLine = lines[0].split(" ", 3);
            assertEquals("HTTP/1.1", statusLine[0]);
            r.status = Integer.parseInt(statusLine[1]);
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                r.headers.put(lines[i].substring(0, c).toLowerCase(Locale.US), lines[i].substring(c + 1).trim());
            }
            r.body = Arrays.copyOfRange(b, split + 4, b.length);
            return r;
        } finally {
            s.close();
        }
    }

    private Response get(String target, String... headers) throws IOException {
        return request("GET", target, headers);
    }

    private Response request(String method, String target, String... headers) throws IOException {
        StringBuilder sb = new StringBuilder(method + " " + target + " HTTP/1.1\r\nHost: camera\r\n");
        for (String h : headers) sb.append(h).append("\r\n");
        return raw(sb.append("\r\n").toString());
    }

    private static void assertBodyLengthMatches(Response r) {
        assertEquals(String.valueOf(r.body.length), r.header("Content-Length"));
        assertEquals("close", r.header("Connection"));
    }

    @Test void info() throws IOException {
        Response r = get("/api/info");
        assertEquals(200, r.status);
        assertEquals("application/json", r.header("Content-Type"));
        assertEquals("{\"app\":\"recipe-lab\",\"protocol\":4,\"model\":\"ILCE-6000\",\"name\":\"Sony \\u03b16000\"}", r.text());
        assertBodyLengthMatches(r);
    }

    @Test void filesListsTheArwAndTheJpegOutsidePrivateAndRecipes() throws IOException {
        Fixtures.write(new File(card, "RECIPES/GOLDENHO.YML"), new byte[] { 1 });
        Response r = get("/api/files");
        assertEquals(200, r.status);
        assertEquals("{\"files\":["
                + "{\"path\":\"" + JPG + "\",\"size\":" + jpgBytes.length + ",\"modified\":1507981650000,\"type\":\"jpg\"},"
                + "{\"path\":\"" + ARW + "\",\"size\":" + arwBytes.length + ",\"modified\":1507981649000,\"type\":\"arw\"}]}", r.text());
        assertBodyLengthMatches(r);
    }

    @Test void jpegPreviewSmallIsTheExifThumbnail() throws IOException {
        Response r = get("/api/preview?path=" + JPG + "&size=small");
        assertEquals(200, r.status);
        assertEquals("image/jpeg", r.header("Content-Type"));
        assertEquals("8", r.header("X-Orientation"));
        assertArrayEquals(thumb, r.body);
        assertBodyLengthMatches(r);
    }

    @Test void jpegPreviewLargeIsTheFileItself() throws IOException {
        Response r = get("/api/preview?path=DCIM%2F100MSDCF%2FDSC01543.JPG&size=large");
        assertEquals(200, r.status);
        assertEquals("image/jpeg", r.header("Content-Type"));
        assertEquals("8", r.header("X-Orientation"));
        assertArrayEquals(jpgBytes, r.body);
        assertBodyLengthMatches(r);
        assertArrayEquals(jpgBytes, get("/api/preview?path=" + JPG).body, "size defaults to large");

        Response head = request("HEAD", "/api/preview?path=" + JPG + "&size=large");
        assertEquals(200, head.status);
        assertEquals(String.valueOf(jpgBytes.length), head.header("Content-Length"));
        assertEquals("8", head.header("X-Orientation"));
        assertEquals(0, head.body.length);
    }

    @Test void aJpegWithoutExifHasNoSmallPreviewButStillALargeOne() throws IOException {
        byte[] plain = Fixtures.fakeJpeg(6000, 4000, 500);
        Fixtures.write(new File(card, "DCIM/100MSDCF/PLAIN.jpeg"), plain);
        Response small = get("/api/preview?path=DCIM/100MSDCF/PLAIN.jpeg&size=small");
        assertEquals(404, small.status);
        assertEquals("application/json", small.header("Content-Type"));
        assertTrue(small.text().startsWith("{\"error\":"), small.text());
        Response large = get("/api/preview?path=DCIM/100MSDCF/PLAIN.jpeg&size=large");
        assertEquals(200, large.status);
        assertEquals("1", large.header("X-Orientation"), "no Exif, no rotation");
        assertArrayEquals(plain, large.body);
    }

    @Test void recipesListsTheYmlFilesInRecipes() throws IOException {
        assertEquals("{\"files\":[]}", get("/api/recipes").text(), "no RECIPES folder: an empty list");
        Fixtures.write(new File(card, "RECIPES/GOLDENHO.YML"), "name: \"Golden Hour\"\n".getBytes("UTF-8")).setLastModified(1507981649000L);
        Fixtures.write(new File(card, "RECIPES/VELVIA2.YML"), new byte[] { 1, 2 }).setLastModified(1507981650000L);
        Fixtures.write(new File(card, "RECIPES/SAVING.TMP"), new byte[] { 1 });
        Response r = get("/api/recipes");
        assertEquals(200, r.status);
        assertEquals("application/json", r.header("Content-Type"));
        assertEquals("{\"files\":["
                + "{\"path\":\"RECIPES/GOLDENHO.YML\",\"size\":20,\"modified\":1507981649000},"
                + "{\"path\":\"RECIPES/VELVIA2.YML\",\"size\":2,\"modified\":1507981650000}]}", r.text());
        assertBodyLengthMatches(r);
        Response file = get("/files/RECIPES/GOLDENHO.YML");                 // downloaded like any other file
        assertEquals(200, file.status);
        assertEquals("name: \"Golden Hour\"\n", file.text());
    }

    @Test void recipesFindALowerCaseFolderAndExtension() throws IOException {
        Fixtures.write(new File(card, "recipes/goldenho.yml"), new byte[] { 7 }).setLastModified(1507981649000L);
        assertEquals("{\"files\":[{\"path\":\"recipes/goldenho.yml\",\"size\":1,\"modified\":1507981649000}]}", get("/api/recipes").text());
        assertArrayEquals(new byte[] { 7 }, get("/files/recipes/goldenho.yml").body);
    }

    @Test void recipesNeverLeaveTheCard() throws IOException {
        Fixtures.write(new File(base, "outside/STOLEN.YML"), "secret".getBytes("UTF-8"));
        Files.createSymbolicLink(new File(card, "RECIPES").toPath(), new File(base, "outside").toPath());
        Response r = get("/api/recipes");
        assertEquals(200, r.status);
        assertEquals("{\"files\":[]}", r.text(), "a RECIPES folder that links out of the card lists nothing");
        assertEquals(400, get("/files/RECIPES/STOLEN.YML").status);
        assertEquals(400, get("/files/RECIPES/..%2F..%2Fsecret.txt").status);
        assertEquals(405, request("POST", "/api/recipes").status);
    }

    @Test void previewLargeAndSmall() throws IOException {
        Response big = get("/api/preview?path=DCIM%2F100MSDCF%2FDSC01542.ARW&size=large");
        assertEquals(200, big.status);
        assertEquals("image/jpeg", big.header("Content-Type"));
        assertEquals("6", big.header("X-Orientation"));
        assertArrayEquals(large, big.body);
        assertBodyLengthMatches(big);

        Response thumb = get("/api/preview?path=" + ARW + "&size=small");
        assertEquals(200, thumb.status);
        assertArrayEquals(small, thumb.body);

        assertArrayEquals(large, get("/api/preview?path=" + ARW).body);      // size defaults to large
    }

    @Test void previewFallsBackWhenOnlyOneJpegExists() throws IOException {
        java.util.List<int[][]> chain = new java.util.ArrayList<int[][]>();
        chain.add(new int[][] { Fixtures.e(0x0201, 4, -100), Fixtures.e(0x0202, 4, small.length) });
        Fixtures.write(new File(card, "DCIM/100MSDCF/ONLY.ARW"),
            Fixtures.tiff(true, chain, new java.util.ArrayList<int[][]>(), Arrays.asList(small)));
        Response r = get("/api/preview?path=DCIM/100MSDCF/ONLY.ARW&size=large");
        assertEquals(200, r.status);
        assertEquals("1", r.header("X-Orientation"));
        assertArrayEquals(small, r.body);
    }

    @Test void previewErrors() throws IOException {
        Fixtures.write(new File(card, "DCIM/100MSDCF/NOTES.TXT"), new byte[] { 1, 2, 3 });
        assertEquals(404, get("/api/preview?path=DCIM/100MSDCF/NOTES.TXT&size=small").status, "neither an ARW nor a JPEG");
        assertEquals(404, get("/api/preview?path=DCIM/100MSDCF/MISSING.JPG").status);
        assertEquals(404, get("/api/preview?path=DCIM/100MSDCF/MISSING.ARW").status);
        Fixtures.write(new File(card, "DCIM/100MSDCF/BROKEN.ARW"), "garbage".getBytes("UTF-8"));
        assertEquals(404, get("/api/preview?path=DCIM/100MSDCF/BROKEN.ARW").status);
        assertEquals(400, get("/api/preview?size=small").status);
        assertEquals(400, get("/api/preview?path=" + ARW + "&size=huge").status);
        assertEquals(400, get("/api/preview?path=..%2Fsecret.txt").status);
    }

    @Test void wholeFile() throws IOException {
        Response r = get("/files/DCIM/100MSDCF/DSC01542.ARW");
        assertEquals(200, r.status);
        assertEquals("application/octet-stream", r.header("Content-Type"));
        assertEquals("bytes", r.header("Accept-Ranges"));
        assertArrayEquals(arwBytes, r.body);
        assertBodyLengthMatches(r);
    }

    @Test void percentEncodedSegments() throws IOException {
        Fixtures.write(new File(card, "DCIM/100MSDCF/A B.ARW"), new byte[] { 9, 8, 7 });
        Response r = get("/files/DCIM/100MSDCF/A%20B.ARW");
        assertEquals(200, r.status);
        assertArrayEquals(new byte[] { 9, 8, 7 }, r.body);
        assertEquals(200, get("/files/DCIM%2F100MSDCF%2FA%20B.ARW").status);
    }

    @Test void rangeRequests() throws IOException {
        int total = arwBytes.length;
        Response r = get("/files/" + ARW, "Range: bytes=100-199");
        assertEquals(206, r.status);
        assertEquals("bytes 100-199/" + total, r.header("Content-Range"));
        assertEquals("bytes", r.header("Accept-Ranges"));
        assertArrayEquals(Arrays.copyOfRange(arwBytes, 100, 200), r.body);
        assertBodyLengthMatches(r);

        Response open = get("/files/" + ARW, "Range: bytes=40000-");
        assertEquals(206, open.status);
        assertEquals("bytes 40000-" + (total - 1) + "/" + total, open.header("Content-Range"));
        assertArrayEquals(Arrays.copyOfRange(arwBytes, 40000, total), open.body);

        Response suffix = get("/files/" + ARW, "Range: bytes=-10");
        assertEquals(206, suffix.status);
        assertArrayEquals(Arrays.copyOfRange(arwBytes, total - 10, total), suffix.body);

        Response bad = get("/files/" + ARW, "Range: bytes=" + total + "-");
        assertEquals(416, bad.status);
        assertEquals("bytes */" + total, bad.header("Content-Range"));
    }

    @Test void resumingWithRangesReassemblesTheFile() throws IOException {
        ByteArrayOutputStream got = new ByteArrayOutputStream();
        int total = arwBytes.length, step = 30_000;
        for (int from = 0; from < total; from += step) {
            Response r = get("/files/" + ARW, "Range: bytes=" + from + "-" + (from + step - 1));
            assertEquals(206, r.status);
            got.write(r.body);
        }
        assertArrayEquals(arwBytes, got.toByteArray());
    }

    @Test void headHasHeadersButNoBody() throws IOException {
        Response r = request("HEAD", "/files/" + ARW);
        assertEquals(200, r.status);
        assertEquals(String.valueOf(arwBytes.length), r.header("Content-Length"));
        assertEquals("bytes", r.header("Accept-Ranges"));
        assertEquals(0, r.body.length);

        Response ranged = request("HEAD", "/files/" + ARW, "Range: bytes=0-9");
        assertEquals(206, ranged.status);
        assertEquals("10", ranged.header("Content-Length"));
        assertEquals(0, ranged.body.length);

        assertEquals(0, request("HEAD", "/api/info").body.length);
        Response missing = request("HEAD", "/files/DCIM/NOPE.ARW");
        assertEquals(404, missing.status);
        assertEquals(0, missing.body.length);
    }

    /** A PUT with {@code body}, its length declared. */
    private Response put(String target, String body) throws IOException {
        return raw("PUT " + target + " HTTP/1.1\r\nHost: camera\r\nContent-Length: " + body.getBytes("UTF-8").length + "\r\n\r\n" + body);
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), "UTF-8");
    }

    @Test void putWritesARecipeFileAndMakesTheFolder() throws IOException {
        Response r = put("/files/RECIPES/goldenho.yml", "name: Golden Hour\n");
        assertEquals(201, r.status);
        assertEquals("{\"path\":\"RECIPES/GOLDENHO.YML\"}", r.text(), "stored in capitals, as the card's file system has it");
        assertBodyLengthMatches(r);
        assertEquals("name: Golden Hour\n", read(new File(card, "RECIPES/GOLDENHO.YML")));
        assertTrue(get("/api/recipes").text().contains("RECIPES/GOLDENHO.YML"), "and it is listed straight away");
        assertArrayEquals(new String[] { "GOLDENHO.YML" }, new File(card, "RECIPES").list(), "the temporary file is gone");
    }

    @Test void putReplacesTheFileThatIsThereInItsOwnSpelling() throws IOException {
        Fixtures.write(new File(card, "Recipes/Golden.yml"), "old".getBytes("UTF-8"));
        Response r = put("/files/RECIPES/GOLDEN.YML", "new");
        assertEquals(200, r.status);
        assertEquals("{\"path\":\"Recipes/Golden.yml\"}", r.text());
        assertEquals("new", read(new File(card, "Recipes/Golden.yml")));
        assertArrayEquals(new String[] { "Golden.yml" }, new File(card, "Recipes").list());
    }

    @Test void putWritesNothingButRecipeFiles() throws IOException {
        assertEquals(403, put("/files/" + ARW, "x").status);
        assertArrayEquals(arwBytes, Files.readAllBytes(new File(card, ARW).toPath()));
        assertEquals(403, put("/files/RECIPES/notes.txt", "x").status);
        assertEquals(403, put("/files/RECIPES/SAVING.TMP", "x").status);
        assertEquals(403, put("/files/RECIPES/TOOLONGNAME.YML", "x").status, "the card takes 8.3 names only");
        assertEquals(403, put("/files/RECIPES/A%20B.YML", "x").status);
        assertEquals(403, put("/files/RECIPES/SUB/A.YML", "x").status);
        assertEquals(403, put("/files/RECIPES/..%2Fsecret.YML", "x").status);
        assertEquals(403, put("/files/DCIM/A.YML", "x").status);
        assertEquals(403, put("/api/recipes", "x").status);
        assertTrue(!new File(card, "RECIPES").exists(), "a refused PUT leaves the card as it was");
    }

    @Test void putNeedsALengthAndASmallBody() throws IOException {
        assertEquals(411, request("PUT", "/files/RECIPES/A.YML").status);
        assertEquals(413, request("PUT", "/files/RECIPES/A.YML", "Content-Length: " + (Protocol.MAX_RECIPE_BYTES + 1)).status);
        assertEquals(400, request("PUT", "/files/RECIPES/A.YML", "Content-Length: -1").status);
        assertTrue(!new File(card, "RECIPES/A.YML").exists());
        StringBuilder full = new StringBuilder();
        for (int i = 0; i < Protocol.MAX_RECIPE_BYTES; i++) full.append('a');
        assertEquals(201, put("/files/RECIPES/A.YML", full.toString()).status, "the largest file Recipe Lab reads still fits");
    }

    @Test void putNeverWritesOutsideTheCard() throws IOException {
        new File(base, "outside").mkdirs();
        Files.createSymbolicLink(new File(card, "RECIPES").toPath(), new File(base, "outside").toPath());
        assertEquals(400, put("/files/RECIPES/A.YML", "x").status);
        assertEquals(0, new File(base, "outside").list().length, "a RECIPES folder that links out of the card takes nothing");
    }

    @Test void deleteRemovesARecipeFileInAnyCase() throws IOException {
        Fixtures.write(new File(card, "Recipes/Golden.yml"), "old".getBytes("UTF-8"));
        Fixtures.write(new File(card, "Recipes/KEEP.YML"), "keep".getBytes("UTF-8"));
        Response r = request("DELETE", "/files/RECIPES/GOLDEN.YML");
        assertEquals(200, r.status);
        assertEquals("{\"path\":\"Recipes/Golden.yml\"}", r.text());
        assertBodyLengthMatches(r);
        assertArrayEquals(new String[] { "KEEP.YML" }, new File(card, "Recipes").list());
        assertEquals(404, request("DELETE", "/files/RECIPES/GOLDEN.YML").status, "it is gone");
    }

    @Test void deleteRemovesNothingButRecipeFiles() throws IOException {
        Fixtures.write(new File(card, "RECIPES/notes.txt"), "x".getBytes("UTF-8"));
        assertEquals(403, request("DELETE", "/files/" + ARW).status);
        assertEquals(403, request("DELETE", "/files/RECIPES/notes.txt").status);
        assertEquals(403, request("DELETE", "/files/RECIPES/..%2Fsecret.YML").status);
        assertEquals(403, request("DELETE", "/files/RECIPES/SUB/A.YML").status);
        assertEquals(403, request("DELETE", "/api/recipes").status);
        assertEquals(404, request("DELETE", "/files/RECIPES/NONE.YML").status);
        assertTrue(new File(card, ARW).exists());
        assertTrue(new File(card, "RECIPES/notes.txt").exists());
    }

    @Test void deleteNeverReachesOutsideTheCard() throws IOException {
        Fixtures.write(new File(base, "outside/STOLEN.YML"), "secret".getBytes("UTF-8"));
        Files.createSymbolicLink(new File(card, "RECIPES").toPath(), new File(base, "outside").toPath());
        assertEquals(400, request("DELETE", "/files/RECIPES/STOLEN.YML").status);
        assertTrue(new File(base, "outside/STOLEN.YML").exists());
    }

    @Test void otherMethodsAre405() throws IOException {
        Response r = request("POST", "/api/files");
        assertEquals(405, r.status);
        assertEquals("GET, HEAD, PUT, DELETE", r.header("Allow"));
        assertEquals(405, request("PATCH", "/files/" + ARW).status);
        assertTrue(new File(card, ARW).exists());
    }

    @Test void traversalIsRejected() throws IOException {
        assertEquals(400, get("/files/../secret.txt").status);
        assertEquals(400, get("/files/%2e%2e/secret.txt").status);
        assertEquals(400, get("/files/DCIM/..%2F..%2Fsecret.txt").status);
        assertEquals(400, get("/files//etc/passwd").status);
        assertEquals(400, get("/files/DCIM%5C..%5C..%5Csecret.txt").status);
        Files.createSymbolicLink(new File(card, "escape").toPath(), base.toPath());
        Response r = get("/files/escape/secret.txt");
        assertEquals(400, r.status);
        assertTrue(!r.text().contains("secret\""));
    }

    @Test void missingAndUnknownAre404() throws IOException {
        assertEquals(404, get("/files/DCIM/100MSDCF/NOPE.ARW").status);
        assertEquals(404, get("/files/DCIM").status);                   // a directory is not a file
        assertEquals(404, get("/").status);
        assertEquals(404, get("/api/nope").status);
        Response r = get("/api/nope");
        assertEquals("application/json", r.header("Content-Type"));
        assertTrue(r.text().startsWith("{\"error\":"));
    }

    @Test void malformedRequestsAre400() throws IOException {
        assertEquals(400, raw("BLAH\r\n\r\n").status);
        assertEquals(400, raw("GET nopath HTTP/1.1\r\n\r\n").status);
        assertEquals(400, raw("GET /files/%zz HTTP/1.1\r\n\r\n").status);
    }

    @Test void worksWithAStockHttpClientToo() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/files/" + ARW).openConnection();
        c.setRequestProperty("Range", "bytes=0-3");
        assertEquals(206, c.getResponseCode());
        InputStream in = c.getInputStream();
        byte[] b = new byte[4];
        int n = 0;
        for (int k; n < 4 && (k = in.read(b, n, 4 - n)) > 0; ) n += k;
        assertArrayEquals(Arrays.copyOf(arwBytes, 4), b);
        assertEquals(-1, in.read());
        c.disconnect();
    }

    @Test void concurrentDownloads() throws Exception {
        final AtomicInteger ok = new AtomicInteger();
        Thread[] ts = new Thread[LinkServer.MAX_CONNECTIONS + 3];     // more than the slots: the rest queue
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        if (Arrays.equals(arwBytes, get("/files/" + ARW).body)) ok.incrementAndGet();
                    } catch (IOException ignored) {}
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join(10_000);
        assertEquals(ts.length, ok.get());
    }

    @Test void countsRequestsAndBytes() throws IOException {
        long before = stats.bytesSent();
        get("/files/" + ARW);
        get("/api/info");
        assertEquals(2, stats.requests());
        assertTrue(stats.bytesSent() - before > arwBytes.length);
        assertTrue(stats.lastRequest().startsWith("GET /api/info -> 200"), stats.lastRequest());
        assertEquals(0, stats.active());
    }

    @Test void clientThatConnectsAndLeavesIsHarmless() throws IOException {
        new Socket(InetAddress.getLoopbackAddress(), port).close();
        assertEquals(200, get("/api/info").status);
    }

    @Test void stopReleasesThePort() throws IOException {
        server.stop();
        server.stop();                                                   // idempotent
        boolean refused = false;
        try {
            new Socket(InetAddress.getLoopbackAddress(), port).close();
        } catch (IOException e) {
            refused = true;
        }
        assertTrue(refused);
        assertEquals(port, server.start(port));                          // and it can bind again
    }

    @Test void realA6000SampleOverHttp() throws IOException {
        File real = Fixtures.realArw();
        assumeTrue(real != null, "set RECIPELAB_TEST_ARW to a real A6000 ARW to run this");
        server.stop();
        // serve the sample's own folder as the card, so nothing needs copying
        server = new LinkServer(new StorageRoot(real.getParentFile()), "ILCE-6000", "Sony", stats, Logger.NONE);
        port = server.start(0);
        String rel = real.getName();
        Response big = get("/api/preview?path=" + rel + "&size=large");
        assertEquals(200, big.status);
        assertEquals(1122206 - 1386, big.body.length);
        assertEquals((byte) 0xff, big.body[0]);
        assertEquals((byte) 0xd8, big.body[1]);
        assertEquals((byte) 0xd9, big.body[big.body.length - 1]);
        Response thumb = get("/api/preview?path=" + rel + "&size=small");
        assertTrue(thumb.body.length > 1000 && thumb.body.length < big.body.length);
        Response head = request("HEAD", "/files/" + rel);
        assertEquals(String.valueOf(real.length()), head.header("Content-Length"));
    }
}
