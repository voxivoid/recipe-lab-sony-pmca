package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class HttpRequestTest {
    private static HttpRequest read(String raw) throws IOException, HttpException {
        return HttpRequest.read(new ByteArrayInputStream(raw.getBytes("ISO-8859-1")));
    }

    private static int status(String raw) {
        return assertThrows(HttpException.class, () -> read(raw)).status;
    }

    @Test void parsesRequestLineAndHeaders() throws Exception {
        HttpRequest r = read("GET /files/DCIM/100MSDCF/DSC01542.ARW HTTP/1.1\r\nHost: cam\r\nRange: bytes=0-99\r\n\r\n");
        assertEquals("GET", r.method);
        assertEquals("/files/DCIM/100MSDCF/DSC01542.ARW", r.path);
        assertEquals("HTTP/1.1", r.version);
        assertNull(r.query);
        assertEquals("bytes=0-99", r.header("range"));
        assertEquals("bytes=0-99", r.header("RANGE"));
        assertEquals("cam", r.header("Host"));
    }

    @Test void acceptsBareLfAndLeadingEmptyLines() throws Exception {
        HttpRequest r = read("\r\n\nHEAD /api/info HTTP/1.0\nX-A: 1\n\n");
        assertEquals("HEAD", r.method);
        assertEquals("/api/info", r.path);
        assertEquals("1", r.header("x-a"));
    }

    @Test void stopsAtTheEndOfTheHeadAndLeavesTheRest() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("GET / HTTP/1.1\r\n\r\nBODY".getBytes("ISO-8859-1"));
        HttpRequest.read(in);
        assertEquals('B', in.read());
    }

    @Test void decodesPathAndQuery() throws Exception {
        HttpRequest r = read("GET /api/preview?path=DCIM%2F100MSDCF%2FDSC%2001.ARW&size=small&x HTTP/1.1\r\n\r\n");
        assertEquals("/api/preview", r.path);
        assertEquals("DCIM/100MSDCF/DSC 01.ARW", r.queryParam("path"));
        assertEquals("small", r.queryParam("size"));
        assertEquals("", r.queryParam("x"));
        assertNull(r.queryParam("missing"));
    }

    @Test void plusIsASpaceOnlyInTheQuery() throws Exception {
        HttpRequest r = read("GET /files/a+b%20c.ARW?q=a+b HTTP/1.1\r\n\r\n");
        assertEquals("/files/a+b c.ARW", r.path);
        assertEquals("a b", r.queryParam("q"));
    }

    @Test void decodesUtf8Escapes() throws Exception {
        assertEquals("/files/café", read("GET /files/caf%C3%A9 HTTP/1.1\r\n\r\n").path);
    }

    @Test void repeatedHeadersAreJoined() throws Exception {
        assertEquals("a, b", read("GET / HTTP/1.1\r\nX: a\r\nx: b\r\n\r\n").header("X"));
    }

    @Test void cleanEofBeforeAnyByteIsNotAnError() throws Exception {
        assertNull(read(""));
        assertNull(read("\r\n"));
    }

    @Test void malformedRequestsAre400() {
        assertEquals(400, status("GET /\r\n\r\n"));
        assertEquals(400, status("GET / HTTP/1.1 extra\r\n\r\n"));
        assertEquals(400, status("get / HTTP/1.1\r\n\r\n"));
        assertEquals(400, status("GET / HTTP/2\r\n\r\n"));
        assertEquals(400, status("GET relative HTTP/1.1\r\n\r\n"));
        assertEquals(400, status("GET http://cam/ HTTP/1.1\r\n\r\n"));
        assertEquals(400, status("GET / HTTP/1.1\r\nNoColon\r\n\r\n"));
        assertEquals(400, status("GET /files/%zz HTTP/1.1\r\n\r\n"));
        assertEquals(400, status("GET /files/abc% HTTP/1.1\r\n\r\n"));
        assertEquals(400, status("GET / HTTP/1.1\r\nHost: x\r\n"));        // stream ended mid-head
    }

    @Test void oversizedHeadIs400() {
        StringBuilder sb = new StringBuilder("GET / HTTP/1.1\r\nX: ");
        for (int i = 0; i < HttpRequest.MAX_HEAD; i++) sb.append('a');
        assertEquals(400, status(sb.append("\r\n\r\n").toString()));
    }

    @Test void badQueryEscapeIs400() throws Exception {
        HttpRequest r = read("GET /api/preview?path=%G1 HTTP/1.1\r\n\r\n");
        assertEquals(400, assertThrows(HttpException.class, () -> r.queryParam("path")).status);
    }
}
