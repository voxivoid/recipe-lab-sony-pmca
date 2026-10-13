package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Json, the info document and Stats formatting. */
class SmallPartsTest {
    @Test void jsonEscaping() {
        assertEquals("\"plain\"", Json.quote("plain"));
        assertEquals("\"a\\\"b\\\\c\"", Json.quote("a\"b\\c"));
        assertEquals("\"\\n\\r\\t\\b\\f\"", Json.quote("\n\r\t\b\f"));
        assertEquals("\"\\u0000\\u001f\\u007f\"", Json.quote("\u0000\u001f\u007f"));
        assertEquals("\"\\u03b16000 caf\\u00e9\"", Json.quote("\u03b16000 caf\u00e9"));
        assertEquals("\"\\u2028\"", Json.quote("\u2028"));
        assertEquals("\"DCIM/100MSDCF/x.ARW\"", Json.quote("DCIM/100MSDCF/x.ARW"));   // '/' left alone
        assertEquals("null", Json.quote(null));
    }

    @Test void infoDocument() {
        assertEquals("{\"app\":\"recipe-lab\",\"protocol\":3,\"model\":\"ILCE-6000\",\"name\":\"Sony \\u03b16000\"}",
            Protocol.infoJson("ILCE-6000", Protocol.friendlyName("ILCE-6000")));
    }

    @Test void friendlyNames() {
        assertEquals("Sony \u03b16000", Protocol.friendlyName("ILCE-6000"));
        assertEquals("Sony \u03b15100", Protocol.friendlyName("ILCE-5100"));
        assertEquals("Sony ILCE-7M2", Protocol.friendlyName("ILCE-7M2"));
        assertEquals("Sony DSC-RX100M3", Protocol.friendlyName("DSC-RX100M3"));
        assertEquals("Sony camera", Protocol.friendlyName(""));
    }

    @Test void statsFormattingAndRate() {
        assertEquals("25.6 MB", Stats.megabytes(25_624_576));
        assertEquals("0.0 MB", Stats.megabytes(0));
        assertEquals("1.05 MB/s", Stats.rate(1_050_000));
        assertEquals("980 KB/s", Stats.rate(980_000));
        Stats s = new Stats();
        assertEquals(0.0, s.sampleRate(0));
        s.addBytes(1_000_000);
        assertEquals(600_000.0, s.sampleRate(1000), 1);             // smoothed: 0.6 of the first second
        s.addBytes(1_000_000);
        assertEquals(840_000.0, s.sampleRate(2000), 1);             // 0.4 * 600k + 0.6 * 1M
        assertEquals(336_000.0, s.sampleRate(3000), 1);             // idle second decays it
    }
}
