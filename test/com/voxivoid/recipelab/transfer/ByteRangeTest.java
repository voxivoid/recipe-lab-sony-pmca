package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ByteRangeTest {
    private static void partial(String header, long total, long first, long last) {
        ByteRange r = ByteRange.parse(header, total);
        assertEquals(ByteRange.Kind.PARTIAL, r.kind, header);
        assertEquals(first, r.first, header);
        assertEquals(last, r.last, header);
        assertEquals(last - first + 1, r.length(), header);
        assertEquals("bytes " + first + "-" + last + "/" + total, r.contentRange());
    }

    private static void kind(ByteRange.Kind k, String header, long total) {
        assertEquals(k, ByteRange.parse(header, total).kind, header);
    }

    @Test void closedAndOpenRanges() {
        partial("bytes=0-99", 1000, 0, 99);
        partial("bytes=100-", 1000, 100, 999);
        partial("bytes=999-999", 1000, 999, 999);
        partial("bytes=5-6", 1000, 5, 6);
        partial("Bytes=0-0", 1000, 0, 0);
    }

    @Test void lastPastTheEndIsClamped() {
        partial("bytes=900-5000", 1000, 900, 999);
        partial("bytes=0-99999999999999999999999", 1000, 0, 999);
    }

    @Test void suffixRanges() {
        partial("bytes=-100", 1000, 900, 999);
        partial("bytes=-5000", 1000, 0, 999);
    }

    @Test void startAtOrPastTheEndIsUnsatisfiable() {
        kind(ByteRange.Kind.UNSATISFIABLE, "bytes=1000-", 1000);
        kind(ByteRange.Kind.UNSATISFIABLE, "bytes=1000-2000", 1000);
        kind(ByteRange.Kind.UNSATISFIABLE, "bytes=99999999999999999999-", 1000);
        kind(ByteRange.Kind.UNSATISFIABLE, "bytes=-0", 1000);
        kind(ByteRange.Kind.UNSATISFIABLE, "bytes=0-", 0);
    }

    @Test void absentMalformedOrUnsupportedHeadersServeTheWholeFile() {
        kind(ByteRange.Kind.FULL, null, 1000);
        kind(ByteRange.Kind.FULL, "items=0-5", 1000);
        kind(ByteRange.Kind.FULL, "bytes=abc", 1000);
        kind(ByteRange.Kind.FULL, "bytes=5-1", 1000);
        kind(ByteRange.Kind.FULL, "bytes=-", 1000);
        kind(ByteRange.Kind.FULL, "bytes=0-1,5-6", 1000);
        kind(ByteRange.Kind.FULL, "bytes=1x-2", 1000);
        ByteRange full = ByteRange.parse(null, 1000);
        assertEquals(0, full.first);
        assertEquals(999, full.last);
        assertEquals(1000, full.length());
    }
}
